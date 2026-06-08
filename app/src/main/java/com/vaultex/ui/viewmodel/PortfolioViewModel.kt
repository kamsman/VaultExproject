package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val colorHex: String
)

data class PortfolioState(
    val totalBalanceXof: Double = 0.0,
    val totalChangePercent: Double = 0.0,
    val tokens: List<TokenBalance> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class PortfolioViewModel @Inject constructor(
    private val secureStorage: SecureStorage,
    private val coinGeckoApi: CoinGeckoApi,
    @Named("eth") private val ethRpc: EvmRpcApi,
    @Named("bnb") private val bnbRpc: EvmRpcApi,
    private val bitcoinApi: BitcoinApi,
    private val solanaRpc: SolanaRpcApi,
    private val tronApi: TronApi
) : ViewModel() {

    private val _state = MutableStateFlow(PortfolioState(isLoading = true))
    val state: StateFlow<PortfolioState> = _state.asStateFlow()

    companion object {
        private const val XOF_PER_USD = 655.0
        private val COIN_IDS = listOf("bitcoin", "ethereum", "binancecoin", "solana", "tron")
    }

    init { loadPortfolio() }

    fun loadPortfolio() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val mnemonic = secureStorage.getMnemonic() ?: run {
                    _state.update { it.copy(isLoading = false, error = "Wallet non initialisé") }
                    return@launch
                }
                val addresses = withContext(Dispatchers.IO) { WalletManager.deriveAddresses(mnemonic) }

                val prices = withContext(Dispatchers.IO) {
                    try {
                        coinGeckoApi.getPrices(
                            ids = COIN_IDS.joinToString(","),
                            vsCurrencies = "usd",
                            include24hChange = true,
                            includeMarketCap = false
                        )
                    } catch (_: Exception) { emptyMap() }
                }

                val tokens = coroutineScope {
                    val btcD = async(Dispatchers.IO) { fetchBtcBalance(addresses.btc) }
                    val ethD = async(Dispatchers.IO) { fetchEvmBalance(ethRpc, addresses.eth) }
                    val bnbD = async(Dispatchers.IO) { fetchEvmBalance(bnbRpc, addresses.bnb) }
                    val solD = async(Dispatchers.IO) { fetchSolBalance(addresses.sol) }
                    val trxD = async(Dispatchers.IO) { fetchTrxBalance(addresses.trx) }
                    val btc = btcD.await(); val eth = ethD.await()
                    val bnb = bnbD.await(); val sol = solD.await(); val trx = trxD.await()

                    fun p(id: String) = prices[id]?.usd ?: 0.0
                    fun c(id: String) = prices[id]?.change24h ?: 0.0
                    listOf(
                        TokenBalance("BTC", "Bitcoin", "%.6f BTC".format(btc), btc * p("bitcoin") * XOF_PER_USD, c("bitcoin"), "#F7931A"),
                        TokenBalance("ETH", "Ethereum", "%.6f ETH".format(eth), eth * p("ethereum") * XOF_PER_USD, c("ethereum"), "#627EEA"),
                        TokenBalance("BNB", "BNB",      "%.4f BNB".format(bnb), bnb * p("binancecoin") * XOF_PER_USD, c("binancecoin"), "#F0B90B"),
                        TokenBalance("SOL", "Solana",   "%.4f SOL".format(sol), sol * p("solana") * XOF_PER_USD, c("solana"), "#9945FF"),
                        TokenBalance("TRX", "Tron",     "%.2f TRX".format(trx), trx * p("tron") * XOF_PER_USD, c("tron"), "#FF060A"),
                    )
                }

                val total = tokens.sumOf { it.valueXof }
                val avgChange = if (tokens.isEmpty()) 0.0 else tokens.map { it.changePercent24h }.average()
                _state.update { it.copy(tokens = tokens, totalBalanceXof = total, totalChangePercent = avgChange, isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun refresh() = loadPortfolio()

    private suspend fun fetchEvmBalance(rpc: EvmRpcApi, address: String): Double = try {
        val res = rpc.rpcCall(JsonRpcRequest("eth_getBalance", mutableListOf(address as Any, "latest" as Any)))
        val hex = res.result as? String ?: "0x0"
        BigInteger(hex.removePrefix("0x").ifEmpty { "0" }, 16)
            .toBigDecimal().divide(java.math.BigDecimal("1000000000000000000")).toDouble()
    } catch (_: Exception) { 0.0 }

    private suspend fun fetchBtcBalance(address: String): Double = try {
        val info = bitcoinApi.getAddressInfo(address)
        (info.chainStats.fundedSum - info.chainStats.spentSum) / 1e8
    } catch (_: Exception) { 0.0 }

    private suspend fun fetchSolBalance(address: String): Double = try {
        val res = solanaRpc.rpcCall(JsonRpcRequest("getBalance", mutableListOf(address as Any)))
        @Suppress("UNCHECKED_CAST")
        val lamports = (res.result as? Map<String, Any>)?.get("value") as? Double ?: 0.0
        lamports / 1e9
    } catch (_: Exception) { 0.0 }

    private suspend fun fetchTrxBalance(address: String): Double = try {
        val account = tronApi.getAccount(address)
        (account.data.firstOrNull()?.balance ?: 0L) / 1_000_000.0
    } catch (_: Exception) { 0.0 }
}
