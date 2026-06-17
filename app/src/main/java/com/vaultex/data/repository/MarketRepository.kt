package com.vaultex.data.repository

import com.vaultex.data.remote.api.CoinGeckoApi
import com.vaultex.data.remote.dto.CoinGeckoChartDto
import com.vaultex.data.remote.dto.CoinGeckoMarketDto
import javax.inject.Inject

class MarketRepository @Inject constructor(
    private val api: CoinGeckoApi
) {

    suspend fun getMarkets(): List<CoinGeckoMarketDto> {
        // L'écran Marché affiche en FCFA : on demande directement les prix en XOF.
        return api.getMarkets(vsCurrency = "xof")
    }

    /** Courbe de prix d'un token sur [days] jours (CoinGecko market_chart). */
    suspend fun getMarketChart(coinId: String, days: Int): CoinGeckoChartDto {
        return api.getMarketChart(coinId, days = days)
    }
}