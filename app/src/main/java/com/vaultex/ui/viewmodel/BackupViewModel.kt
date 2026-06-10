package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.core.security.PinManager
import com.vaultex.core.security.PinVerificationResult
import com.vaultex.core.security.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class BackupState(
    val mnemonic: String? = null,
    val isRevealed: Boolean = false,
    val showPinDialog: Boolean = false,
    val pinInput: String = "",
    val pinError: String? = null
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val secureStorage: SecureStorage,
    private val pinManager: PinManager
) : ViewModel() {

    private val _state = MutableStateFlow(BackupState())
    val state: StateFlow<BackupState> = _state.asStateFlow()

    fun requestReveal() {
        _state.update { it.copy(showPinDialog = true, pinInput = "", pinError = null) }
    }

    fun setPinInput(v: String) {
        if (v.length <= 6 && v.all { it.isDigit() }) {
            _state.update { it.copy(pinInput = v, pinError = null) }
            if (v.length == 6) verifyPin(v)
        }
    }

    private fun verifyPin(pin: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) { pinManager.verifyPin(pin) }
            when (result) {
                is PinVerificationResult.Valid -> {
                    val mnemonic = secureStorage.getMnemonic()
                    _state.update { it.copy(showPinDialog = false, isRevealed = true, mnemonic = mnemonic, pinInput = "") }
                }
                is PinVerificationResult.Invalid ->
                    _state.update { it.copy(pinError = "PIN incorrect — ${result.remainingAttempts} essai(s) restant(s)", pinInput = "") }
                is PinVerificationResult.Locked ->
                    _state.update { it.copy(pinError = "Bloqué ${result.unlockInSeconds}s", pinInput = "") }
                is PinVerificationResult.PanicTriggered ->
                    _state.update { it.copy(pinError = "PIN de panique — données effacées", pinInput = "") }
            }
        }
    }

    fun dismissPinDialog() = _state.update { it.copy(showPinDialog = false, pinInput = "", pinError = null) }

    fun hide() = _state.update { it.copy(mnemonic = null, isRevealed = false) }
}
