package com.vaultex.core.session

import com.vaultex.core.security.SecureStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * État partagé d'affichage/masquage du solde (Show / Hide Balance).
 * Singleton pour que le réglage soit cohérent entre Dashboard, Home et
 * Portfolio, persisté dans SecureStorage.
 */
@Singleton
class BalanceVisibilityController @Inject constructor(
    private val secureStorage: SecureStorage
) {
    private val _hidden = MutableStateFlow(secureStorage.isBalanceHidden())
    val hidden: StateFlow<Boolean> = _hidden.asStateFlow()

    fun toggle() {
        val next = !_hidden.value
        secureStorage.setBalanceHidden(next)
        _hidden.value = next
    }
}
