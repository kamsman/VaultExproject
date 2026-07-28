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
        L'affichage système part sur un THREAD SÉPARÉ. Ce n'est pas du confort :
        le logo de la monnaie est téléchargé (6 s de délai maximum) et l'icône
        de repli est décodée depuis les ressources. Or `post` est appelé aussi
        bien depuis un worker que depuis le thread principal — après un envoi
        réussi, par exemple. Sur le thread principal, ce travail gèlerait
        l'interface au pire moment : juste après une transaction.
         */
        Thread { showSystemNotification(key, title, body, symbol, channelId) }.start()
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
            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setLargeIcon(com.vaultex.service.NotifLogo.forSymbol(context, symbol))
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
                .setDefaults(NotificationCompat.DEFAULT_ALL)   // son + vibration (< Android 8)
                // Regroupement : plusieurs événements se replient en une pile
                // ordonnée au lieu d'inonder la barre système.
                .setGroup(GROUP)
                // SANS ceci, Android n'alerte que le résumé du groupe — qui
                // n'existe pas ici : les notifications arrivaient donc en
                // silence, sans bannière. C'est le détail qui fait toute la
                // différence entre « visible » et « découverte plus tard ».
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                .build()
            // Identifiant DÉRIVÉ DE LA CLÉ : republier le même événement
            // remplace la notification au lieu d'en ajouter une seconde.
            context.getSystemService(NotificationManager::class.java)
                ?.notify(key.hashCode(), notification)
        } catch (_: Exception) {
            // Permission notifications refusée : la cloche reste alimentée.
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
        editor.apply()
    }

    companion object {
        private const val PREFS = "vaultex_notif_dedup"
        private const val GROUP = "vaultex_events"
        private const val MAX_KEYS = 200

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
