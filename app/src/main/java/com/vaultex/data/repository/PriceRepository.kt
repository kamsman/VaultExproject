package com.vaultex.data.repository

import com.vaultex.data.remote.api.CoinGeckoApi
import javax.inject.Inject

class PriceRepository @Inject constructor(
    private val api: CoinGeckoApi
) {

    /**
     * Prix USD + variation 24h pour plusieurs tokens natifs en une requête.
     * @param coinGeckoIds ex: ["bitcoin","ethereum","binancecoin","solana","tron"]
     */
    suspend fun getMultiplePrices(
        coinGeckoIds: List<String>
    ): Map<String, Double> = try {
        api.getPrices(
            ids = coinGeckoIds.joinToString(","),
            vsCurrencies = "usd",
            include24hChange = false,
            includeMarketCap = false
        ).mapValues { (_, dto) -> dto.usd }
    } catch (_: Exception) {
        emptyMap()
    }

    /** Prix USD d'un seul token natif. */
    suspend fun getNativeUsdPrice(coinGeckoId: String): Double = try {
        api.getPrices(
            ids = coinGeckoId,
            vsCurrencies = "usd",
            include24hChange = false,
            includeMarketCap = false
        )[coinGeckoId]?.usd ?: 0.0
    } catch (_: Exception) {
        0.0
    }
}
