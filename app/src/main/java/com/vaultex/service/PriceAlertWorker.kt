package com.vaultex.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
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
    private val priceFallback: com.vaultex.data.repository.PriceFallbackSource,
    private val moveSettings: PriceMoveSettings,
    private val hub: com.vaultex.core.session.NotificationHub
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

            /*
            Source principale, puis SECOURS pour ce qu'elle n'a pas rendu.

            C'est ce worker qui remontait « ⚠️ Service indisponible : alertes
            de prix — HTTP 429 » en boucle sur le canal d'administration : le
            quota mensuel de CoinGecko épuisé, l'appel échouait à chaque
            réveil. Deux conséquences, l'alerte n'étant que la plus visible :
            plus aucune alerte de prix ne pouvait se déclencher, puisqu'il n'y
            avait plus de prix à comparer aux seuils.

            La source de secours n'a pas de quota mensuel. Un échec des DEUX
            reste signalé — c'est alors une vraie panne, pas un plafond.
             */
            val coursPrincipaux: Map<String, CoinGeckoPriceDto> = try {
                coinGeckoApi.getPrices(
                    ids = ids.joinToString(","),
                    // "usd" est indispensable : CoinGecko ne renvoie la variation
                    // 24 h (usd_24h_change) que si le dollar est demandé.
                    vsCurrencies = "usd,xof",
                    include24hChange = true,
                    includeMarketCap = false
                )
            } catch (_: Exception) { emptyMap() }
            val nonCotees = ids.filter { (coursPrincipaux[it]?.usd ?: 0.0) <= 0.0 }
            val prices = if (nonCotees.isEmpty()) coursPrincipaux
                else coursPrincipaux + priceFallback.pricesByCoinGeckoId(nonCotees)
            if (prices.isEmpty()) return Result.success()

            if (moveEnabled) checkMoves(prices)
            checkTargets(alerts, prices)
            Result.success()
        } catch (e: Exception) {
            /*
            `success` et non `retry`, comme pour DepositCheckWorker.

            CoinGecko limite le débit : un 429 est le cas d'échec le plus
            courant ici. Avec `retry`, WorkManager reprogramme avec un délai qui
            DOUBLE à chaque échec — et tant qu'il attend, ce travail périodique
            ne tourne plus. Quelques limitations d'affilée suffisent donc à
            éteindre les alertes de prix pour des heures, sans que rien ne
            l'indique.

            Le travail repasse de toute façon dans 15 minutes : renoncer à ce
            cycle-ci est la bonne réponse, réessayer en boucle ne l'est pas.
             */
            com.vaultex.core.monitoring.reportUnlessCancelled("alertes de prix", e)
            Result.success()
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

    /** Alertes créées par l'utilisateur (cible de prix atteinte).
     *  `suspend` : la désactivation après déclenchement passe par le DAO. */
    private suspend fun checkTargets(alerts: List<PriceAlertEntity>, prices: Map<String, CoinGeckoPriceDto>) {
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
        /*
        Canal SÉPARÉ, mais en importance HAUTE.

        Séparé, pour que l'utilisateur puisse couper les alertes de marché
        depuis les réglages Android SANS perdre les alertes de fonds reçus —
        deux besoins très différents ne doivent pas partager un interrupteur.

        Haute, parce qu'une alerte de prix qui dort au fond du volet ne
        ramène personne dans l'application : elle doit s'afficher en haut de
        l'écran comme les autres.

        Le suffixe « _v2 » est nécessaire : Android FIGE l'importance d'un
        canal à sa création et ignore toute modification ultérieure. Sans
        nouvel identifiant, les appareils ayant déjà lancé l'app resteraient
        bloqués sur l'ancienne importance, discrète.
         */
        val manager = ctx.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MOVES,
                ctx.getString(R.string.price_moves_channel),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        val fmt = NumberFormat.getNumberInstance(com.vaultex.core.session.LocaleManager.appLocale())
        val percent = String.format(com.vaultex.core.session.LocaleManager.appLocale(), "%+.1f %%", changePercent)
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
        // Par le hub : bannière, pastille de l'icône et cloche d'un seul geste.
        // L'anti-spam propre aux variations est déjà assuré en amont par
        // PriceMoveSettings (réarmement + délai de garde de 12 h).
        hub.post(
            key = "move:$symbol:${if (isUp) "up" else "down"}:${changePercent.toInt()}",
            title = title,
            body = body,
            symbol = symbol,
            channelId = CHANNEL_MOVES
        )
    }

    private fun notify(symbol: String, condition: String, target: Double, current: Double) {
        val ctx = applicationContext
        val fmt = NumberFormat.getNumberInstance(com.vaultex.core.session.LocaleManager.appLocale())
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                ctx.getString(R.string.alerts_title),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        val title = ctx.getString(R.string.alert_triggered_title, symbol)
        val body = ctx.getString(
            R.string.alert_triggered_body, symbol, condition, fmt.format(target), fmt.format(current)
        )
        // Une cible ne se déclenche qu'une fois (l'alerte est désactivée juste
        // après) : la clé porte la cible elle-même.
        hub.post(
            key = "target:$symbol:$condition:$target",
            title = title,
            body = body,
            symbol = symbol,
            channelId = CHANNEL_ID
        )
    }

    companion object {
        const val CHANNEL_ID = "vaultex_price_alerts"
        const val CHANNEL_MOVES = "vaultex_price_moves_v2"
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
