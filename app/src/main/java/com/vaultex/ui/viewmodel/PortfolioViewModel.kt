package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.core.crypto.Blockchain
import com.vaultex.core.crypto.WalletManager
import com.vaultex.core.security.SecureStorage
import com.vaultex.data.remote.api.BitcoinApi
import com.vaultex.data.remote.api.CoinGeckoApi
import com.vaultex.data.remote.api.EvmRpcApi
import com.vaultex.data.remote.api.SolanaRpcApi
import com.vaultex.data.remote.api.TronApi
import com.vaultex.data.remote.dto.JsonRpcRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Named

data class TokenBalance(
    val symbol: String,
    val name: String,
    val amountFormatted: String,
    val valueXof: Double,
    val changePercent24h: Double,
    val colorHex: String,
    val blockchain: Blockchain,
    val valueUsd: Double = 0.0
)

data class PortfolioState(
    val totalBalanceXof: Double = 0.0,
    val totalBalanceUsd: Double = 0.0,
    val totalChangePercent: Double = 0.0,
    val tokens: List<TokenBalance> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastUpdated: Long = 0L,   // epoch ms de la dernière synchro réussie (#5)
    val isFromCache: Boolean = false,
    // Vrai si au moins un solde n'a pas pu être lu (réseau/RPC). Permet de ne
    // JAMAIS confondre « lecture échouée » avec « solde nul » (fonds reçus mais
    // invisibles parce que le nœud public a rejeté l'appel).
    val balancesUnavailable: Boolean = false
)

@HiltViewModel
class PortfolioViewModel @Inject constructor(
    private val secureStorage: SecureStorage,
    private val coinGeckoApi: CoinGeckoApi,
    @Named("eth") private val ethRpc: EvmRpcApi,
    @Named("bnb") private val bnbRpc: EvmRpcApi,
    private val bitcoinApi: BitcoinApi,
    private val solanaRpc: SolanaRpcApi,
    private val tronApi: TronApi,
    private val balanceVisibility: com.vaultex.core.session.BalanceVisibilityController
) : ViewModel() {

    private val _state = MutableStateFlow(PortfolioState(isLoading = true))
    val state: StateFlow<PortfolioState> = _state.asStateFlow()

    /** État partagé Show / Hide Balance. */
    val balanceHidden: StateFlow<Boolean> = balanceVisibility.hidden
    fun toggleBalanceVisibility() = balanceVisibility.toggle()

    companion object {
        private val COIN_IDS = listOf("bitcoin", "ethereum", "binancecoin", "solana", "tron", "tether")
        private const val USDT_TRC20_CONTRACT = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"
        private const val USDT_ETH_CONTRACT  = "0xdAC17F958D2ee523a2206206994597C13D831ec7"
        private const val USDT_BNB_CONTRACT  = "0x55d398326f99059fF775485246999027B3197955"
    }

    private val gson = com.google.gson.Gson()

    private data class Snapshot(
        val totalBalanceXof: Double,
        val totalBalanceUsd: Double,
        val totalChangePercent: Double,
        val tokens: List<TokenBalance>,
        val lastUpdated: Long
    )

    init {
        loadCachedSnapshot()
        loadPortfolio()
    }

    /** Affiche immédiatement le dernier portefeuille connu (offline-first). */
    private fun loadCachedSnapshot() {
        val json = secureStorage.getPortfolioSnapshot() ?: return
        try {
            val snap = gson.fromJson(json, Snapshot::class.java) ?: return
            _state.update {
                it.copy(
                    totalBalanceXof = snap.totalBalanceXof,
                    totalBalanceUsd = snap.totalBalanceUsd,
                    totalChangePercent = snap.totalChangePercent,
                    tokens = snap.tokens,
                    lastUpdated = snap.lastUpdated,
                    isFromCache = true
                )
            }
        } catch (_: Exception) { /* cache illisible : ignoré */ }
    }

    private fun persistSnapshot(state: PortfolioState) {
        try {
            secureStorage.savePortfolioSnapshot(
                gson.toJson(
                    Snapshot(
                        state.totalBalanceXof, state.totalBalanceUsd,
                        state.totalChangePercent, state.tokens, state.lastUpdated
                    )
                )
            )
        } catch (_: Exception) { }
    }

    fun loadPortfolio() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val mnemonic = secureStorage.getMnemonic() ?: run {
                    _state.update { it.copy(isLoading = false, error = "Wallet non initialisé") }
                    return@launch
                }
                val addresses = withContext(Dispatchers.IO) { WalletManager.deriveAddresses(mnemonic, secureStorage.getPassphrase()) }

                val prices = withContext(Dispatchers.IO) {
                    try {
                        coinGeckoApi.getPrices(
                            ids = COIN_IDS.joinToString(","),
                            vsCurrencies = "xof,usd",
                            include24hChange = true,
                            includeMarketCap = false
                        )
                    } catch (_: Exception) { emptyMap() }
                }

                val tokens = coroutineScope {
                    val btcD     = async(Dispatchers.IO) { fetchBtcBalance(addresses.btc) }
                    val ethD     = async(Dispatchers.IO) { fetchEvmBalance(ethRpc, addresses.eth) }
                    val bnbD     = async(Dispatchers.IO) { fetchEvmBalance(bnbRpc, addresses.bnb) }
                    val solD     = async(Dispatchers.IO) { fetchSolBalance(addresses.sol) }
                    val trxD     = async(Dispatchers.IO) { fetchTrxBalance(addresses.trx) }
                    val usdtTrcD = async(Dispatchers.IO) { fetchUsdtTrc20Balance(addresses.trx) }
                    val usdtEthD = async(Dispatchers.IO) { fetchErc20Balance(ethRpc, USDT_ETH_CONTRACT, addresses.eth, 6) }
                    val usdtBnbD = async(Dispatchers.IO) { fetchErc20Balance(bnbRpc, USDT_BNB_CONTRACT, addresses.bnb, 18) }
                    val btc = btcD.await(); val eth = ethD.await()
                    val bnb = bnbD.await(); val sol = solD.await()
                    val trx = trxD.await(); val usdtTrc = usdtTrcD.await()
                    val usdtEth = usdtEthD.await(); val usdtBnb = usdtBnbD.await()

                    fun xof(id: String) = prices[id]?.xof ?: 0.0
                    fun usd(id: String) = prices[id]?.usd ?: 0.0
                    fun c(id: String) = prices[id]?.change24h ?: 0.0
                    // "—" si la lecture a échoué (null) ; sinon le montant formaté.
                    fun amt(bal: Double?, decimals: Int, unit: String) =
                        if (bal == null) "—" else "%.${decimals}f $unit".format(bal)
                    // 0 si lecture échouée pour ne pas fausser à la hausse le total.
                    fun value(bal: Double?, price: Double) = (bal ?: 0.0) * price
                    listOf(
                        TokenBalance("BTC",      "Bitcoin",     amt(btc, 6, "BTC"),      value(btc, xof("bitcoin")),     c("bitcoin"),     "#F7931A", Blockchain.BITCOIN,  valueUsd = value(btc, usd("bitcoin"))),
                        TokenBalance("ETH",      "Ethereum",    amt(eth, 6, "ETH"),      value(eth, xof("ethereum")),    c("ethereum"),    "#627EEA", Blockchain.ETHEREUM, valueUsd = value(eth, usd("ethereum"))),
                        TokenBalance("BNB",      "BNB",         amt(bnb, 4, "BNB"),      value(bnb, xof("binancecoin")), c("binancecoin"), "#F0B90B", Blockchain.BNB_CHAIN, valueUsd = value(bnb, usd("binancecoin"))),
                        TokenBalance("SOL",      "Solana",      amt(sol, 4, "SOL"),      value(sol, xof("solana")),      c("solana"),      "#9945FF", Blockchain.SOLANA,   valueUsd = value(sol, usd("solana"))),
                        TokenBalance("TRX",      "Tron",        amt(trx, 2, "TRX"),      value(trx, xof("tron")),        c("tron"),        "#FF060A", Blockchain.TRON,     valueUsd = value(trx, usd("tron"))),
                        TokenBalance("USDT",     "Tether TRC20", amt(usdtTrc, 2, "USDT"), value(usdtTrc, xof("tether")),  c("tether"),      "#26A17B", Blockchain.TRON,     valueUsd = value(usdtTrc, usd("tether"))),
                        TokenBalance("USDT-ETH", "Tether ERC20", amt(usdtEth, 2, "USDT"), value(usdtEth, xof("tether")),  c("tether"),      "#26A17B", Blockchain.ETHEREUM, valueUsd = value(usdtEth, usd("tether"))),
                        TokenBalance("USDT-BNB", "Tether BEP20", amt(usdtBnb, 2, "USDT"), value(usdtBnb, xof("tether")),  c("tether"),      "#26A17B", Blockchain.BNB_CHAIN, valueUsd = value(usdtBnb, usd("tether"))),
                    )
                }

                // "—" comme montant ⇒ au moins une lecture de solde a échoué.
                val balancesUnavailable = tokens.any { it.amountFormatted.startsWith("—") }
                val total = tokens.sumOf { it.valueXof }
                val totalUsd = tokens.sumOf { it.valueUsd }
                // Value-weighted 24h change: each token weighted by its XOF value
                val avgChange = if (total == 0.0) 0.0
                    else tokens.sumOf { it.changePercent24h * it.valueXof } / total
                val newState = _state.value.copy(
                    tokens = tokens,
                    totalBalanceXof = total,
                    totalBalanceUsd = totalUsd,
                    totalChangePercent = avgChange,
                    isLoading = false,
                    error = if (balancesUnavailable)
                        "Certains soldes n'ont pas pu être lus (réseau/RPC). Les fonds reçus sont en sécurité on-chain ; réessaie."
                    else null,
                    lastUpdated = System.currentTimeMillis(),
                    isFromCache = false,
                    balancesUnavailable = balancesUnavailable
                )
                _state.value = newState
                // On NE persiste PAS un instantané partiel : on garde le dernier
                // cache complet plutôt que d'écraser avec des soldes incomplets.
                if (!balancesUnavailable) persistSnapshot(newState)
            } catch (e: Exception) {
                // Offline-first : on conserve les données en cache, on signale juste l'erreur.
                _state.update { it.copy(isLoading = false, error = e.message, isFromCache = it.lastUpdated > 0L) }
            }
        }
    }

    fun refresh() = loadPortfolio()

    // Les fetch renvoient Double? : null = ÉCHEC de lecture (réseau/RPC),
    // une valeur (y compris 0.0) = solde réellement déterminé. Si la réponse
    // RPC contient une erreur explicite, on considère aussi que c'est un échec.
    private suspend fun fetchErc20Balance(rpc: EvmRpcApi, contract: String, address: String, decimals: Int): Double? = try {
        val paddedAddr = address.removePrefix("0x").padStart(64, '0')
        val data = "0x70a08231$paddedAddr"
        val res = rpc.rpcCall(JsonRpcRequest("eth_call",
            mutableListOf(mapOf("to" to contract, "data" to data) as Any, "latest" as Any)))
        val hex = res.result as? String
        if (res.error != null || hex == null) null
        else BigInteger(hex.removePrefix("0x").ifEmpty { "0" }, 16)
            .toBigDecimal().divide(java.math.BigDecimal.TEN.pow(decimals)).toDouble()
    } catch (_: Exception) { null }

    private suspend fun fetchEvmBalance(rpc: EvmRpcApi, address: String): Double? = try {
        val res = rpc.rpcCall(JsonRpcRequest("eth_getBalance", mutableListOf(address as Any, "latest" as Any)))
        val hex = res.result as? String
        if (res.error != null || hex == null) null
        else BigInteger(hex.removePrefix("0x").ifEmpty { "0" }, 16)
            .toBigDecimal().divide(java.math.BigDecimal("1000000000000000000")).toDouble()
    } catch (_: Exception) { null }

    private suspend fun fetchBtcBalance(address: String): Double? = try {
        val info = bitcoinApi.getAddressInfo(address)
        (info.chainStats.fundedSum - info.chainStats.spentSum) / 1e8
    } catch (_: Exception) { null }

    private suspend fun fetchSolBalance(address: String): Double? = try {
        val res = solanaRpc.rpcCall(JsonRpcRequest("getBalance", mutableListOf(address as Any)))
        if (res.error != null) null
        else {
            @Suppress("UNCHECKED_CAST")
            val lamports = (res.result as? Map<String, Any>)?.get("value") as? Double ?: 0.0
            lamports / 1e9
        }
    } catch (_: Exception) { null }

    private suspend fun fetchTrxBalance(address: String): Double? = try {
        val account = tronApi.getAccount(address)
        (account.data.firstOrNull()?.balance ?: 0L) / 1_000_000.0
    } catch (_: Exception) { null }

    private suspend fun fetchUsdtTrc20Balance(address: String): Double? {
        return try {
            val account = tronApi.getAccount(address)
            val trc20List = account.data.firstOrNull()?.trc20 ?: return 0.0
            val rawBalance = trc20List
                .firstOrNull { it.containsKey(USDT_TRC20_CONTRACT) }
                ?.get(USDT_TRC20_CONTRACT) ?: return 0.0
            rawBalance.toLongOrNull()?.let { it / 1_000_000.0 } ?: 0.0
        } catch (_: Exception) { null }
    }
}
