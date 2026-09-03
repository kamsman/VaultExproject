package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.vaultex.core.security.SecureStorage
import com.vaultex.ui.theme.ThemeController
import com.vaultex.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class SettingsState(
    val isBiometricEnabled: Boolean = false,
    val autoLockMinutes: Int = 5,
    val selectedCurrency: String = "XOF",
    val selectedLanguage: String = "fr",
    val walletName: String = "",
    val hasPanicPin: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val secureStorage: SecureStorage,
    private val themeController: ThemeController,
    private val currencyController: com.vaultex.core.session.CurrencyController,
    private val walletNameController: com.vaultex.core.session.WalletNameController,
    private val sessionLock: com.vaultex.core.session.SessionLockManager
) : ViewModel() {

    /**
     * Verrouille la session à la demande — l'écran de code réapparaît.
     *
     * Le verrouillage n'existait qu'AUTOMATIQUEMENT, au retour d'arrière-plan
     * après délai. Quelqu'un qui prête son téléphone, le pose, ou quitte son
     * bureau n'avait aucun moyen de refermer le portefeuille sur-le-champ : il
     * fallait attendre que le délai s'écoule, en laissant les soldes affichés.
     *
     * Ne touche QUE la session : ni les clés, ni le portefeuille, ni les
     * réglages. C'est l'équivalent de refermer une porte, pas de déménager.
     */
    fun verrouillerMaintenant() = sessionLock.lock()

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = themeController.mode

    fun setThemeMode(mode: ThemeMode) = themeController.setMode(mode)

    init {
        loadSettings()
    }

    private fun loadSettings() {
        _state.update {
            it.copy(
                isBiometricEnabled = secureStorage.isBiometricEnabled(),
                autoLockMinutes = secureStorage.getAutoLockMinutes(),
                selectedCurrency = currencyController.currency.value,
                walletName = walletNameController.name.value,
                hasPanicPin = secureStorage.getPanicPinHash() != null
            )
        }
    }

    fun setWalletName(name: String) {
        walletNameController.setName(name)
        _state.update { it.copy(walletName = name.trim()) }
    }

    fun setBiometric(enabled: Boolean) {
        secureStorage.setBiometricEnabled(enabled)
        _state.update { it.copy(isBiometricEnabled = enabled) }
    }

    fun setAutoLock(minutes: Int) {
        secureStorage.setAutoLockMinutes(minutes)
        _state.update { it.copy(autoLockMinutes = minutes) }
    }

    fun setCurrency(currency: String) {
        currencyController.setCurrency(currency)
        _state.update { it.copy(selectedCurrency = currency) }
    }

    fun setLanguage(language: String) = _state.update { it.copy(selectedLanguage = language) }
}
