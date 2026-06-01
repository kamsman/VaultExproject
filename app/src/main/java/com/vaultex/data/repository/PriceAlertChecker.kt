package com.vaultex.data.repository

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.vaultex.app.MainActivity
import com.vaultex.data.local.dao.PriceAlertDao
import com.vaultex.data.local.entity.PriceAlertEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PriceAlertChecker @Inject constructor(
    private val priceAlertDao: PriceAlertDao,
    private val priceRepository: PriceRepository,
    @ApplicationContext private val context: Context
) {
    private val symbolToId = mapOf(
        "BTC" to "bitcoin", "ETH" to "ethereum", "BNB" to "binancecoin",
        "SOL" to "solana", "TRX" to "tron"
    )

    suspend fun check() = withContext(Dispatchers.IO) {
        val alerts = priceAlertDao.observeActive().first()
        if (alerts.isEmpty()) return@withContext

        val coinIds = alerts.mapNotNull { symbolToId[it.tokenSymbol] }.distinct()
        val prices = try { priceRepository.getMultiplePrices(coinIds) } catch (_: Exception) { return@withContext }

        for (alert in alerts) {
            val coinId = symbolToId[alert.tokenSymbol] ?: continue
            val currentPrice = prices[coinId] ?: continue
            val targetPrice = alert.targetPrice.toDoubleOrNull() ?: continue

            val triggered = when (alert.condition) {
                "ABOVE" -> currentPrice >= targetPrice
                "BELOW" -> currentPrice <= targetPrice
                else -> false
            }

            if (triggered) {
                fireNotification(alert, currentPrice)
                priceAlertDao.setActive(alert.id, false)
            }
        }
    }

    private fun fireNotification(alert: PriceAlertEntity, currentPrice: Double) {
        val conditionLabel = if (alert.condition == "ABOVE") "dépasse" else "passe sous"
        val title = "🚨 Alerte ${alert.tokenSymbol}"
        val body = "${alert.tokenSymbol} $conditionLabel ${"$"}${alert.targetPrice} · Prix actuel : ${"$"}${"%.2f".format(currentPrice)}"

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, alert.id.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(alert.id.hashCode(), notification)
    }

    companion object {
        private const val CHANNEL_ID = "vaultex_notifications"
    }
}
