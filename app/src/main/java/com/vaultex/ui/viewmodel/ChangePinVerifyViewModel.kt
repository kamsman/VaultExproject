package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.core.security.PinManager
import com.vaultex.core.security.PinVerificationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Porte de vérification AVANT de changer le PIN : on exige le PIN ACTUEL (ou la
 * biométrie). Sans ça, une personne ayant le téléphone déverrouillé pourrait
 * changer le PIN sans connaître l'ancien. Réutilise pinManager.verifyPin() →
 * même anti-bruteforce (verrouillage temporisé) et même PIN de panique
 * (l'effacement est déclenché par PinManager).
 */
@HiltViewModel
class ChangePinVerifyViewModel @Inject constructor(
    private val pinManager: PinManager,
    private val secureStorage: com.vaultex.core.security.SecureStorage,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
) : ViewModel() {

    data class State(
        val pin: String = "",
        val error: String? = null,
        val lockedSeconds: Long = 0L,
        val verified: Boolean = false,
        val panicTriggered: Boolean = false
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun isBiometricEnabled(): Boolean = secureStorage.isBiometricEnabled()

    /** Biométrie = preuve d'identité suffisante pour autoriser le changement. */
    fun onBiometricSuccess() = _state.update { it.copy(verified = true) }

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
            when (val r = pinManager.verifyPin(pin)) {
                is PinVerificationResult.Valid ->
                    _state.update { it.copy(verified = true) }
                is PinVerificationResult.Invalid ->
                    _state.update { it.copy(pin = "", error = appContext.getString(com.vaultex.R.string.pin_wrong_attempts, r.remainingAttempts)) }
                is PinVerificationResult.Locked ->
                    _state.update { it.copy(pin = "", lockedSeconds = r.unlockInSeconds, error = null) }
                is PinVerificationResult.PanicTriggered ->
                    _state.update { it.copy(panicTriggered = true) }
            }
        }
    }
}
