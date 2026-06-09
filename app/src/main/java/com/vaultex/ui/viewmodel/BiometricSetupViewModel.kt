package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.vaultex.core.security.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class BiometricSetupViewModel @Inject constructor(
    private val secureStorage: SecureStorage
) : ViewModel() {
    fun enableBiometric() = secureStorage.setBiometricEnabled(true)
    fun isBiometricEnabled(): Boolean = secureStorage.isBiometricEnabled()
}
