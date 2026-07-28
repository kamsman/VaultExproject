package com.vaultex.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vaultex.R
import com.vaultex.core.session.PriceMoveSettings
import com.vaultex.data.local.dao.PriceAlertDao
import com.vaultex.data.local.entity.PriceAlertEntity
import com.vaultex.data.remote.api.CoinGeckoApi
import com.vaultex.data.remote.dto.CoinGeckoPriceDto
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.text.NumberFormat
import java.util.Locale

/**
 * Worker de prix, exécuté toutes les 15 min. Il fait DEUX choses en un seul
 * appel réseau :
 *
 * 1. **Alertes de variation** (automatiques, actives par défaut) : prévient
 *    quand une monnaie monte ou chute fortement sur 24 h. L'anti-spam est géré
 *    par [PriceMoveSettings] ; l'utilisateur peut les désactiver ou changer le
 *    seuil depuis l'écran Alertes.
 * 2. **Alertes de cible** (créées manuellement) : prévient quand un prix passe
 *    au-dessus/en dessous d'une valeur choisie. L'alerte est désactivée après
 *    déclenchement pour ne pas notifier en boucle.
 */
@HiltWorker
class PriceAlertWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val priceAlertDao: PriceAlertDao,
    private val coinGeckoApi: CoinGeckoApi,
    private val moveSettings: PriceMoveSettings,
    private val notificationCenter: com.vaultex.core.session.NotificationCenter
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val alerts = priceAlertDao.getActiveOnce()
            val moveEnabled = moveSettings.enabled

            // Un seul appel pour les deux usages : les monnaies suivies pour
            // les variations + celles visées par une alerte de cible.
            val ids = buildSet {
                if (moveEnabled) addAll(SYMBOL_TO_ID.values)
                alerts.forEach { SYMBOL_TO_ID[it.tokenSymbol]?.let(::add) }
            }
            if (ids.isEmpty()) return Result.success()

            val prices = coinGeckoApi.getPrices(
                ids = ids.joinToString(","),
                // "usd" est indispensable : CoinGecko ne renvoie la variation
                // 24 h (usd_24h_change) que si le dollar est demandé.
                vsCurrencies = "usd,xof",
                include24hChange = true,
                includeMarketCap = false
            )

            if (moveEnabled) checkMoves(prices)
            checkTargets(alerts, prices)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    /** Alertes automatiques : forte hausse / forte baisse sur 24 h. */
    private fun checkMoves(prices: Map<String, CoinGeckoPriceDto>) {
        val now = System.currentTimeMillis()
        SYMBOL_TO_ID.forEach { (symbol, id) ->
            val dto = prices[id] ?: return@forEach
            val change = dto.change24h
            // 0.0 = valeur par défaut du DTO, donc donnée absente et non pas
            // « le cours n'a pas bougé d'un centième ». On ne notifie jamais
            // sur une donnée qu'on n'a pas reçue.
            if (change == 0.0) return@forEach
            val direction = moveSettings.evaluate(symbol, change, now) ?: return@forEach
            notifyMove(symbol, change, dto.xof, isUp = direction == "up")
        }
    }

    /** Alertes créées par l'utilisateur (cible de prix atteinte). */
    private fun checkTargets(alerts: List<PriceAlertEntity>, prices: Map<String, CoinGeckoPriceDto>) {
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
    }

    private fun notifyMove(symbol: String, changePercent: Double, priceXof: Double, isUp: Boolean) {
        val ctx = applicationContext
        val manager = ctx.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MOVES,
                ctx.getString(R.string.price_moves_channel),
                // IMPORTANCE_DEFAULT : c'est une information de marché, pas un
                // mouvement de fonds — pas de son intrusif comme les dépôts.
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        val fmt = NumberFormat.getNumberInstance(Locale.FRANCE)
        val percent = String.format(Locale.FRANCE, "%+.1f %%", changePercent)
        val title = ctx.getString(
            if (isUp) R.string.price_move_up_title else R.string.price_move_down_title,
            symbol
        )
        val body = if (priceXof > 0) {
            fmt.maximumFractionDigits = if (priceXof < 100) 2 else 0
            ctx.getString(R.string.price_move_body, percent, fmt.format(priceXof))
        } else {
            ctx.getString(R.string.price_move_body_no_price, percent)
        }
        val notification = NotificationCompat.Builder(ctx, CHANNEL_MOVES)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setLargeIcon(NotifLogo.forSymbol(ctx, symbol))
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        // Identifiant distinct des alertes de cible, sinon une alerte de
        // variation écraserait une alerte de cible sur la même monnaie.
        manager.notify(("move_$symbol").hashCode(), notification)
        notificationCenter.push(title, body, symbol)
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
        val title = applicationContext.getString(R.string.alert_triggered_title, symbol)
        val body = applicationContext.getString(
            R.string.alert_triggered_body, symbol, condition, fmt.format(target), fmt.format(current)
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setLargeIcon(NotifLogo.forSymbol(applicationContext, symbol))
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(symbol.hashCode(), notification)
        notificationCenter.push(title, body, symbol)
    }

    companion object {
        const val CHANNEL_ID = "vaultex_price_alerts"
        const val CHANNEL_MOVES = "vaultex_price_moves"
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
