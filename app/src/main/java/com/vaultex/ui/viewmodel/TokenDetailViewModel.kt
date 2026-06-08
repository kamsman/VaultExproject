package com.vaultex.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.core.crypto.WalletManager
import com.vaultex.core.security.SecureStorage
import com.vaultex.data.remote.api.CoinGeckoApi
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
        val isLoading: Boolean = true,
        val error: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val symbol: String = savedStateHandle["symbol"] ?: "ETH"

    private val coinGeckoId = mapOf(
        "BTC" to "bitcoin", "ETH" to "ethereum", "BNB" to "binancecoin",
        "SOL" to "solana", "TRX" to "tron"
    )[symbol] ?: "ethereum"

    private val tokenNames = mapOf(
        "BTC" to "Bitcoin", "ETH" to "Ethereum", "BNB" to "BNB",
        "SOL" to "Solana", "TRX" to "Tron"
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
                    val a = WalletManager.deriveAddresses(mnemonic)
                    when (symbol) {
                        "BTC" -> a.btc; "ETH" -> a.eth; "BNB" -> a.bnb
                        "SOL" -> a.sol; "TRX" -> a.trx; else -> a.eth
                    }
                }

                val prices = withContext(Dispatchers.IO) {
                    try {
                        coinGeckoApi.getPrices(
                            ids = coinGeckoId,
                            vsCurrencies = "usd",
                            include24hChange = true,
                            includeMarketCap = true
                        )
                    } catch (_: Exception) { emptyMap() }
                }

                val chartData = withContext(Dispatchers.IO) {
                    try {
                        coinGeckoApi.getMarketChart(coinGeckoId, "usd", 7).prices.map { it[1] }
                    } catch (_: Exception) { emptyList() }
                }

                val dto = prices[coinGeckoId]
                _state.update {
                    it.copy(
                        priceUsd = dto?.usd ?: 0.0,
                        change24h = dto?.change24h ?: 0.0,
                        marketCapUsd = dto?.marketCap ?: 0.0,
                        chartPrices = chartData,
                        address = address,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
