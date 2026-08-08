package com.vaultex.core.session

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Une notification affichée dans le centre de notifications de l'app. */
data class NotifItem(
    val id: Long,
    val title: String,
    val body: String,
    val timestamp: Long,
    val symbol: String? = null,
    val read: Boolean = false
)

/**
 * Centre de notifications in-app : mémorise les notifications affichées (dépôts,
 * alertes prix, annonces…) pour les lister dans l'app et compter les non-lues.
 * Persisté en clair dans un fichier dédié (aucune clé privée) — voir le
 * commentaire sur `file` pour la raison précise de ce choix.
 */
@Singleton
class NotificationCenter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /*
    ═══════════════════════════════════════════════════════════════════════
    STOCKAGE DANS UN FICHIER, PAS DANS SHAREDPREFERENCES
    ═══════════════════════════════════════════════════════════════════════

    SharedPreferences maintient une copie EN MÉMOIRE de son contenu.
    `getString()` ne lit pas le fichier : il lit ce cache. Deux conséquences,
    toutes deux rencontrées ici.

    1. Relire ne relit rien. Une instance ayant chargé une liste vide au
       démarrage renverra indéfiniment une liste vide, même si le disque a
       changé entre-temps. La relecture ajoutée au retour au premier plan
       était donc sans effet.

    2. Deux processus s'ignorent. MODE_PRIVATE n'est pas prévu pour l'accès
       multi-processus : le service FCM et l'interface tiennent chacun leur
       cache, et leurs écritures peuvent s'écraser mutuellement.

    Symptôme observé : une annonce reçue application fermée s'affiche en
    notification, est écrite sur le disque — et reste introuvable dans la
    cloche, quel que soit le moment où on la consulte.

    Un fichier ordinaire n'a aucun cache. Chaque lecture touche le disque,
    chaque écriture y va directement. Le contenu est minuscule (100 entrées
    au maximum) et lu rarement.
    ═══════════════════════════════════════════════════════════════════════
     */
    private val file = java.io.File(context.filesDir, "notif_center.json")
    private val legacyPrefs =
        context.getSharedPreferences("vaultex_notif_center", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<NotifItem>> = _items

    /** Nombre de notifications non lues (pour la pastille). */
    private val _unreadCount = MutableStateFlow(_items.value.count { !it.read })
    val unreadCount: StateFlow<Int> = _unreadCount

    private fun apply(updated: List<NotifItem>) {
        _items.value = updated
        _unreadCount.value = updated.count { !it.read }
        save(updated)
    }

    /** Ajoute une notification en tête de liste (max 100 conservées). */
    @Synchronized
    fun push(title: String, body: String, symbol: String? = null) {
        val item = NotifItem(
            id = System.currentTimeMillis(),
            title = title,
            body = body,
            timestamp = System.currentTimeMillis(),
            symbol = symbol,
            read = false
        )
        apply((listOf(item) + _items.value).take(100))
    }

    /**
     * Relit le disque et remplace l'etat en memoire.
     *
     * C'EST LA CORRECTION DE FOND DE TOUT CE CHAPITRE.
     *
     * Cette classe est un singleton : sa liste est chargee UNE fois, a la
     * construction, puis vit en memoire. Or elle est ecrite depuis plusieurs
     * contextes — le service FCM, les workers de fond — qui s'executent
     * parfois dans un processus demarre pour eux seuls : ils ecrivent sur le
     * disque, puis meurent.
     *
     * L'instance qui sert l'interface ne sait rien de ces ecritures. Elle
     * continue d'afficher la liste qu'elle avait au demarrage. Symptome
     * observe sur appareil : une annonce recue application fermee s'affiche
     * bien en notification, est correctement ecrite sur le disque — et reste
     * pourtant introuvable dans la cloche.
     *
     * Les sondes l'ont montre sans ambiguite : aucune trace de `PUSH` dans le
     * processus de l'interface, et un `pid` inchange. L'ecriture avait bien eu
     * lieu, ailleurs.
     *
     * Appele quand l'application revient au premier plan : le seul moment ou
     * l'utilisateur peut constater un ecart, et le seul ou une relecture a un
     * cout.
     *
     * Ne notifie que si le contenu a REELLEMENT change, pour ne pas declencher
     * de recomposition inutile a chaque retour a l'ecran.
     */
    @Synchronized
    fun reload() {
        val fromDisk = load()
        if (fromDisk != _items.value) {
            _items.value = fromDisk
            _unreadCount.value = fromDisk.count { !it.read }
        }
    }

    /** Marque toutes les notifications comme lues. */
    fun markAllRead() = apply(_items.value.map { if (it.read) it else it.copy(read = true) })

    /** Vide le centre de notifications. */
    fun clear() = apply(emptyList())

    /**
     * Relecture depuis le disque, au démarrage du processus.
     *
     * DEUX PIÈGES, tous deux rencontrés :
     *
     * 1. `object : TypeToken<List<NotifItem>>() {}` — un TypeToken ANONYME.
     *    R8 peut effacer l'information de type générique de ces classes lors
     *    de la minification. Gson perd alors le type cible et échoue. Le
     *    symptôme est trompeur : tout fonctionne application ouverte, où la
     *    liste vit en mémoire et où aucune désérialisation n'a lieu ; et la
     *    cloche revient vide dès que le processus a été relancé — après une
     *    notification reçue application fermée, typiquement.
     *
     *    `Array<NotifItem>::class.java` désigne un type CONCRET : plus de
     *    générique à préserver, donc plus rien que R8 puisse effacer.
     *
     * 2. L'échec était avalé par un `catch` muet renvoyant une liste vide.
     *    Une perte de données silencieuse est pire que le bug : elle
     *    ressemble à « il n'y avait rien », alors que les données sont sur le
     *    disque et intactes. L'échec est désormais signalé au canal
     *    d'administration, et le JSON n'est PAS écrasé — la prochaine
     *    écriture ne détruira pas ce qui n'a pas pu être relu.
     */
    private fun load(): List<NotifItem> {
        val json = readRaw() ?: return emptyList()
        return try {
            gson.fromJson(json, Array<NotifItem>::class.java)?.toList() ?: emptyList()
        } catch (e: Exception) {
            runCatching {
                com.vaultex.core.monitoring.AdminBot.send(
                    "Centre de notifications illisible : " +
                        (e.message?.take(200) ?: e.javaClass.simpleName)
                )
            }
            emptyList()
        }
    }

    /**
     * Contenu brut, lu DIRECTEMENT sur le disque a chaque appel.
     *
     * Migration : les versions precedentes stockaient la liste dans des
     * SharedPreferences. Si le fichier n'existe pas encore mais qu'une valeur
     * y subsiste, on la reprend puis on efface l'ancienne — aucune
     * notification n'est perdue a la mise a jour.
     */
    private fun readRaw(): String? {
        runCatching { if (file.exists()) return file.readText().ifBlank { null } }
        val legacy = runCatching { legacyPrefs.getString(KEY, null) }.getOrNull()
        if (legacy != null) {
            runCatching { file.writeText(legacy) }
            runCatching { legacyPrefs.edit().remove(KEY).commit() }
            return legacy.ifBlank { null }
        }
        return null
    }

    /**
     * Écriture SYNCHRONE — `commit()` et non `apply()`, volontairement.
     *
     * `apply()` met à jour la mémoire puis programme l'écriture disque pour
     * plus tard. C'est le bon choix dans une Activity, qui vit assez longtemps
     * pour que l'écriture aboutisse. Ici, c'est une perte de données.
     *
     * Le scénario, observé sur appareil : l'application est fermée, une
     * annonce arrive. Android démarre le processus, `onMessageReceived`
     * s'exécute, la notification s'affiche et la cloche est mise à jour EN
     * MÉMOIRE. Puis le service termine et le système tue le processus —
     * l'écriture programmée n'a jamais lieu. L'utilisateur ouvre l'app : la
     * cloche relit le disque et ne trouve rien. Le message a bien été notifié,
     * puis silencieusement perdu.
     *
     * Le bug ne se manifestait QUE application fermée : processus vivant,
     * l'écriture différée aboutit normalement.
     *
     * `commit()` écrit avant de rendre la main. Il bloque quelques
     * millisecondes sur une liste plafonnée à 100 entrées — un coût dérisoire
     * face à la perte d'une notification de dépôt.
     */
    private fun save(list: List<NotifItem>) {
        runCatching { file.writeText(gson.toJson(list)) }
    }

    companion object {
        private const val KEY = "items"
    }
}
