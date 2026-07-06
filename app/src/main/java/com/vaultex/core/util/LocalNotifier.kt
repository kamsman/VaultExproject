package com.vaultex.core.util

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.vaultex.R

/** Notification système simple (canal push par défaut, logo VaultEx). */
object LocalNotifier {
    fun show(context: Context, title: String, body: String) {
        try {
            val logo = android.graphics.BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
            val n = NotificationCompat.Builder(context, com.vaultex.app.VaultExApplication.FCM_DEFAULT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setLargeIcon(logo)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            context.getSystemService(NotificationManager::class.java)
                .notify(System.currentTimeMillis().toInt(), n)
        } catch (_: Exception) { /* permission refusée : silencieux */ }
    }
}
