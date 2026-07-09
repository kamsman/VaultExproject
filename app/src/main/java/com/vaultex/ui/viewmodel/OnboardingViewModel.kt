package com.vaultex.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.core.crypto.WalletManager
import com.vaultex.core.security.PinManager
import com.vaultex.core.security.SecureStorage
import com.vaultex.core.session.AppLaunchManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ViewModel partagé pour tout le flux d'onboarding.
 * Remplace WalletMemory et PinMemory — la mnémonique ne transite plus
 * par des singletons globaux non sécurisés.
 *
 * Scoped à l'Activity (passé explicitement depuis VaultExNavGraph) pour
 * être partagé entre MnemonicDisplay → MnemonicVerify → PinSetup.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val secureStorage: SecureStorage,
    private val pinManager: PinManager,
    private val sessionLock: com.vaultex.core.session.SessionLockManager,
    private val notifPrefs: com.vaultex.core.session.NotifPrefs,
    private val notificationCenter: com.vaultex.core.session.NotificationCenter,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // Mnémonique en mémoire uniquement le temps de l'onboarding
    private val _mnemonic = MutableStateFlow<List<String>>(emptyList())
    val mnemonic: StateFlow<List<String>> = _mnemonic.asStateFlow()

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    // Index du mot à vérifier — fixé à la génération, jamais changé
    var verifyWordIndex: Int = -1
        private set

    /** Génère une nouvelle mnémonique (seulement si pas déjà générée). */
    fun generateMnemonic() {
        if (_mnemonic.value.isEmpty()) {
            val words = WalletManager.generateMnemonic()
            _mnemonic.value = words
            verifyWordIndex = words.indices.random()
        }
    }

    /**
     * Charge une mnémonique importée par l'utilisateur.
     * @return true si valide (12 mots BIP39), false sinon
     */
    fun setImportedMnemonic(words: List<String>): Boolean {
        val phrase = words.joinToString(" ")
        if (!WalletManager.validateMnemonic(phrase)) return false
        _mnemonic.value = words
        wasImported = true
        return true
    }

    // true si le wallet vient d'un import (pour l'événement admin créé/importé).
    private var wasImported = false

    /** Vérifie que le mot à l'index donné correspond à la saisie. */
    fun verifyWord(index: Int, input: String): Boolean =
        _mnemonic.value.getOrNull(index)?.equals(input.trim(), ignoreCase = true) == true

    // Passphrase BIP39 optionnelle saisie pendant l'onboarding (« 13e mot »).
    private val _passphrase = MutableStateFlow("")
    val passphrase: StateFlow<String> = _passphrase.asStateFlow()
    fun setPassphrase(value: String) { _passphrase.value = value }

    /**
     * Sauvegarde la mnémonique (chiffrée) + la passphrase BIP39 (chiffrée)
     * + le PIN (hashé PBKDF2). Efface la mnémonique de la mémoire ensuite.
     */
    fun saveWallet(pin: String) {
        viewModelScope.launch {
            _saveState.value = SaveState.Loading
            try {
                val hadMnemonic = _mnemonic.value.isNotEmpty()
                val mnemonicStr = _mnemonic.value.joinToString(" ")
                withContext(Dispatchers.IO) {
                    secureStorage.saveMnemonic(mnemonicStr)
                    secureStorage.savePassphrase(_passphrase.value.trim())
                    val isChange = pinManager.hasPin()
                    pinManager.setPin(pin)
                    if (isChange) try {
                        if (notifPrefs.pinChangeAlerts.value) {
                            val t = context.getString(com.vaultex.R.string.notif_pin_title)
                            val b = context.getString(com.vaultex.R.string.notif_pin_body)
                            notificationCenter.push(t, b)
                            com.vaultex.core.util.LocalNotifier.show(context, t, b)
                        }
                    } catch (_: Exception) { }
                    AppLaunchManager.setWalletCreated(context, true)
                }
                _mnemonic.value = emptyList() // efface de la RAM
                _passphrase.value = ""        // efface la passphrase de la RAM
                sessionLock.markUnlocked()    // nouvelle session déverrouillée
                _saveState.value = SaveState.Success
                // Événement admin (Telegram) : un vrai wallet vient d'être créé/importé.
                if (hadMnemonic) com.vaultex.core.monitoring.AdminBot.walletCreated(wasImported)
            } catch (e: Exception) {
                _saveState.value = SaveState.Error(e.message ?: "Erreur inconnue")
            }
        }
    }

    fun resetSaveState() {
        _saveState.value = SaveState.Idle
    }

    sealed class SaveState {
        data object Idle : SaveState()
        data object Loading : SaveState()
        data object Success : SaveState()
        data class Error(val message: String) : SaveState()
    }
}
