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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class PinUnlockViewModel @Inject constructor(
    private val pinManager: PinManager,
    private val secureStorage: SecureStorage
) : ViewModel() {

    data class State(
        val isLoading: Boolean = false,
        val result: PinVerificationResult? = null,
        val error: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    val isBiometricEnabled: Boolean get() = secureStorage.isBiometricEnabled()

    fun verify(pin: String) {
        viewModelScope.launch {
            _state.value = State(isLoading = true)
            val result = withContext(Dispatchers.IO) { pinManager.verifyPin(pin) }
            _state.value = State(isLoading = false, result = result)
        }
    }
}
