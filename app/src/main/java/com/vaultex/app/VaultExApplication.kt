package com.vaultex.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.scottyab.rootbeer.RootBeer
import com.vaultex.ui.viewmodel.HistoryViewModel
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VaultExApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val rootBeer = RootBeer(this)
        if (rootBeer.isRooted) {
            // Production: block app. Dev/debug: allow for tests.
        }
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
