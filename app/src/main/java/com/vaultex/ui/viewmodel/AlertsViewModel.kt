package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.data.local.entity.PriceAlertEntity
import com.vaultex.data.remote.api.CoinGeckoApi
import com.vaultex.domain.usecase.PriceAlertUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val priceAlertUseCase: PriceAlertUseCase,
    private val coinGeckoApi: CoinGeckoApi
) : ViewModel() {

    val alerts: StateFlow<List<PriceAlertEntity>> = priceAlertUseCase.observeAlerts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Prix courants en FCFA, indexés par symbole token. */
    private val _currentPricesXof = MutableStateFlow<Map<String, Double>>(emptyMap())
    val currentPricesXof: StateFlow<Map<String, Double>> = _currentPricesXof

    init {
        refreshPrices()
    }

    fun refreshPrices() {
        viewModelScope.launch {
            val prices = withContext(Dispatchers.IO) {
                try {
                    coinGeckoApi.getPrices(
                        ids = SYMBOL_TO_COINGECKO_ID.values.joinToString(","),
                        vsCurrencies = "xof",
                        include24hChange = false,
                        includeMarketCap = false
                    )
                } catch (_: Exception) {
                    emptyMap()
                }
            }
            _currentPricesXof.value = SYMBOL_TO_COINGECKO_ID.mapNotNull { (symbol, id) ->
                prices[id]?.xof?.takeIf { it > 0 }?.let { symbol to it }
            }.toMap()
        }
    }

    fun createAlert(symbol: String, condition: String, targetPrice: String) {
        viewModelScope.launch { priceAlertUseCase.createAlert(symbol, condition, targetPrice) }
    }

    fun toggleAlert(id: String, active: Boolean) {
        viewModelScope.launch { priceAlertUseCase.toggleAlert(id, active) }
    }

    fun deleteAlert(id: String) {
        viewModelScope.launch { priceAlertUseCase.deleteAlert(id) }
    }

    companion object {
        val SYMBOL_TO_COINGECKO_ID = mapOf(
            "BTC" to "bitcoin",
            "ETH" to "ethereum",
            "BNB" to "binancecoin",
            "SOL" to "solana",
            "TRX" to "tron",
            "USDT" to "tether"
        )
    }
}
