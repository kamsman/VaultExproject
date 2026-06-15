package com.vaultex.data.remote.api

import com.vaultex.data.remote.dto.*
import okhttp3.RequestBody
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
    suspend fun broadcastTx(@Body rawHex: RequestBody): String

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

    @POST("wallet/createtransaction")
    suspend fun createTransaction(@Body body: TronCreateTxBody): TronRawTxDto

    @POST("wallet/triggersmartcontract")
    suspend fun triggerSmartContract(@Body body: TronTriggerSmartContractBody): TronTriggerSmartContractDto

    @POST("wallet/broadcasttransaction")
    suspend fun broadcast(@Body tx: TronBroadcastDto): TronBroadcastResultDto
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

/**
 * ChangeNOW — swaps cross-chain avec marge 1.5% côté VaultEx.
 * Base URL : https://api.changenow.io/v1/
 */
interface ChangeNowApi {
    @GET("exchange-amount/{amount}/{fromTo}")
    suspend fun getEstimatedAmount(
        @Path("amount") amount: String,
        @Path("fromTo") fromTo: String,  // ex: "btc_eth"
        @Query("api_key") apiKey: String
    ): ChangeNowEstimateDto

    @GET("min-amount/{fromTo}")
    suspend fun getMinAmount(
        @Path("fromTo") fromTo: String,
        @Query("api_key") apiKey: String
    ): ChangeNowMinAmountDto

    @POST("transactions/{apiKey}")
    suspend fun createTransaction(
        @Path("apiKey") apiKey: String,
        @Body body: ChangeNowTransactionBody
    ): ChangeNowTransactionDto

    @GET("transactions/{id}/{apiKey}")
    suspend fun getTransactionStatus(
        @Path("id") transactionId: String,
        @Path("apiKey") apiKey: String
    ): ChangeNowStatusDto
}

/**
 * Flutterwave — Mobile Money UEMOA (Orange Money, Wave, Moov, Free).
 * Base URL : https://api.flutterwave.com/v3/
 * Requires Authorization: Bearer <secret_key> header.
 */
interface FlutterwaveApi {
    @POST("charges?type=mobile_money_franco")
    suspend fun charge(@Body body: FlutterwaveChargeBody): FlutterwaveChargeDto

    @GET("transactions/{id}/verify")
    suspend fun verify(@Path("id") transactionId: Long): FlutterwaveVerifyDto
}

/**
 * Etherscan / BscScan — historique de transactions EVM.
 * Compatible Etherscan API (api.etherscan.io et api.bscscan.com).
 */
interface EtherscanApi {
    @GET("api")
    suspend fun getTransactions(
        @Query("module") module: String = "account",
        @Query("action") action: String = "txlist",
        @Query("address") address: String,
        @Query("startblock") startBlock: Long = 0L,
        @Query("endblock") endBlock: Long = 99_999_999L,
        @Query("page") page: Int = 1,
        @Query("offset") offset: Int = 50,
        @Query("sort") sort: String = "desc",
        @Query("apikey") apiKey: String = ""
    ): EtherscanResponse
}

