package com.vaultex.data.remote.dto

import com.google.gson.annotations.SerializedName

// ─── JSON-RPC GENERIC (EVM + Solana) ──────────────────────────────
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: MutableList<Any> = mutableListOf(),
    val id: Int = 1
)
data class JsonRpcResponse(
    val jsonrpc: String,
    val id: Int,
    val result: Any?,
    val error: JsonRpcError? = null
)
data class JsonRpcError(val code: Int, val message: String)

// ─── BLOCKSTREAM (Bitcoin) ────────────────────────────────────────
data class BlockstreamAddressDto(
    val address: String,
    @SerializedName("chain_stats") val chainStats: ChainStats,
    @SerializedName("mempool_stats") val mempoolStats: ChainStats
)
data class ChainStats(
    @SerializedName("funded_txo_sum") val fundedSum: Long,
    @SerializedName("spent_txo_sum") val spentSum: Long,
    @SerializedName("tx_count") val txCount: Int
)
data class BlockstreamUtxoDto(
    val txid: String,
    val vout: Int,
    val value: Long,
    val status: BlockstreamStatus
)
data class BlockstreamStatus(
    val confirmed: Boolean,
    @SerializedName("block_height") val blockHeight: Long? = null,
    @SerializedName("block_time") val blockTime: Long? = null
)
data class BlockstreamTxDto(
    val txid: String,
    val version: Int,
    val locktime: Long,
    val vin: List<BlockstreamVin>,
    val vout: List<BlockstreamVout>,
    val fee: Long,
    val status: BlockstreamStatus
)
data class BlockstreamVin(val txid: String, val vout: Int, val prevout: BlockstreamVout?)
data class BlockstreamVout(val value: Long, @SerializedName("scriptpubkey_address") val address: String?)

// ─── TRON ──────────────────────────────────────────────────────────
data class TronAccountDto(val data: List<TronAccountData>)
data class TronAccountData(
    val balance: Long = 0,
    val address: String,
    val trc20: List<Map<String, String>>? = null  // [{contractAddr: balance}]
)
data class TronTxListDto(val data: List<TronTransactionDto>)
data class TronTransactionDto(
    val txID: String,
    @SerializedName("block_timestamp") val timestamp: Long,
    val ret: List<Map<String, String>>?,
    @SerializedName("raw_data") val rawData: TronRawData
)
data class TronRawData(val contract: List<Map<String, Any>>)
data class TronTrc20ListDto(val data: List<TronTrc20Tx>)
data class TronTrc20Tx(
    @SerializedName("transaction_id") val txId: String,
    @SerializedName("token_info") val tokenInfo: Map<String, Any>?,
    val from: String,
    val to: String,
    val value: String,
    @SerializedName("block_timestamp") val timestamp: Long
)
data class TronBroadcastDto(val raw_data_hex: String, val signature: List<String>)
data class TronBroadcastResultDto(val result: Boolean, val txid: String?, val message: String?)
data class TronCreateTxBody(
    val owner_address: String,
    val to_address: String,
    val amount: Long,
    val visible: Boolean = false  // false = hex addresses expected by TronGrid by default
)
data class TronRawTxDto(
    val txID: String,
    @SerializedName("raw_data_hex") val rawDataHex: String?
)
data class TronTriggerSmartContractBody(
    val owner_address: String,
    val contract_address: String,
    val function_selector: String,
    val parameter: String,
    val fee_limit: Long = 10_000_000,
    val call_value: Long = 0
)
data class TronTriggerSmartContractDto(
    val result: TronTriggerResult,
    val transaction: TronRawTxDto?
)
data class TronTriggerResult(val result: Boolean, val message: String? = null)

// ─── 1INCH ─────────────────────────────────────────────────────────
data class OneInchQuoteDto(
    val toAmount: String,
    val fromToken: OneInchToken?,
    val toToken: OneInchToken?,
    val protocols: List<List<List<OneInchProtocol>>>?,
    val gas: String?
)
data class OneInchToken(
    val address: String,
    val symbol: String,
    val name: String,
    val decimals: Int,
    val logoURI: String?
)
data class OneInchProtocol(val name: String, val part: Double)

data class OneInchSwapDto(
    val toAmount: String,
    val tx: OneInchTx
)
data class OneInchTx(
    val from: String,
    val to: String,
    val data: String,
    val value: String,
    val gas: Long,
    val gasPrice: String
)
data class OneInchTokensDto(val tokens: Map<String, OneInchToken>)

// ─── COINGECKO ─────────────────────────────────────────────────────
data class CoinGeckoPriceDto(
    val usd: Double = 0.0,
    val xof: Double = 0.0,
    @SerializedName("usd_24h_change") val change24h: Double = 0.0,
    @SerializedName("usd_market_cap") val marketCap: Double = 0.0
)
data class CoinGeckoMarketDto(
    val id: String,
    val symbol: String,
    val name: String,
    val image: String?,
    @SerializedName("current_price") val currentPrice: Double,
    @SerializedName("market_cap") val marketCap: Double,
    @SerializedName("market_cap_rank") val rank: Int,
    @SerializedName("total_volume") val volume24h: Double,
    @SerializedName("price_change_percentage_24h") val change24h: Double,
    val sparkline_in_7d: SparklineDto? = null
)
data class SparklineDto(val price: List<Double>)
data class CoinGeckoChartDto(val prices: List<List<Double>>)

// ─── CHANGENOW ─────────────────────────────────────────────────────
data class ChangeNowEstimateDto(
    @SerializedName("estimatedAmount") val estimatedAmount: String,
    @SerializedName("transactionSpeedForecast") val speedForecast: String? = null,
    @SerializedName("warningMessage") val warning: String? = null
)

data class ChangeNowMinAmountDto(
    @SerializedName("minAmount") val minAmount: Double
)

data class ChangeNowTransactionBody(
    val from: String,
    val to: String,
    val address: String,
    val amount: String,
    @SerializedName("refundAddress") val refundAddress: String? = null
)

data class ChangeNowTransactionDto(
    val id: String,
    @SerializedName("payinAddress") val payinAddress: String,
    @SerializedName("payoutAddress") val payoutAddress: String,
    @SerializedName("payinExtraId") val payinExtraId: String? = null,
    val amount: String
)

data class ChangeNowStatusDto(
    val id: String,
    val status: String,  // waiting, confirming, exchanging, sending, finished, failed
    @SerializedName("amountFrom") val amountFrom: String? = null,
    @SerializedName("amountTo") val amountTo: String? = null,
    val hash: String? = null
)

// ─── FLUTTERWAVE ───────────────────────────────────────────────
data class FlutterwaveChargeBody(
    val phone_number: String,
    val amount: String,
    val currency: String = "XOF",
    val tx_ref: String,
    val type: String = "mobile_money_franco",
    val network: String,           // ORANGE, MOOV, WAVE, FREE
    val email: String = "user@vaultex.app",
    val fullname: String = "VaultEx User"
)
data class FlutterwaveChargeDto(
    val status: String,
    val message: String,
    val data: FlutterwaveChargeData?
)
data class FlutterwaveChargeData(
    val id: Long,
    @SerializedName("tx_ref") val txRef: String,
    @SerializedName("flw_ref") val flwRef: String,
    val status: String,
    val currency: String,
    val amount: Double
)
data class FlutterwaveVerifyDto(
    val status: String,
    val message: String,
    val data: FlutterwaveVerifyData?
)
data class FlutterwaveVerifyData(
    val id: Long,
    val status: String,
    @SerializedName("tx_ref") val txRef: String,
    val amount: Double,
    val currency: String
)

// ─── ETHERSCAN / BSCSCAN ──────────────────────────────────────
data class EtherscanResponse(
    val status: String,
    val result: List<EtherscanTx>? = null
)
data class EtherscanTx(
    val hash: String,
    val from: String,
    val to: String,
    val value: String,      // wei as decimal string
    @SerializedName("timeStamp") val timeStamp: String,
    val gasUsed: String,
    val gasPrice: String,
    val isError: String,    // "0" = success, "1" = failed
    val confirmations: String = "0"
)
