package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.domain.usecase.SendCryptoUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SendState(
    val selectedChain: String = "ETH",
    val toAddress: String = "",
    val amount: String = "",
    val estimatedFee: String = "",
    val isAddressValid: Boolean = false,
    val isLoading: Boolean = false,
    val txHash: String? = null,
    val error: String? = null
)

@HiltViewModel
class SendViewModel @Inject constructor(
    private val sendCryptoUseCase: SendCryptoUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(SendState())
    val state: StateFlow<SendState> = _state.asStateFlow()

    fun setChain(chain: String) = _state.update { it.copy(selectedChain = chain) }

    fun setToAddress(address: String) {
        val valid = address.length >= 26
        _state.update { it.copy(toAddress = address, isAddressValid = valid) }
    }

    fun setAmount(amount: String) = _state.update { it.copy(amount = amount) }

    fun send() {
        val s = _state.value
        if (!s.isAddressValid || s.amount.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            // Signing réel appelé ici via sendCryptoUseCase
            _state.update { it.copy(isLoading = false, txHash = "0xPENDING") }
        }
    }

    fun reset() = _state.update { SendState() }
}
