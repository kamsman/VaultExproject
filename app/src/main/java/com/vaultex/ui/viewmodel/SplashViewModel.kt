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

    /**
     * Le seed est là mais ne se déchiffre plus.
     *
     * Distinct de hasWallet(), qui ne vérifie que la PRÉSENCE du texte
     * chiffré : sans cette seconde question, l'application ouvrait l'écran
     * de code sur un portefeuille qu'elle ne pouvait plus lire.
     */
    fun seedIllisible(): Boolean = secureStorage.seedIllisible()
}
