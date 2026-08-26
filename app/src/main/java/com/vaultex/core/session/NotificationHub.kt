package com.vaultex.core.session

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.vaultex.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Point de passage UNIQUE de toute notification VaultEx.
 *
 * Le problème qu'il résout : quatre chemins indépendants pouvaient signaler le
 * MÊME événement — le push serveur (FCM), le worker de détection de dépôt, la
 * synchronisation d'historique et l'alerte de prix. Chacun poussait dans la
 * cloche et affichait sa propre notification système, avec un identifiant tiré
 * de l'horloge. Résultat : un seul dépôt pouvait apparaître deux ou trois fois,
 * la pastille comptait plusieurs fois le même événement, et les notifications
 * s'empilaient dans la barre système au lieu de se remplacer.
 *
 * Ici, tout événement porte une CLÉ qui décrit l'événement lui-même — pas le
 * message. Deux sources qui décrivent le même dépôt produisent la même clé :
 * la première passe, les suivantes sont ignorées. La clé sert aussi
 * d'identifiant de notification système, donc un renvoi remplace au lieu
 * d'empiler.
 */
@Singleton
class NotificationHub @Inject constructor(
    @ApplicationContext private val context: Context,
    private val center: NotificationCenter
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Publie [title]/[body] si [key] n'a pas déjà été publiée récemment.
     *
     * @return true si la notification a été publiée, false si c'était un doublon.
     */
    @Synchronized
    fun post(
        key: String,
        title: String,
        body: String,
        symbol: String? = null,
        channelId: String = com.vaultex.app.VaultExApplication.FCM_DEFAULT_CHANNEL_ID
    ): Boolean {
        if (isDuplicate(key)) return false
        remember(key)

        // La cloche d'abord : c'est la trace durable. Si l'affichage système
        // échoue (permission refusée, canal bloqué), l'utilisateur retrouve
        // quand même l'événement dans l'application.
        center.push(title, body, symbol)

        /*
        ═══════════════════════════════════════════════════════════════════
        UN FIL SÉPARÉ SEULEMENT DEPUIS LE THREAD PRINCIPAL
        ═══════════════════════════════════════════════════════════════════

        L'affichage partait TOUJOURS sur un fil séparé, pour une raison
        valable : le logo de la monnaie est téléchargé (jusqu'à 6 secondes)
        et l'icône de repli décodée depuis les ressources. Appelé depuis le
        thread principal — après un envoi réussi, par exemple — ce travail
        gèlerait l'interface au pire moment.

        Mais depuis un service en arrière-plan, ce fil est une perte de
        données. Application fermée, Android démarre le processus dans le
        seul but de livrer le message ; dès que `onMessageReceived` rend la
        main, le service est considéré comme terminé et le processus PEUT
        ÊTRE TUÉ. Le fil séparé n'a alors jamais le temps d'afficher quoi
        que ce soit.

        Constaté sur appareil, et le symptôme désigne précisément le
        coupable : l'annonce apparaît dans la cloche — écrite juste
        au-dessus, de façon synchrone — mais aucune bannière ne s'affiche.
        Une notification envoyée depuis la console Firebase, elle,
        s'affichait bien : c'est le SDK qui la dessine lui-même, sans passer
        par ce code. Les deux moitiés fonctionnaient, jamais ensemble.

        La règle est donc devenue conditionnelle : on ne se décale que là où
        le décalage protège quelque chose. Sur un service ou un worker, on
        affiche AVANT de rendre la main — c'est le seul moment où l'on est
        sûr d'être encore en vie.
        ═══════════════════════════════════════════════════════════════════
         */
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            Thread { showSystemNotification(key, title, body, symbol, channelId) }.start()
        } else {
            showSystemNotification(key, title, body, symbol, channelId)
        }
        return true
    }

    private fun showSystemNotification(
        key: String, title: String, body: String, symbol: String?, channelId: String
    ) {
        try {
            val intent = Intent(context, com.vaultex.app.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pending = PendingIntent.getActivity(
                context, key.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            // Peut être null : logo indisponible ou illisible. On l'omet alors,
            // au lieu de laisser tomber toute la notification — c'est
            // exactement ce qui se produisait, voir NotifLogo.logoApplication.
            val grandeIcone = com.vaultex.service.NotifLogo.forSymbol(context, symbol)
            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .apply { grandeIcone?.let { setLargeIcon(it) } }
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(pending)
                .setAutoCancel(true)
                // Bannière EN HAUT DE L'ÉCRAN (« heads-up ») : c'est un mouvement
                // d'argent, l'utilisateur doit le voir sans dérouler le volet.
                // Trois conditions, toutes nécessaires — importance HIGH du canal,
                // priorité HIGH ici, et une catégorie qui autorise l'interruption.
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                /*
                 * Nombre affiché dans la pastille de l'icône du lanceur.
                 *
                 * Sans ceci, Android compte les notifications ACTIVES : la
                 * pastille affichait donc 1, quel que soit le nombre
                 * d'événements non lus dans la cloche. On y met le compteur
                 * réel du centre de notifications, celui que l'utilisateur
                 * retrouvera en ouvrant l'application.
                 *
                 * LIMITE D'ANDROID, à connaître : depuis Android 8, la
                 * pastille n'existe QUE tant qu'une notification est présente
                 * dans le volet. Balayée ou touchée — `setAutoCancel(true)`
                 * ci-dessus — elle disparaît, même s'il reste des éléments non
                 * lus dans la cloche. Aucune application ne peut imposer une
                 * pastille permanente ; c'est le système qui la gouverne.
                 */
                .setNumber(center.unreadCount.value)
                .setDefaults(NotificationCompat.DEFAULT_ALL)   // son + vibration (< Android 8)
                // Regroupement : plusieurs événements se replient en une pile
                // ordonnée au lieu d'inonder la barre système.
                .setGroup(GROUP)
                // SANS ceci, Android n'alerte que le résumé du groupe — qui
                // n'existe pas ici : les notifications arrivaient donc en
                // silence, sans bannière. C'est le détail qui fait toute la
                // différence entre « visible » et « découverte plus tard ».
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                // PASTILLE CHIFFRÉE sur l'icône de l'application, y compris
                // quand l'app est fermée. Les lanceurs qui savent l'afficher
                // (Samsung, Transsion/HiOS, Xiaomi) lisent ce nombre ; les
                // autres se contentent du point. On donne le nombre de
                // notifications NON LUES : le chiffre de l'icône dit alors
                // exactement la même chose que la cloche dans l'application.
                .setNumber(center.unreadCount.value)
                .build()
            val manager = context.getSystemService(NotificationManager::class.java)
            // Identifiant DÉRIVÉ DE LA CLÉ : republier le même événement
            // remplace la notification au lieu d'en ajouter une seconde.
            manager?.notify(key.hashCode(), notification)

            /*
            RÉSUMÉ DE GROUPE. Sans lui, un groupe déclaré n'est jamais replié :
            au-delà de quelques événements la barre système est saturée, et
            surtout la pastille de l'icône ne totalise rien. Le résumé porte le
            compte global ; les enfants restent consultables en le dépliant.
             */
            val summary = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(context.getString(R.string.app_name))
                .setGroup(GROUP)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .setNumber(center.unreadCount.value)
                // Le résumé n'alerte pas : ce sont les enfants qui le font
                // (GROUP_ALERT_CHILDREN), sinon la bannière sonnerait deux fois.
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                .build()
            manager?.notify(SUMMARY_ID, summary)
        } catch (e: Exception) {
            /*
            ÉCHEC D'AFFICHAGE — SIGNALÉ, PLUS AVALÉ.

            Ce bloc était muet, avec pour seul commentaire « permission
            refusée ». Il couvrait en réalité tout ce qui peut mal tourner
            ici : canal absent ou bloqué, icône illisible, service de
            notification indisponible.

            Le symptôme est alors indiscernable d'un cas parfaitement
            normal : la cloche se remplit, l'écran ne montre rien, et rien
            nulle part ne dit pourquoi. Constaté sur appareil avec une
            annonce reçue correctement dans la cloche mais jamais affichée.

            La cloche reste bien le repli — l'événement n'est jamais perdu —
            mais l'échec remonte désormais au canal d'administration, seul
            moyen de diagnostiquer à distance ce qui ne s'affiche pas sur un
            téléphone qu'on n'a pas en main.
             */
            runCatching {
                com.vaultex.core.monitoring.AdminBot.serviceFailed(
                    "Affichage notification",
                    "${e.javaClass.simpleName} — ${e.message?.take(120) ?: "sans message"}"
                )
            }
        }
    }

    /**
     * Alimente la cloche SANS afficher de notification système.
     *
     * Pour les alertes de prix, qui gèrent déjà leur propre affichage sur un
     * canal dédié, volontairement moins intrusif que celui des mouvements de
     * fonds : une information de marché n'a pas à sonner comme un dépôt.
     */
    fun postBellOnly(title: String, body: String, symbol: String? = null) {
        center.push(title, body, symbol)
    }

    /**
     * Efface les notifications système de VaultEx.
     *
     * Appelé quand l'utilisateur ouvre le centre de notifications : sans cela,
     * la cloche retomberait à zéro pendant que la pastille de l'icône et la
     * barre système continueraient d'afficher des messages. Les deux comptes
     * doivent toujours raconter la même chose.
     */
    fun clearSystemNotifications() {
        try {
            context.getSystemService(NotificationManager::class.java)?.cancelAll()
        } catch (_: Exception) { }
    }

    /**
     * Enregistre un événement dans la cloche SANS afficher de notification
     * système — parce qu'elle a déjà été affichée par quelqu'un d'autre.
     *
     * Le cas visé : une annonce envoyée depuis la console Firebase. Ces
     * messages sont de type « notification » ; quand l'application est en
     * arrière-plan, le SDK Firebase les affiche LUI-MÊME et n'appelle jamais
     * `onMessageReceived`. Le contenu n'atteint donc jamais le code de
     * l'application, et l'utilisateur qui rouvre VaultEx ne retrouve rien
     * dans sa cloche — alors qu'il vient tout juste de lire le message.
     *
     * Android transmet malgré tout ce contenu dans l'intention de lancement
     * quand l'utilisateur APPUIE sur la notification. C'est ce moment qu'on
     * rattrape ici.
     *
     * Passer par [post] serait faux : l'utilisateur verrait une seconde
     * notification système pour un message qu'il vient d'ouvrir.
     *
     * La déduplication reste celle de [post] — même mémoire, mêmes clés : si
     * un dépôt a déjà été signalé par le worker local, le clic sur le push
     * correspondant ne le fera pas apparaître deux fois.
     *
     * @return true si l'événement a été enregistré, false si c'était un doublon.
     */
    @Synchronized
    fun record(key: String, title: String, body: String, symbol: String? = null): Boolean {
        if (isDuplicate(key)) return false
        remember(key)
        center.push(title, body, symbol)
        return true
    }

    /**
     * Relaie la relecture du centre de notifications.
     *
     * MainActivity n'a besoin que de NotificationHub ; passer par lui evite de
     * lui injecter une seconde dependance pour un seul appel.
     */
    fun reloadCenter() = center.reload()

    private fun isDuplicate(key: String): Boolean {
        val at = prefs.getLong(key, 0L)
        return at > 0L && System.currentTimeMillis() - at < DEDUP_WINDOW_MS
    }

    private fun remember(key: String) {
        val editor = prefs.edit().putLong(key, System.currentTimeMillis())
        // Purge : sans elle, ces préférences grossiraient indéfiniment.
        if (prefs.all.size > MAX_KEYS) {
            val cutoff = System.currentTimeMillis() - DEDUP_WINDOW_MS
            prefs.all.forEach { (k, v) -> if ((v as? Long ?: 0L) < cutoff) editor.remove(k) }
        }
        // `commit()` et non `apply()` : la déduplication est souvent écrite
        // depuis un worker ou le service FCM, dont le processus peut être tué
        // aussitôt après. Une écriture différée serait perdue, et le même
        // dépôt réapparaîtrait à la détection suivante — exactement ce que
        // cette classe existe pour empêcher.
        editor.commit()
    }

    companion object {
        private const val PREFS = "vaultex_notif_dedup"
        private const val GROUP = "vaultex_events"
        private const val MAX_KEYS = 200

        /** Identifiant fixe du résumé de groupe (une seule instance). */
        private const val SUMMARY_ID = 424242

        /**
         * Fenêtre pendant laquelle un même événement n'est plus renotifié.
         *
         * 30 min : c'est le délai maximal séparant les deux détections d'un même
         * dépôt (le push serveur arrive en secondes, le worker en arrière-plan
         * jusqu'à 15 min plus tard, davantage si le système l'a retardé).
         * Contrepartie assumée : deux dépôts RIGOUREUSEMENT identiques — même
         * monnaie, même montant — reçus en moins de 30 min ne donnent qu'une
         * notification. Bien préférable à notifier trois fois le même.
         */
        private const val DEDUP_WINDOW_MS = 30L * 60 * 1000

        /**
         * Clé d'un dépôt. Les trois détecteurs (push serveur, worker, synchro
         * d'historique) ne voient PAS la même chose — le serveur compare des
         * soldes et ignore le hash de transaction — donc la clé ne peut reposer
         * que sur ce qu'ils partagent tous : la monnaie et le montant.
         */
        fun receiveKey(symbol: String?, amount: String): String {
            val normalized = amount.trim().replace(",", ".")
                .toDoubleOrNull()
                ?.let { String.format(java.util.Locale.US, "%.6f", it) }
                ?: amount.trim()
            return "recv:${symbol.orEmpty().uppercase()}:$normalized"
        }
    }
}
