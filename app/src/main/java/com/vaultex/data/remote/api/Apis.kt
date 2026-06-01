package com.vaultex.data.remote.api

import com.vaultex.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Ethereum / BNB JSON-RPC interface (compatible Infura, Alchemy, BSC public RPC).
 */
interface EvmRpcApi {
    @POST("./")
    @Headers("Content-Type: application/json")
    suspend fun rpcCall(@Body request: JsonRpcRequest): JsonRpcResponse
}

/**
 * Bitcoin via Blockstream API (REST).
 */
interface BitcoinApi {
    @GET("address/{address}")
    suspend fun getAddressInfo(@Path("address") address: String): BlockstreamAddressDto

    @GET("address/{address}/utxo")
    suspend fun getUtxos(@Path("address") address: String): List<BlockstreamUtxoDto>

    @GET("address/{address}/txs")
    suspend fun getTransactions(@Path("address") address: String): List<BlockstreamTxDto>

    @GET("tx/{txid}")
    suspend fun getTransaction(@Path("txid") txid: String): BlockstreamTxDto

    @POST("tx")
    @Headers("Content-Type: text/plain")
    suspend fun broadcastTx(@Body rawHex: String): String

    @GET("fee-estimates")
    suspend fun getFeeEstimates(): Map<String, Double>
}

/**
 * Solana JSON-RPC.
 */
interface SolanaRpcApi {
    @POST("./")
    @Headers("Content-Type: application/json")
    suspend fun rpcCall(@Body request: JsonRpcRequest): JsonRpcResponse
}

/**
 * Tron via TronGrid REST API.
 */
interface TronApi {
    @GET("v1/accounts/{address}")
    suspend fun getAccount(@Path("address") address: String): TronAccountDto

    @GET("v1/accounts/{address}/transactions")
    suspend fun getTransactions(
        @Path("address") address: String,
        @Query("limit") limit: Int = 50
    ): TronTxListDto

    @GET("v1/accounts/{address}/transactions/trc20")
    suspend fun getTrc20Transactions(
        @Path("address") address: String,
        @Query("limit") limit: Int = 50
    ): TronTrc20ListDto

    @POST("wallet/broadcasttransaction")
    suspend fun broadcast(@Body tx: TronBroadcastDto): TronBroadcastResultDto

    @POST("wallet/createtransaction")
    suspend fun createTransfer(@Body body: TronCreateTransferRequest): TronUnsignedTxDto
}

/**
 * 1inch DEX Aggregator API — pour les swaps cross-token.
 * Frais VaultEx 1.5% intégrés via paramètre fee.
 */
interface OneInchApi {
    @GET("swap/v6.0/{chainId}/quote")
    suspend fun getQuote(
        @Path("chainId") chainId: Int,
        @Query("src") fromTokenAddress: String,
        @Query("dst") toTokenAddress: String,
        @Query("amount") amount: String,
        @Query("fee") feePercent: Double = 1.5,
        @Query("includeProtocols") includeProtocols: Boolean = true,
        @Query("includeGas") includeGas: Boolean = true
    ): OneInchQuoteDto

    @GET("swap/v6.0/{chainId}/swap")
    suspend fun getSwapData(
        @Path("chainId") chainId: Int,
        @Query("src") fromTokenAddress: String,
        @Query("dst") toTokenAddress: String,
        @Query("amount") amount: String,
        @Query("from") fromAddress: String,
        @Query("slippage") slippagePercent: Double,
        @Query("fee") feePercent: Double = 1.5,
        @Query("referrer") feeRecipient: String,
        @Query("disableEstimate") disableEstimate: Boolean = false
    ): OneInchSwapDto

    @GET("swap/v6.0/{chainId}/tokens")
    suspend fun getSupportedTokens(@Path("chainId") chainId: Int): OneInchTokensDto
}

/**
 * CoinGecko Pro — prix temps réel et historique.
 */
interface CoinGeckoApi {
    @GET("simple/price")
    suspend fun getPrices(
        @Query("ids") ids: String,         // ex: "bitcoin,ethereum,solana"
        @Query("vs_currencies") vsCurrencies: String = "usd",
        @Query("include_24hr_change") include24hChange: Boolean = true,
        @Query("include_market_cap") includeMarketCap: Boolean = true
    ): Map<String, CoinGeckoPriceDto>

    @GET("coins/markets")
    suspend fun getMarkets(
        @Query("vs_currency") vsCurrency: String = "usd",
        @Query("order") order: String = "market_cap_desc",
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1,
        @Query("sparkline") sparkline: Boolean = true,
        @Query("price_change_percentage") priceChangeRanges: String = "24h"
    ): List<CoinGeckoMarketDto>

    @GET("coins/{id}/market_chart")
    suspend fun getMarketChart(
        @Path("id") coinId: String,
        @Query("vs_currency") vsCurrency: String = "usd",
        @Query("days") days: Int
    ): CoinGeckoChartDto
}
