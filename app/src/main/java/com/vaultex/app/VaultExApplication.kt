package com.vaultex.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.vaultex.BuildConfig
import com.vaultex.core.monitoring.CrashReporter
import com.vaultex.core.security.DeviceIntegrity
import com.vaultex.service.PendingSendWorker
import com.vaultex.service.PriceAlertWorker
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
        createNotificationChannel()
        schedulePriceAlertChecks()
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

    /** Vérification des alertes de prix toutes les 15 minutes. */
    private fun schedulePriceAlertChecks() {
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            PriceAlertWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PriceAlertWorker>(15, TimeUnit.MINUTES).build()
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            HistoryViewModel.CHANNEL_ID,
            "Transactions VaultEx",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications pour les transactions crypto reçues"
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
}
