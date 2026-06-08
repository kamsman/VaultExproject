package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.data.repository.PriceRepository
import com.vaultex.data.local.dao.WalletDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    private val walletDao: WalletDao,
    private val priceRepository: PriceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PortfolioState(isLoading = true))
    val state: StateFlow<PortfolioState> = _state.asStateFlow()

    init {
        loadPortfolio()
    }

    fun loadPortfolio() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                // Placeholder balances — remplacer par vrais appels RPC
                val tokens = listOf(
                    TokenBalance("BTC", "Bitcoin", "0.0124 BTC", 850_200.0, 1.8, "#F7931A"),
                    TokenBalance("ETH", "Ethereum", "0.45 ETH", 1_245_000.0, 3.2, "#627EEA"),
                    TokenBalance("BNB", "BNB", "2.1 BNB", 450_000.0, -0.5, "#F0B90B"),
                    TokenBalance("TRX", "Tron", "1250 TRX", 189_750.0, 5.1, "#FF060A"),
                    TokenBalance("SOL", "Solana", "1.8 SOL", 112_400.0, 7.3, "#9945FF"),
                )
                val total = tokens.sumOf { it.valueXof }
                val avgChange = tokens.map { it.changePercent24h }.average()
                _state.update { it.copy(tokens = tokens, totalBalanceXof = total, totalChangePercent = avgChange, isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun refresh() = loadPortfolio()
}
