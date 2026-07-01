package com.vaultex.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.vaultex.R
import com.vaultex.app.MainActivity

/**
 * Service Firebase Cloud Messaging.
 * Reçoit les notifications push pour:
 * - Alertes prix configurées par l'utilisateur
 * - Confirmation de transactions reçues
 */
@dagger.hilt.android.AndroidEntryPoint
class VaultExFcmService : FirebaseMessagingService() {

    @javax.inject.Inject
    lateinit var notificationCenter: com.vaultex.core.session.NotificationCenter

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Enregistrer le token côté backend si vous avez un backend optionnel
        // Pour les alertes de prix, le backend peut envoyer des push à ce token
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: message.data["title"] ?: "VaultEx"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        showNotification(title, body)
        notificationCenter.push(title, body, message.data["symbol"])
    }

    private fun showNotification(title: String, body: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VaultEx Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val logo = android.graphics.BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setLargeIcon(logo)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    companion object {
        private const val CHANNEL_ID = "vaultex_notifications"
    }
}
