package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.data.remote.dto.CoinGeckoMarketDto
import com.vaultex.data.repository.MarketRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MarketViewModel @Inject constructor(
    private val repository: MarketRepository
) : ViewModel() {

    sealed class UiState {
        object Loading : UiState()
        data class Success(val coins: List<CoinGeckoMarketDto>) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private var allCoins: List<CoinGeckoMarketDto> = emptyList()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            try {
                val coins = withContext(Dispatchers.IO) { repository.getMarkets() }
                allCoins = coins
                applyFilter()
            } catch (e: Exception) {
                _state.value = UiState.Error(e.message ?: "Erreur de chargement")
            }
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
        applyFilter()
    }

    private fun applyFilter() {
        val q = _searchQuery.value.trim()
        val filtered = if (q.isBlank()) allCoins
        else allCoins.filter {
            it.name.contains(q, ignoreCase = true) || it.symbol.contains(q, ignoreCase = true)
        }
        _state.value = UiState.Success(filtered)
    }
}
