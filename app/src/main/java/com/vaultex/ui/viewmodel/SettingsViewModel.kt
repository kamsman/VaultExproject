package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.core.security.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val isBiometricEnabled: Boolean = false,
    val autoLockMinutes: Int = 5,
    val selectedCurrency: String = "XOF",
    val selectedLanguage: String = "fr",
    val hasPanicPin: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val secureStorage: SecureStorage
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        _state.update {
            it.copy(
                isBiometricEnabled = secureStorage.isBiometricEnabled(),
                autoLockMinutes = secureStorage.getAutoLockMinutes(),
                hasPanicPin = secureStorage.getPanicPinHash() != null
            )
        }
    }

    fun setBiometric(enabled: Boolean) {
        secureStorage.setBiometricEnabled(enabled)
        _state.update { it.copy(isBiometricEnabled = enabled) }
    }

    fun setAutoLock(minutes: Int) {
        secureStorage.setAutoLockMinutes(minutes)
        _state.update { it.copy(autoLockMinutes = minutes) }
    }

    fun setCurrency(currency: String) = _state.update { it.copy(selectedCurrency = currency) }

    fun setLanguage(language: String) = _state.update { it.copy(selectedLanguage = language) }
}
