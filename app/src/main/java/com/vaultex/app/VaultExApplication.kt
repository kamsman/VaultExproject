package com.vaultex.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.vaultex.BuildConfig
import com.vaultex.core.monitoring.CrashReporter
import com.vaultex.core.security.DeviceIntegrity
import com.vaultex.service.DepositCheckWorker
import com.vaultex.service.PendingSendWorker
import com.vaultex.service.PriceAlertWorker
import com.vaultex.service.SwapTrackingWorker
import com.vaultex.ui.viewmodel.HistoryViewModel
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class VaultExApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Bot Telegram admin : code d'installation (VX-XXXXXX) apposé aux événements.
        com.vaultex.core.monitoring.AdminBot.init(this)
        // 📲 Compte les installations réelles (une seule annonce par téléphone).
        com.vaultex.core.monitoring.AdminBot.announceInstallOnce()
        // 💥 Rapport de crash Telegram — s'AJOUTE devant le handler existant
        // (Crashlytics/système), ne remplace rien.
        com.vaultex.core.monitoring.AdminBot.installCrashHandler()
        // AVANT toute planification de worker : les workers formatent des
        // notifications application fermée, où aucune Activity n'a encore
        // appelé LocaleManager.wrap(). Sans cette amorce, un dépôt détecté
        // après un redémarrage du téléphone serait annoncé avec le format de
        // nombres du système, pas celui de la langue choisie dans l'app.
        com.vaultex.core.session.LocaleManager.prime(this)
        createNotificationChannel()
        subscribeToAnnouncements()
        schedulePriceAlertChecks()
        scheduleDepositChecks()
        scheduleSwapTracking()
        // Reprend les envois mis en file lors d'une session précédente
        // (le worker attend tout seul le retour du réseau).
        PendingSendWorker.enqueue(this)

        // Contexte enrichi pour les rapports de crash (Crashlytics).
        CrashReporter.setKey("app_version", BuildConfig.VERSION_NAME)
        CrashReporter.setKey("device_rooted", DeviceIntegrity.isDeviceRooted(this))
        CrashReporter.log("Application démarrée")

        // Play Integrity (best-effort, no-op tant que le projet n'est pas configuré).
        DeviceIntegrity.requestIntegrityToken(this)
    }


    /*
    ═══════════════════════════════════════════════════════════════════════
    CONTRAINTE RÉSEAU SUR LES TÂCHES PÉRIODIQUES
    ═══════════════════════════════════════════════════════════════════════

    Les trois tâches ci-dessous interrogent toutes des services distants :
    soldes des chaînes, cours, statut des échanges. Sans contrainte, Android
    les réveillait toutes les 15 minutes même hors connexion — chaque
    réveil consommait de la batterie pour échouer aussitôt, et remontait un
    échec qui ne signalait rien d'autre que l'absence de réseau.

    C'est particulièrement coûteux sur le marché visé, où la couverture est
    intermittente et l'autonomie compte.

    Avec la contrainte, le système attend le retour du réseau puis déclenche
    la tâche. Rien n'est perdu : le travail est simplement différé.
    ═══════════════════════════════════════════════════════════════════════
     */
    private val reseauRequis = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /*
     * Vérification des alertes de prix — toutes les HEURES, plus toutes les
     * 15 minutes.
     *
     * Le quota gratuit de CoinGecko est de 10 000 appels par MOIS. À 15
     * minutes, ce seul worker en consommait 96 par jour et par téléphone,
     * soit près de 2 900 par mois — l'essentiel du quota, avant même qu'un
     * utilisateur ait ouvert l'application. Deux téléphones de test l'ont
     * épuisé, et l'API a répondu 429 sur tout : plus aucun prix affiché.
     *
     * Une heure ramène ce coût à 720 appels par mois et par téléphone. Pour
     * une alerte de prix, l'écart est sans conséquence : personne ne surveille
     * un seuil à la minute près sur un portefeuille mobile.
     *
     * Android n'accepte de toute façon pas de garantie plus fine : la période
     * est un minimum, pas une promesse.
     */
    private fun schedulePriceAlertChecks() {
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            PriceAlertWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<PriceAlertWorker>(1, TimeUnit.HOURS)
                .setConstraints(reseauRequis).build()
        )
    }

    /**
     * Suivi des échanges en cours, toutes les 15 minutes.
     *
     * Sans lui, le suivi d'un swap mourait avec l'écran Swap : l'utilisateur
     * devait rester devant, parfois plusieurs heures, sinon la notification de
     * fin ne partait jamais. Le worker reprend le suivi depuis la base, donc il
     * survit à la navigation, à la fermeture de l'app et au redémarrage.
     */
    private fun scheduleSwapTracking() {
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SwapTrackingWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<SwapTrackingWorker>(15, TimeUnit.MINUTES)
                .setConstraints(reseauRequis).build()
        )
    }

    /** Détection locale des dépôts reçus toutes les 15 minutes (secours du push). */
    private fun scheduleDepositChecks() {
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            DepositCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<DepositCheckWorker>(15, TimeUnit.MINUTES)
                .setConstraints(reseauRequis).build()
        )
    }

    /**
     * Abonne l'appareil au canal de diffusion des annonces.
     *
     * POURQUOI UN CANAL PLUTOT QUE DES JETONS. Envoyer une annonce a des
     * jetons individuels supposerait de les collecter cote serveur — c'est le
     * role de `registerDevice`, une Cloud Function qui exige le plan Blaze,
     * donc une carte bancaire et une facturation a l'usage. Un canal FCM ne
     * demande rien : l'appareil s'y inscrit lui-meme, et un seul message
     * atteint tout le monde. Gratuit, sans serveur, sans limite.
     *
     * POURQUOI CA REGLE LE PROBLEME DE LA CLOCHE. Une annonce envoyee depuis
     * la console Firebase est un message de type « notification » : le SDK
     * l'affiche lui-meme et n'appelle jamais `onMessageReceived`. Le contenu
     * n'atteint donc jamais le code, et rien ne peut etre inscrit dans la
     * cloche — verifie sur appareil : l'intention de lancement arrive sans
     * aucun extra, meme lorsque l'utilisateur touche la notification.
     *
     * Un message « data » envoye a ce canal, lui, passe TOUJOURS par
     * `VaultExFcmService.onMessageReceived`, que l'application soit ouverte,
     * fermee ou en arriere-plan, et que la notification soit touchee ou non.
     * L'annonce s'affiche ET reste consultable dans la cloche.
     *
     * Voir tools/send-announcement.sh pour l'envoi.
     *
     * Best-effort : un echec d'abonnement ne doit jamais empecher
     * l'application de demarrer. Firebase reessaie de lui-meme.
     */
    private fun subscribeToAnnouncements() {
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance()
                .subscribeToTopic(ANNOUNCE_TOPIC)
        } catch (_: Exception) { /* sans impact sur le demarrage */ }
    }

    private fun createNotificationChannel() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        // Canal transactions (notifs locales existantes).
        notificationManager.createNotificationChannel(
            NotificationChannel(
                HistoryViewModel.CHANNEL_ID,
                "Transactions VaultEx",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications pour les transactions crypto reçues"
                // Pastille sur l'icône du lanceur, y compris app fermée.
                setShowBadge(true)
            }
        )
        // Canal PUSH par défaut (FCM / console Firebase). DOIT exister sinon les
        // messages « notification » reçus en arrière-plan sont ignorés par le SDK.
        notificationManager.createNotificationChannel(
            NotificationChannel(
                FCM_DEFAULT_CHANNEL_ID,
                "Notifications VaultEx",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertes et messages push VaultEx"
                setShowBadge(true)
            }
        )
    }

    companion object {
        // Doit correspondre au meta-data default_notification_channel_id du manifeste
        // ET au CHANNEL_ID de VaultExFcmService.
        const val FCM_DEFAULT_CHANNEL_ID = "vaultex_notifications"

        /**
         * Canal FCM de diffusion des annonces.
         *
         * Tout appareil s'y abonne au démarrage. Un seul message envoyé à
         * `/topics/vaultex_all` atteint donc l'ensemble du parc, sans qu'aucun
         * serveur n'ait à connaître les jetons individuels.
         *
         * Ce nom est un CONTRAT : le changer couperait tous les appareils déjà
         * installés, qui resteraient abonnés à l'ancien canal jusqu'à leur
         * prochaine mise à jour. Il doit rester synchronisé avec
         * tools/send-announcement.sh.
         */
        const val ANNOUNCE_TOPIC = "vaultex_all"
    }
}
