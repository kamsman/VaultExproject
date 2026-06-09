package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.core.security.PinManager
import com.vaultex.core.security.PinVerificationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UnlockState(
    val pin: String = "",
    val error: String? = null,
    val lockedSeconds: Long = 0L,
    val isUnlocked: Boolean = false,
    val panicTriggered: Boolean = false
)

@HiltViewModel
class UnlockViewModel @Inject constructor(
    private val pinManager: PinManager
) : ViewModel() {

    private val _state = MutableStateFlow(UnlockState())
    val state: StateFlow<UnlockState> = _state.asStateFlow()

    fun onDigit(d: String) {
        val s = _state.value
        if (s.lockedSeconds > 0 || s.pin.length >= 6) return
        val newPin = s.pin + d
        _state.update { it.copy(pin = newPin, error = null) }
        if (newPin.length == 6) verify(newPin)
    }

    fun onBackspace() {
        val s = _state.value
        if (s.pin.isNotEmpty()) _state.update { it.copy(pin = s.pin.dropLast(1), error = null) }
    }

    private fun verify(pin: String) {
        viewModelScope.launch {
            when (val result = pinManager.verifyPin(pin)) {
                is PinVerificationResult.Valid ->
                    _state.update { it.copy(isUnlocked = true) }
                is PinVerificationResult.Invalid ->
                    _state.update { it.copy(pin = "", error = "PIN incorrect — ${result.remainingAttempts} tentative(s) restante(s)") }
                is PinVerificationResult.Locked ->
                    _state.update { it.copy(pin = "", lockedSeconds = result.unlockInSeconds, error = null) }
                is PinVerificationResult.PanicTriggered ->
                    _state.update { it.copy(panicTriggered = true) }
            }
        }
    }
}
