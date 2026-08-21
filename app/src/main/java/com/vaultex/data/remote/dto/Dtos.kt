package com.vaultex.data.remote.dto

import com.google.gson.annotations.SerializedName

// ─── JSON-RPC GENERIC (EVM + Solana) ──────────────────────────────
data class JsonRpcRequest(
    val method: String,
    val params: MutableList<Any> = mutableListOf(),
    val jsonrpc: String = "2.0",
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
data class TronTxInfoBody(val value: String)
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
    val fee_limit: Long = 100_000_000,   // 100 TRX : couvre un transfert TRC-20 sans énergie stakée
    val call_value: Long = 0
)
data class TronTriggerSmartContractDto(
    val result: TronTriggerResult,
    val transaction: TronRawTxDto?
)
data class TronTriggerResult(val result: Boolean, val message: String? = null)


// ─── COINGECKO ─────────────────────────────────────────────────────
data class CoinGeckoPriceDto(
    val usd: Double = 0.0,
    val xof: Double = 0.0,
    val eur: Double = 0.0,
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
    @SerializedName("high_24h") val high24h: Double = 0.0,
    @SerializedName("low_24h") val low24h: Double = 0.0,
    @SerializedName("ath") val ath: Double = 0.0,
    @SerializedName("circulating_supply") val circulatingSupply: Double = 0.0,
    val sparkline_in_7d: SparklineDto? = null
)
data class SparklineDto(val price: List<Double>)
data class CoinGeckoChartDto(val prices: List<List<Double>>)

// Détail léger d'une pièce — uniquement ses adresses de contrat par réseau
// (« platforms »). Clés utiles : "ethereum" et "binance-smart-chain".
// CoinGecko renvoie une chaîne VIDE (pas null) pour un réseau sans contrat.
data class CoinGeckoDetailDto(
    val id: String,
    val platforms: Map<String, String>? = null
)

// Données globales du marché (cap totale, dominance BTC).
data class CoinGeckoGlobalDto(val data: CoinGeckoGlobalData? = null)
data class CoinGeckoGlobalData(
    @SerializedName("total_market_cap") val totalMarketCap: Map<String, Double> = emptyMap(),
    @SerializedName("market_cap_percentage") val marketCapPercentage: Map<String, Double> = emptyMap(),
    @SerializedName("market_cap_change_percentage_24h_usd") val mcapChange24h: Double = 0.0
)

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
    val hash: String? = null,
    // Hash du paiement SORTANT de ChangeNOW vers le portefeuille (dispo à
    // « finished ») : sert à afficher le badge « En attente » sur la monnaie
    // reçue tant que le versement n'est pas confirmé sur sa chaîne.
    @SerializedName("payoutHash") val payoutHash: String? = null
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
    /**
     * Motif du refus quand status vaut « 0 » : « Missing/Invalid API Key »,
     * « Max rate limit reached », « No transactions found »…
     *
     * Ce champ n'etait pas lu, alors qu'il porte l'explication : le refus
     * d'Etherscan/BscScan sans cle d'API a rendu les receptions ETH et BNB
     * muettes pendant des jours, sans le moindre indice.
     */
    val message: String? = null,
    /**
     * ATTENTION : ce champ change de TYPE selon l'issue de la requete.
     *
     *  succes  → tableau de transactions
     *  refus   → simple chaine : « Missing/Invalid API Key », « Max rate limit
     *            reached », « Invalid address format »…
     *
     * Il etait declare `List<EtherscanTx>?`. Sur un refus, Gson echouait donc
     * avec « Expected BEGIN_ARRAY but was STRING » AVANT meme que le code ne
     * teste `status` — le motif du refus etait perdu, et l'echec ressemblait a
     * une panne reseau quelconque. C'est ce qui a rendu les receptions ETH et
     * BNB muettes pendant des jours.
     *
     * Declare en JsonElement : on regarde ce qu'on a recu avant de l'interpreter.
     */
    val result: com.google.gson.JsonElement? = null
) {
    /** Transactions, ou liste vide si la reponse portait un message d'erreur. */
    fun transactions(gson: com.google.gson.Gson): List<EtherscanTx> =
        if (result == null || !result.isJsonArray) emptyList()
        // Type CONCRET plutot qu'un TypeToken anonyme : R8 peut effacer
        // l'information de type generique d'une classe anonyme, ce qui fait
        // echouer Gson en release uniquement. Un tableau ne porte aucun
        // generique a preserver. Meme correction que dans NotificationCenter,
        // ou ce piege avait vide la cloche apres chaque redemarrage.
        else runCatching {
            gson.fromJson(result, Array<EtherscanTx>::class.java)?.toList() ?: emptyList()
        }.getOrDefault(emptyList())

    /** Motif du refus, quand l'API renvoie une chaine a la place des donnees. */
    fun errorText(): String? = when {
        result != null && result.isJsonPrimitive -> result.asString
        else -> message
    }
}
data class EtherscanTx(
    val hash: String,
    val from: String,
    val to: String,
    val value: String,      // wei as decimal string
    @SerializedName("timeStamp") val timeStamp: String,
    val gasUsed: String,
    val gasPrice: String,
    // Absent des réponses tokentx (un transfert de token listé a réussi).
    val isError: String = "0",
    val confirmations: String = "0",
    // Présents UNIQUEMENT dans les réponses tokentx (transferts ERC-20/BEP-20).
    @SerializedName("tokenSymbol") val tokenSymbol: String? = null,
    @SerializedName("tokenDecimal") val tokenDecimal: String? = null
)


// ─── BINANCE (source de prix de secours) ───────────────────────────
// Nombres reçus en chaînes : voir BinanceApi.getTickers pour le pourquoi.
data class BinanceTickerDto(
    val symbol: String = "",
    val lastPrice: String = "0",
    val priceChangePercent: String = "0"
)
