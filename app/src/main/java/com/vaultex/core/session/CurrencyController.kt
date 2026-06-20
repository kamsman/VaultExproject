package com.vaultex.core.session

import com.vaultex.core.security.SecureStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Devise d'affichage du wallet (USD / EUR / XOF), partagée et persistée.
 * Singleton pour que le choix soit cohérent entre tous les écrans.
 */
@Singleton
class CurrencyController @Inject constructor(
    private val secureStorage: SecureStorage
) {
    private val _currency = MutableStateFlow(secureStorage.getCurrency())
    val currency: StateFlow<String> = _currency.asStateFlow()

    fun setCurrency(code: String) {
        secureStorage.setCurrency(code)
        _currency.value = code
    }

    companion object {
        val SUPPORTED = listOf("USD", "EUR", "XOF")
    }
}
