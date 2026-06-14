package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.data.remote.dto.CoinGeckoMarketDto
import com.vaultex.data.repository.MarketRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MarketViewModel @Inject constructor(
    private val repository: MarketRepository
) : ViewModel() {

    private val _markets = MutableStateFlow<List<CoinGeckoMarketDto>>(emptyList())
    val markets: StateFlow<List<CoinGeckoMarketDto>> = _markets

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // ─── Courbe de prix (détail token) ─────────────────────────
    private val _chart = MutableStateFlow<List<Float>>(emptyList())
    val chart: StateFlow<List<Float>> = _chart

    private val _chartLoading = MutableStateFlow(false)
    val chartLoading: StateFlow<Boolean> = _chartLoading

    fun loadMarkets() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _markets.value = withContext(Dispatchers.IO) { repository.getMarkets() }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Charge la courbe de prix d'un token pour une période donnée. */
    fun loadChart(coinId: String, days: Int) {
        viewModelScope.launch {
            _chartLoading.value = true
            try {
                val dto = withContext(Dispatchers.IO) { repository.getMarketChart(coinId, days) }
                _chart.value = dto.prices.mapNotNull { it.getOrNull(1)?.toFloat() }
            } catch (e: Exception) {
                _chart.value = emptyList()
            } finally {
                _chartLoading.value = false
            }
        }
    }
}
