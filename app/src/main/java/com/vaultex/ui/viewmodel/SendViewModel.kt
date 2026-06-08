package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.domain.usecase.SendCryptoUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
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

    fun setChain(chain: String) = _state.update { it.copy(selectedChain = chain, error = null) }

    fun setToAddress(address: String) {
        _state.update { it.copy(toAddress = address, isAddressValid = address.length >= 26) }
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
                    val amountWei = try {
                        BigDecimal(s.amount)
                            .multiply(BigDecimal("1000000000000000000"))
                            .toBigInteger()
                    } catch (_: Exception) {
                        _state.update { it.copy(isLoading = false, error = "Montant invalide") }
                        return@launch
                    }
                    sendCryptoUseCase.sendEvm(toAddress = s.toAddress, amountWei = amountWei, chainId = chainId)
                }
                "BTC" -> {
                    val amountSatoshi = try {
                        BigDecimal(s.amount).multiply(BigDecimal("100000000")).toLong()
                    } catch (_: Exception) {
                        _state.update { it.copy(isLoading = false, error = "Montant invalide") }
                        return@launch
                    }
                    sendCryptoUseCase.sendBtc(toAddress = s.toAddress, amountSatoshi = amountSatoshi)
                }
                "TRX" -> {
                    val amountSun = try {
                        BigDecimal(s.amount).multiply(BigDecimal("1000000")).toLong()
                    } catch (_: Exception) {
                        _state.update { it.copy(isLoading = false, error = "Montant invalide") }
                        return@launch
                    }
                    sendCryptoUseCase.sendTrx(toAddress = s.toAddress, amountSun = amountSun)
                }
                "SOL" -> {
                    val lamports = try {
                        BigDecimal(s.amount).multiply(BigDecimal("1000000000")).toLong()
                    } catch (_: Exception) {
                        _state.update { it.copy(isLoading = false, error = "Montant invalide") }
                        return@launch
                    }
                    sendCryptoUseCase.sendSol(toAddress = s.toAddress, lamports = lamports)
                }
                else -> SendCryptoUseCase.Result.Error("Chain non supportée")
            }
            when (result) {
                is SendCryptoUseCase.Result.Success -> _state.update { it.copy(isLoading = false, txHash = result.txHash) }
                is SendCryptoUseCase.Result.Error   -> _state.update { it.copy(isLoading = false, error = result.message) }
            }
        }
    }

    fun reset() = _state.update { SendState() }
}
