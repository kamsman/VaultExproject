package com.vaultex.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Service Firebase Cloud Messaging. Il ne fait plus qu'une chose : traduire un
 * message reçu en événement, et le confier à [NotificationHub] qui décide de
 * l'afficher ou de l'ignorer comme doublon.
 */
@dagger.hilt.android.AndroidEntryPoint
class VaultExFcmService : FirebaseMessagingService() {

    @javax.inject.Inject
    lateinit var hub: com.vaultex.core.session.NotificationHub

    @javax.inject.Inject
    lateinit var pushRegistrar: PushRegistrar

    /**
     * Firebase RENOUVELLE le jeton : réinstallation, effacement des données,
     * restauration sur un nouveau téléphone, ou rotation spontanée.
     *
     * Ne rien faire ici — ce qui était le cas — condamne les notifications :
     * le serveur continue d'écrire vers un jeton mort, sans erreur visible côté
     * utilisateur. Les push s'arrêtent DÉFINITIVEMENT et SILENCIEUSEMENT.
     * On réenregistre donc immédiatement.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Thread {
            runCatching { pushRegistrar.forceRegisterBlocking() }
        }.start()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // Fonctionne pour les messages « data-only » (titre/corps dans data,
        // envoyés par nos Cloud Functions) ET les messages « notification »
        // (console Firebase). L'affichage passe TOUJOURS en premier ; la mise à
        // jour de la cloche est protégée pour ne jamais l'empêcher.
        val title = message.notification?.title ?: message.data["title"] ?: "VaultEx"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        val symbol = message.data["symbol"]
        val amount = message.data["amount"]

        // Clé d'événement : pour un dépôt, elle DOIT coïncider avec celle que
        // produiront le worker local et la synchro d'historique, sinon le même
        // dépôt serait notifié deux ou trois fois.
        val key = when {
            message.data["type"] == "deposit" && amount != null ->
                com.vaultex.core.session.NotificationHub.receiveKey(symbol, amount)
            // Annonce ou message sans identité propre : on retombe sur le
            // contenu, ce qui évite au moins les répétitions à l'identique.
            else -> "fcm:" + (message.data["key"] ?: "$title|$body")
        }
        hub.post(key = key, title = title, body = body, symbol = symbol)
    }

}
