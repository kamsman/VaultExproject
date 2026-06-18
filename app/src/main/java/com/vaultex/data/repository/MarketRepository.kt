package com.vaultex.data.repository

import com.vaultex.data.remote.api.CoinGeckoApi
import com.vaultex.data.remote.dto.CoinGeckoChartDto
import com.vaultex.data.remote.dto.CoinGeckoMarketDto
import javax.inject.Inject

class MarketRepository @Inject constructor(
    private val api: CoinGeckoApi
) {

    // Cache mémoire de la dernière liste marché (instance singleton partagée).
    // L'écran détail le réutilise → ouverture instantanée SANS nouvel appel
    // réseau, ce qui évite le rate-limit CoinGecko (cause des « impossible de
    // charger »). Tout est en USD pour que liste et détail soient cohérents.
    @Volatile
    private var cache: List<CoinGeckoMarketDto> = emptyList()
    @Volatile
    private var cacheTime: Long = 0L
    private val cacheTtlMs = 90_000L

    suspend fun getMarkets(): List<CoinGeckoMarketDto> {
        val now = System.currentTimeMillis()
        // Sert le cache si récent → bien moins d'appels CoinGecko (donc moins de
        // rate-limit) et le cache reste rempli pour l'écran détail.
        if (cache.isNotEmpty() && now - cacheTime < cacheTtlMs) return cache
        return try {
            val list = api.getMarkets(vsCurrency = "usd")
            if (list.isNotEmpty()) {
                cache = list
                cacheTime = now
            }
            if (list.isNotEmpty()) list else cache
        } catch (e: Exception) {
            // En cas d'échec (rate-limit/réseau), on garde la dernière liste connue.
            if (cache.isNotEmpty()) cache else throw e
        }
    }

    /**
     * Détail d'UNE pièce. D'abord le cache (0 appel réseau) ; sinon un appel
     * léger filtré (ids=) en repli, par ex. si on arrive directement sur le détail.
     */
    suspend fun getMarket(coinId: String): List<CoinGeckoMarketDto> {
        cache.firstOrNull { it.id == coinId }?.let { return listOf(it) }
        // sparkline=true → ramène aussi la courbe 7j (utilisée par l'écran détail),
        // ce qui évite un second appel réseau pour le graphique.
        val list = api.getMarkets(vsCurrency = "usd", sparkline = true, ids = coinId)
        if (list.isNotEmpty()) cache = (cache + list).distinctBy { it.id }
        return list
    }

    /** Courbe de prix d'un token sur [days] jours (CoinGecko market_chart, USD). */
    suspend fun getMarketChart(coinId: String, days: Int): CoinGeckoChartDto {
        return api.getMarketChart(coinId, days = days)
    }
}
