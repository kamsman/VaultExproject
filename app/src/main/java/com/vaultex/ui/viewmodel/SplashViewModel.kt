package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.vaultex.core.security.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val secureStorage: SecureStorage
) : ViewModel() {
    fun hasWallet(): Boolean = secureStorage.hasMnemonic()
}
