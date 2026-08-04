package com.vaultex.core.session

import com.vaultex.core.security.SecureStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Nom d'affichage du wallet, partagé et persisté. Modifiable uniquement
 * depuis les Paramètres ; le Dashboard l'affiche (réactif via ce singleton).
 */
@Singleton
class WalletNameController @Inject constructor(
    private val secureStorage: SecureStorage
) {
    private val _name = MutableStateFlow(secureStorage.getWalletName())
    val name: StateFlow<String> = _name.asStateFlow()

    fun setName(name: String) {
        secureStorage.saveWalletName(name)
        _name.value = name.trim()
    }
}
