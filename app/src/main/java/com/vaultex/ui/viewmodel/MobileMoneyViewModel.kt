package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.data.remote.api.FlutterwaveApi
import com.vaultex.data.remote.dto.FlutterwaveChargeBody
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class MobileMoneyState(
    val selectedNetwork: String = "",
    val phoneNumber: String = "",
    val amountFcfa: String = "",
    val isLoading: Boolean = false,
    val txRef: String? = null,
    val flwRef: String? = null,
    val error: String? = null,
    val success: Boolean = false
)

@HiltViewModel
class MobileMoneyViewModel @Inject constructor(
    private val flutterwaveApi: FlutterwaveApi
) : ViewModel() {

    private val _state = MutableStateFlow(MobileMoneyState())
    val state: StateFlow<MobileMoneyState> = _state.asStateFlow()

    fun setNetwork(network: String) = _state.update { it.copy(selectedNetwork = network, error = null) }
    fun setPhone(phone: String) = _state.update { it.copy(phoneNumber = phone, error = null) }
    fun setAmount(amount: String) = _state.update { it.copy(amountFcfa = amount, error = null) }
    fun resetResult() = _state.update { it.copy(success = false, error = null, txRef = null, flwRef = null) }

    val fee: Double get() = (_state.value.amountFcfa.toDoubleOrNull() ?: 0.0) * 0.01
    val amountAfterFee: Double get() = (_state.value.amountFcfa.toDoubleOrNull() ?: 0.0) - fee

    fun initiateTransfer() {
        val s = _state.value
        if (s.isLoading) return
        val amount = s.amountFcfa.toDoubleOrNull()
        if (amount == null || amount < 100) {
            _state.update { it.copy(error = "Montant minimum : 100 FCFA") }
            return
        }
        if (s.selectedNetwork.isEmpty()) {
            _state.update { it.copy(error = "Sélectionnez un opérateur") }
            return
        }
        if (s.phoneNumber.length < 8) {
            _state.update { it.copy(error = "Numéro de téléphone invalide") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val txRef = "VAULTEX-${UUID.randomUUID().toString().take(8).uppercase()}"
                val body = FlutterwaveChargeBody(
                    phone_number = s.phoneNumber.filter { it.isDigit() || it == '+' },
                    amount = amount.toLong().toString(),
                    network = s.selectedNetwork,
                    tx_ref = txRef
                )
                val response = flutterwaveApi.charge(body)
                if (response.status == "success") {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            success = true,
                            txRef = response.data?.txRef,
                            flwRef = response.data?.flwRef
                        )
                    }
                } else {
                    _state.update { it.copy(isLoading = false, error = response.message) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Erreur de connexion") }
            }
        }
    }
}
