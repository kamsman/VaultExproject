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

    suspend fun getMarkets(): List<CoinGeckoMarketDto> {
        val list = api.getMarkets(vsCurrency = "usd")
        if (list.isNotEmpty()) cache = list
        return list
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
