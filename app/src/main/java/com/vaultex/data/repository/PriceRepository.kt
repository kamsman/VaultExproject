package com.bidichange.cryptowallet.data.repository

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object PriceRepository {

    private val client = OkHttpClient()

    suspend fun getNativeUsdPrice(networkId: String): Double {
        val coinId = when (networkId) {
            "ethereum" -> "ethereum"
            "bnb" -> "binancecoin"
            "polygon" -> "matic-network"
            "arbitrum" -> "ethereum"
            else -> "ethereum"
        }

        val url =
            "https://api.coingecko.com/api/v3/simple/price" +
                    "?ids=$coinId&vs_currencies=usd"

        val request = Request.Builder()
            .url(url)
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return 0.0

        val json = JSONObject(body)

        if (!json.has(coinId)) return 0.0

        return json.getJSONObject(coinId).optDouble("usd", 0.0)
    }

    suspend fun getTokenUsdPrice(
        networkId: String,
        contractAddress: String
    ): Double {

        val networkPath = when (networkId) {
            "ethereum" -> "ethereum"
            "bnb" -> "binance-smart-chain"
            "polygon" -> "polygon-pos"
            "arbitrum" -> "arbitrum-one"
            else -> "ethereum"
        }

        val url =
            "https://api.coingecko.com/api/v3/simple/token_price/$networkPath" +
                    "?contract_addresses=$contractAddress&vs_currencies=usd"

        val request = Request.Builder()
            .url(url)
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return 0.0

        val json = JSONObject(body)
        val key = contractAddress.lowercase()

        if (!json.has(key)) return 0.0

        return json.getJSONObject(key).optDouble("usd", 0.0)
    }
}