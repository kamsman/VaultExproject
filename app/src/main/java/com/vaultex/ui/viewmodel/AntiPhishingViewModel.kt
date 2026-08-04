package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.vaultex.core.security.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** Réglage du code anti-phishing (stockage chiffré, aucune opération réseau). */
@HiltViewModel
class AntiPhishingViewModel @Inject constructor(
    private val secureStorage: SecureStorage
) : ViewModel() {

    private val _code = MutableStateFlow(secureStorage.getAntiPhishingCode())
    val code: StateFlow<String> = _code.asStateFlow()

    fun save(value: String) {
        val clean = value.trim()
        if (clean.isEmpty()) return
        secureStorage.saveAntiPhishingCode(clean)
        _code.value = clean
    }

    fun clear() {
        secureStorage.clearAntiPhishingCode()
        _code.value = ""
    }
}
