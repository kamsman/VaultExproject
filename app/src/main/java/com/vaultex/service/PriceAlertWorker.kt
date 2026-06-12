package com.vaultex.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vaultex.R
import com.vaultex.data.local.dao.PriceAlertDao
import com.vaultex.data.remote.api.CoinGeckoApi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.text.NumberFormat
import java.util.Locale

/**
 * Vérifie périodiquement (15 min) les alertes de prix actives contre
 * les prix CoinGecko et envoie une notification locale quand une cible
 * est atteinte. L'alerte est désactivée après déclenchement pour ne pas
 * notifier en boucle.
 */
@HiltWorker
class PriceAlertWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val priceAlertDao: PriceAlertDao,
    private val coinGeckoApi: CoinGeckoApi
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val alerts = priceAlertDao.getActiveOnce()
            if (alerts.isEmpty()) return Result.success()

            val ids = alerts.mapNotNull { SYMBOL_TO_ID[it.tokenSymbol] }.distinct()
            if (ids.isEmpty()) return Result.success()

            val prices = coinGeckoApi.getPrices(
                ids = ids.joinToString(","),
                vsCurrencies = "xof",
                include24hChange = false,
                includeMarketCap = false
            )

            alerts.forEach { alert ->
                val id = SYMBOL_TO_ID[alert.tokenSymbol] ?: return@forEach
                val current = prices[id]?.xof?.takeIf { it > 0 } ?: return@forEach
                val target = alert.targetPrice.toDoubleOrNull() ?: return@forEach
                val isAbove = alert.condition.contains("dessus", ignoreCase = true)
                val triggered = (isAbove && current >= target) || (!isAbove && current <= target)
                if (triggered) {
                    notify(alert.tokenSymbol, alert.condition, target, current)
                    priceAlertDao.setActive(alert.id, false)
                }
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun notify(symbol: String, condition: String, target: Double, current: Double) {
        val fmt = NumberFormat.getNumberInstance(Locale.FRANCE)
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.alerts_title),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(applicationContext.getString(R.string.alert_triggered_title, symbol))
            .setContentText(
                applicationContext.getString(
                    R.string.alert_triggered_body,
                    symbol, condition, fmt.format(target), fmt.format(current)
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(symbol.hashCode(), notification)
    }

    companion object {
        const val CHANNEL_ID = "vaultex_price_alerts"
        const val WORK_NAME = "price_alert_check"

        private val SYMBOL_TO_ID = mapOf(
            "BTC" to "bitcoin",
            "ETH" to "ethereum",
            "BNB" to "binancecoin",
            "SOL" to "solana",
            "TRX" to "tron",
            "USDT" to "tether"
        )
    }
}
