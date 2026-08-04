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
     * Les deux détections LOCALES (worker de dépôt et synchro d'historique)
     * respectent `txAlerts` avant de notifier. Ce chemin-ci ne le faisait pas :
     * un utilisateur qui désactivait « Fonds reçus » dans les réglages
     * continuait d'en recevoir, envoyées par le serveur.
     *
     * Un interrupteur qui ne coupe qu'une source sur trois est pire que pas
     * d'interrupteur du tout : l'utilisateur croit avoir réglé le problème,
     * constate que non, et en conclut que l'application ignore ses choix.
     */
    @javax.inject.Inject
    lateinit var notifPrefs: com.vaultex.core.session.NotifPrefs

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

        val isDeposit = message.data["type"] == "deposit"
        // Même règle que les détections locales : si l'utilisateur a coupé les
        // alertes de transaction, le push de dépôt est ignoré. Les annonces de
        // l'application, elles, ne relèvent pas de ce réglage et passent.
        if (isDeposit && !notifPrefs.txAlerts.value) return

        // Clé d'événement : pour un dépôt, elle DOIT coïncider avec celle que
        // produiront le worker local et la synchro d'historique, sinon le même
        // dépôt serait notifié deux ou trois fois.
        val key = when {
            isDeposit && amount != null ->
                com.vaultex.core.session.NotificationHub.receiveKey(symbol, amount)
            // Annonce ou message sans identité propre : on retombe sur le
            // contenu, ce qui évite au moins les répétitions à l'identique.
            else -> "fcm:" + (message.data["key"] ?: "$title|$body")
        }
        hub.post(key = key, title = title, body = body, symbol = symbol)
    }

}
