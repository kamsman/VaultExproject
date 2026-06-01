package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SendViewModel @Inject constructor(
    private val txRepo: TransactionRepository
) : ViewModel() {

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class Success(val txHash: String) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun send(chain: String, toAddress: String, amount: String) {
        val amountDouble = amount.toDoubleOrNull() ?: run {
            _state.value = UiState.Error("Montant invalide")
            return
        }
        viewModelScope.launch {
            _state.value = UiState.Loading
            try {
                val result = withContext(Dispatchers.IO) {
                    txRepo.send(chain, toAddress, amountDouble)
                }
                _state.value = UiState.Success(result.txHash)
            } catch (e: Exception) {
                _state.value = UiState.Error(e.message ?: "Erreur inconnue")
            }
        }
    }

    fun reset() { _state.value = UiState.Idle }
}
