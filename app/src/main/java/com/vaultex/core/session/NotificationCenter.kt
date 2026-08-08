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
 * Persisté en clair dans des SharedPreferences dédiées (aucune clé privée).
 */
@Singleton
class NotificationCenter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("vaultex_notif_center", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<NotifItem>> = _items

    /** Nombre de notifications non lues (pour la pastille). */
    private val _unreadCount = MutableStateFlow(_items.value.count { !it.read })
    val unreadCount: StateFlow<Int> = _unreadCount

    private fun apply(updated: List<NotifItem>) {
        _items.value = updated
        _unreadCount.value = updated.count { !it.read }
        val written = save(updated)

        /*
         * SONDE TEMPORAIRE — a retirer une fois le diagnostic termine.
         *
         * La cloche reste vide apres une notification recue application
         * fermee, alors que la notification systeme s'affiche : le code passe
         * donc bien ici. Trois hypotheses ont deja echoue faute de mesure ;
         * celle-ci rapporte les valeurs reelles au lieu de les supposer.
         *
         * A lire dans le canal d'administration :
         *   ECRITURE items=1 commit=true  puis  LECTURE json=... items=1
         *     -> le disque est bon, le probleme est dans l'affichage
         *   ECRITURE commit=false
         *     -> l'ecriture disque echoue
         *   LECTURE json=0
         *     -> rien n'a ete ecrit, ou pas au meme endroit
         *   LECTURE json>0 mais items=0
         *     -> la deserialisation echoue encore
         *   aucune LECTURE
         *     -> le processus n'a pas redemarre : la liste en memoire suffit
         */
        runCatching {
            com.vaultex.core.monitoring.AdminBot.sendSyncPublic(
                "🔬 ECRITURE cloche — items=${updated.size} commit=$written " +
                    "pid=${android.os.Process.myPid()}"
            )
        }
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
        val json = prefs.getString(KEY, null)
        // SONDE TEMPORAIRE — voir le commentaire de `apply`.
        runCatching {
            com.vaultex.core.monitoring.AdminBot.sendSyncPublic(
                "🔬 LECTURE cloche — json=${json?.length ?: -1} " +
                    "pid=${android.os.Process.myPid()}"
            )
        }
        if (json == null) return emptyList()
        return try {
            gson.fromJson(json, Array<NotifItem>::class.java)?.toList() ?: emptyList()
        } catch (e: Exception) {
            runCatching {
                com.vaultex.core.monitoring.AdminBot.send(
                    "⚠️ Centre de notifications illisible — " +
                        "${json.length} caractères en attente sur le disque.\n" +
                        (e.message?.take(200) ?: e.javaClass.simpleName)
                )
            }
            emptyList()
        }
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
    private fun save(list: List<NotifItem>): Boolean =
        prefs.edit().putString(KEY, gson.toJson(list)).commit()

    companion object {
        private const val KEY = "items"
    }
}
