package com.vaultex.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.core.crypto.WalletManager
import com.vaultex.core.security.SecureStorage
import com.vaultex.data.remote.api.CoinGeckoApi
import com.vaultex.data.repository.MarketRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class TokenDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val secureStorage: SecureStorage,
    private val marketRepository: MarketRepository,
    private val coinGeckoApi: CoinGeckoApi
) : ViewModel() {

    data class State(
        val symbol: String = "",
        val name: String = "",
        val priceUsd: Double = 0.0,
        val change24h: Double = 0.0,
        val marketCapUsd: Double = 0.0,
        val chartPrices: List<Double> = emptyList(),
        val address: String = "",
        // Solde détenu (depuis l'instantané portefeuille).
        val amountFormatted: String = "",
        val valueUsd: Double = 0.0,
        val isLoading: Boolean = true,
        val error: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val gson = com.google.gson.Gson()

    private val symbol: String = savedStateHandle["symbol"] ?: "ETH"

    private val coinGeckoId = mapOf(
        "BTC" to "bitcoin", "ETH" to "ethereum", "BNB" to "binancecoin",
        "SOL" to "solana", "TRX" to "tron",
        "USDT" to "tether", "USDT-ETH" to "tether", "USDT-BNB" to "tether"
    )[symbol] ?: "ethereum"

    private val tokenNames = mapOf(
        "BTC" to "Bitcoin", "ETH" to "Ethereum", "BNB" to "BNB",
        "SOL" to "Solana", "TRX" to "Tron",
        "USDT" to "Tether TRC20", "USDT-ETH" to "Tether ERC20", "USDT-BNB" to "Tether BEP20"
    )

    init {
        _state.update { it.copy(symbol = symbol, name = tokenNames[symbol] ?: symbol) }
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val address = withContext(Dispatchers.IO) {
                    val mnemonic = secureStorage.getMnemonic() ?: return@withContext ""
                    val a = WalletManager.deriveAddresses(mnemonic, secureStorage.getPassphrase())
                    when (symbol) {
                        "BTC" -> a.btc
                        "ETH", "USDT-ETH" -> a.eth
                        "BNB", "USDT-BNB" -> a.bnb
                        "SOL" -> a.sol
                        "TRX", "USDT" -> a.trx
                        else -> a.eth
                    }
                }

                // Solde détenu : lu dans l'instantané portefeuille (aucun appel réseau).
                val (amountFormatted, valueUsd) = balanceFor(symbol)

                // Prix / variation / market cap / sparkline 7j : cache marché
                // (cache-first → évite le rate-limit CoinGecko qui cassait l'écran).
                val dto = withContext(Dispatchers.IO) {
                    try { marketRepository.getMarket(coinGeckoId).firstOrNull() } catch (_: Exception) { null }
                }
                // Graphique : d'abord le sparkline du dto ; sinon repli market_chart.
                val chartData = dto?.sparkline_in_7d?.price
                    ?: withContext(Dispatchers.IO) {
                        try { coinGeckoApi.getMarketChart(coinGeckoId, "usd", 7).prices.map { it[1] } }
                        catch (_: Exception) { emptyList() }
                    }

                _state.update {
                    it.copy(
                        priceUsd = dto?.currentPrice ?: 0.0,
                        change24h = dto?.change24h ?: 0.0,
                        marketCapUsd = dto?.marketCap ?: 0.0,
                        chartPrices = chartData,
                        address = address,
                        amountFormatted = amountFormatted,
                        valueUsd = valueUsd,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    /** Solde + valeur USD de [sym] depuis l'instantané portefeuille persté. */
    private fun balanceFor(sym: String): Pair<String, Double> {
        val json = secureStorage.getPortfolioSnapshot() ?: return "" to 0.0
        return try {
            val snap = gson.fromJson(json, SnapshotLite::class.java)
            val t = snap?.tokens?.firstOrNull { it.symbol == sym }
            (t?.amountFormatted ?: "") to (t?.valueUsd ?: 0.0)
        } catch (_: Exception) { "" to 0.0 }
    }

    private data class SnapshotLite(val tokens: List<TokenLite>?)
    private data class TokenLite(
        val symbol: String = "",
        val amountFormatted: String = "",
        val valueUsd: Double = 0.0
    )
}
