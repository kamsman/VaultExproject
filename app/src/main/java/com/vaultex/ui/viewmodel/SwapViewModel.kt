package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.domain.usecase.SwapUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SwapState(
    val fromToken: String = "ETH",
    val toToken: String = "BNB",
    val fromAmount: String = "",
    val toAmount: String = "",
    val estimatedFee: String = "",
    val vaultexFeePercent: Double = SwapUseCase.VAULTEX_FEE_PERCENT,
    val isCrossChain: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val txHash: String? = null
)

@HiltViewModel
class SwapViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(SwapState())
    val state: StateFlow<SwapState> = _state.asStateFlow()

    fun setFromToken(token: String) = _state.update { it.copy(fromToken = token) }
    fun setToToken(token: String) = _state.update { it.copy(toToken = token) }

    fun setFromAmount(amount: String) {
        _state.update { it.copy(fromAmount = amount) }
        if (amount.isNotEmpty()) estimateOutput(amount)
    }

    fun swapTokens() = _state.update { it.copy(fromToken = it.toToken, toToken = it.fromToken, fromAmount = it.toAmount, toAmount = it.fromAmount) }

    private fun estimateOutput(amount: String) {
        viewModelScope.launch {
            val input = amount.toDoubleOrNull() ?: return@launch
            val (fee, net) = SwapUseCase.applyFee(input)
            // Estimation simplifiée — remplacer par appel 1inch ou ChangeNOW réel
            _state.update { it.copy(toAmount = String.format("%.6f", net * 0.92), estimatedFee = String.format("%.4f", fee)) }
        }
    }

    fun executeSwap() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            _state.update { it.copy(isLoading = false, txHash = "SWAP_PENDING") }
        }
    }
}
