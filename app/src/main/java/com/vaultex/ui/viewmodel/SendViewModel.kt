package com.vaultex.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.core.session.NetworkMonitor
import com.vaultex.core.validation.AddressValidator
import com.vaultex.data.local.dao.PendingSendDao
import com.vaultex.data.local.entity.PendingSendEntity
import com.vaultex.domain.usecase.SendCryptoUseCase
import com.vaultex.service.PendingSendWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SendState(
    val selectedChain: String = "USDT",
    val toAddress: String = "",
    val amount: String = "",
    val estimatedFee: String = "",
    val isAddressValid: Boolean = false,
    val isLoading: Boolean = false,
    val txHash: String? = null,
    val error: String? = null,
    val queued: Boolean = false,
    val dustWarning: String? = null
)

@HiltViewModel
class SendViewModel @Inject constructor(
    private val sendCryptoUseCase: SendCryptoUseCase,
    private val pendingSendDao: PendingSendDao,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(SendState())
    val state: StateFlow<SendState> = _state.asStateFlow()

    fun setChain(chain: String) {
        val addr = _state.value.toAddress
        val valid = if (addr.isEmpty()) false else AddressValidator.isValid(addr, chain)
        val warning = dustWarning(chain, _state.value.amount)
        _state.update { it.copy(selectedChain = chain, isAddressValid = valid, error = null, dustWarning = warning) }
    }

    fun setToAddress(address: String) {
        val chain = _state.value.selectedChain
        val valid = AddressValidator.isValid(address, chain)
        _state.update { it.copy(toAddress = address, isAddressValid = valid) }
    }

    fun setAmount(amount: String) {
        val warning = dustWarning(_state.value.selectedChain, amount)
        _state.update { it.copy(amount = amount, dustWarning = warning) }
    }

    private fun dustWarning(chain: String, amount: String): String? {
        val value = amount.replace(",", ".").toDoubleOrNull() ?: return null
        val minimum = MINIMUM_AMOUNTS[chain] ?: return null
        return if (value < minimum) "$minimum $chain" else null
    }

    companion object {
        private val MINIMUM_AMOUNTS = mapOf(
            "BTC"      to 0.00000546,
            "ETH"      to 0.0001,
            "BNB"      to 0.0001,
            "SOL"      to 0.000001,
            "TRX"      to 0.000001,
            "USDT"     to 1.0,
            "USDT-ETH" to 1.0,
            "USDT-BNB" to 1.0
        )
    }

    fun send() {
        val s = _state.value
        if (s.isLoading) return
        if (!s.isAddressValid || s.amount.isEmpty()) return

        // Hors-ligne : on met l'INTENTION en file. Elle sera signée (avec un
        // nonce/blockhash frais) et diffusée automatiquement, une seule fois,
        // au retour du réseau via PendingSendWorker.
        if (!NetworkMonitor.isOnline(appContext)) {
            viewModelScope.launch {
                try {
                    pendingSendDao.insert(
                        PendingSendEntity(
                            chain = s.selectedChain,
                            toAddress = s.toAddress,
                            amount = s.amount,
                            status = PendingSendWorker.STATUS_PENDING,
                            txHash = null,
                            lastError = null,
                            attempts = 0,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    PendingSendWorker.enqueue(appContext)
                    _state.update { it.copy(queued = true, error = null) }
                } catch (e: Exception) {
                    _state.update { it.copy(error = e.message ?: "Erreur de mise en file") }
                }
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = sendCryptoUseCase.sendByChain(s.selectedChain, s.toAddress, s.amount)
            when (result) {
                is SendCryptoUseCase.Result.Success -> _state.update { it.copy(isLoading = false, txHash = result.txHash) }
                is SendCryptoUseCase.Result.Error   -> _state.update { it.copy(isLoading = false, error = result.message) }
            }
        }
    }

    fun reset() = _state.update { SendState() }
}
