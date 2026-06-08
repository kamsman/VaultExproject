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
            val result = when (s.selectedChain) {
                "ETH", "BNB" -> {
                    val chainId = if (s.selectedChain == "ETH") 1L else 56L
                    val coinType = 60
                    val amountWei = try {
                        java.math.BigDecimal(s.amount)
                            .multiply(java.math.BigDecimal("1000000000000000000"))
                            .toBigInteger()
                    } catch (e: Exception) {
                        _state.update { it.copy(isLoading = false, error = "Montant invalide") }
                        return@launch
                    }
                    sendCryptoUseCase.sendEvm(
                        toAddress = s.toAddress,
                        amountWei = amountWei,
                        gasPrice = java.math.BigInteger.valueOf(20_000_000_000L),
                        gasLimit = java.math.BigInteger.valueOf(21_000L),
                        nonce = java.math.BigInteger.ZERO,
                        chainId = chainId,
                        coinType = coinType
                    )
                }
                else -> SendCryptoUseCase.Result.Error("Chain non encore supportée dans l'UI")
            }
            when (result) {
                is SendCryptoUseCase.Result.Success -> _state.update { it.copy(isLoading = false, txHash = result.signedTxHex) }
                is SendCryptoUseCase.Result.Error -> _state.update { it.copy(isLoading = false, error = result.message) }
            }
        }
    }

    fun reset() = _state.update { SendState() }
}
