package com.vaultex.data.remote.dto

import com.google.gson.annotations.SerializedName

// ─── JSON-RPC GENERIC (EVM + Solana) ──────────────────────────────
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: List<Any> = emptyList(),
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
    val usd: Double,
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

// ─── TRON CREATE TRANSACTION ──────────────────────────────────────
data class TronCreateTransferRequest(
    val owner_address: String,
    val to_address: String,
    val amount: Long,
    val visible: Boolean = true
)
data class TronUnsignedTxDto(
    val raw_data_hex: String,
    val txID: String,
    val raw_data: Map<String, Any>? = null
)

// ─── SOLANA RPC ────────────────────────────────────────────────────
data class SolanaBlockhashParams(val commitment: String = "finalized")

// ─── TRON SINGLE TX ────────────────────────────────────────────────
data class TronSingleTxListDto(val data: List<TronSingleTxData>)
data class TronSingleTxData(
    val txID: String,
    @SerializedName("blockNumber") val blockNumber: Long? = null,
    val ret: List<Map<String, String>>? = null
)
