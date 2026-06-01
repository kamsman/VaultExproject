package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.core.security.KeystoreManager
import com.vaultex.core.security.PinManager
import com.vaultex.core.security.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val secureStorage: SecureStorage,
    private val pinManager: PinManager,
    private val keystoreManager: KeystoreManager
) : ViewModel() {

    data class SettingsState(
        val biometricEnabled: Boolean = false,
        val autoLockMinutes: Int = 5,
        val hasPanicPin: Boolean = false,
        val actionResult: String? = null
    )

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        _state.value = _state.value.copy(
            biometricEnabled = secureStorage.isBiometricEnabled(),
            autoLockMinutes = secureStorage.getAutoLockMinutes(),
            hasPanicPin = secureStorage.getPanicPinHash() != null
        )
    }

    fun setBiometricEnabled(enabled: Boolean) {
        secureStorage.setBiometricEnabled(enabled)
        _state.value = _state.value.copy(biometricEnabled = enabled)
    }

    fun setAutoLockMinutes(minutes: Int) {
        secureStorage.setAutoLockMinutes(minutes)
        _state.value = _state.value.copy(autoLockMinutes = minutes)
    }

    fun setPanicPin(pin: String): Boolean {
        if (pin.length != 6 || !pin.all { it.isDigit() }) return false
        pinManager.setPin(pin, isPanic = true)
        _state.value = _state.value.copy(hasPanicPin = true, actionResult = "PIN de panique configuré")
        clearActionResult()
        return true
    }

    fun resetWallet() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                secureStorage.nukeAllData()
            }
            _state.value = _state.value.copy(actionResult = "Wallet réinitialisé")
        }
    }

    fun clearActionResult() {
        viewModelScope.launch {
            kotlinx.coroutines.delay(2500)
            _state.value = _state.value.copy(actionResult = null)
        }
    }
}
