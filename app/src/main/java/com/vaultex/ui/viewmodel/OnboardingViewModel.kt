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
    private val hub: com.vaultex.core.session.NotificationHub,
    private val walletStore: com.vaultex.core.session.WalletStore,
    private val syncService: com.vaultex.core.tx.TransactionSyncService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    /** true si un PIN d'app existe déjà (ajout d'un wallet ≠ premier onboarding). */
    fun hasPin(): Boolean = pinManager.hasPin()

    /** Code anti-phishing, affiché avant la saisie d'une phrase de récupération. */
    fun antiPhishingCode(): String = secureStorage.getAntiPhishingCode()

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
            wasImported = false
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
     * Enregistre le wallet via WalletStore (MULTI-WALLETS : chaque seed a son
     * propre emplacement chiffré — créer un wallet n'écrase JAMAIS le
     * précédent) puis pose le PIN.
     *
     * [pin] null = un PIN existe déjà (ajout d'un wallet depuis le
     * gestionnaire) : on garde le PIN actuel, on n'y touche pas.
     */
    fun saveWallet(pin: String?) {
        viewModelScope.launch {
            _saveState.value = SaveState.Loading
            try {
                val hadMnemonic = _mnemonic.value.isNotEmpty()
                val mnemonicStr = _mnemonic.value.joinToString(" ")
                var created: com.vaultex.data.local.entity.WalletEntity? = null
                withContext(Dispatchers.IO) {
                    if (hadMnemonic) {
                        created = walletStore.addWallet(
                            mnemonic = mnemonicStr,
                            passphrase = _passphrase.value.trim(),
                            imported = wasImported
                        )
                        // CORRECTIF : pour un wallet fraîchement GÉNÉRÉ (pas
                        // importé), la seed vient de SecureRandom — aucun
                        // historique on-chain ne peut exister sur ses adresses.
                        // Sans ce pré-marquage, le tout premier dépôt reçu
                        // (typiquement le dépôt de TEST que l'utilisateur
                        // envoie juste après avoir créé le wallet) était
                        // confondu avec un « ancien historique à importer » et
                        // avalé en silence, sans aucune notification.
                        // Un wallet IMPORTÉ, lui, peut avoir un vrai passé :
                        // on garde pour lui le comportement prudent existant.
                        if (!wasImported) {
                            val addr = com.vaultex.core.crypto.WalletManager
                                .deriveAddresses(mnemonicStr, _passphrase.value.trim())
                            syncService.markFreshWalletBackfilled(addr)
                        }
                    }
                    if (pin != null) {
                        val isChange = pinManager.hasPin()
                        pinManager.setPin(pin)
                        if (isChange) try {
                            if (notifPrefs.pinChangeAlerts.value) {
                                val t = context.getString(com.vaultex.R.string.notif_pin_title)
                                val b = context.getString(com.vaultex.R.string.notif_pin_body)
                                // Alerte de SÉCURITÉ : passe par le hub comme
                                // le reste (bannière, pastille, cloche). La clé
                                // horodatée laisse passer chaque changement.
                                hub.post("pinchange:${System.currentTimeMillis()}", t, b)
                            }
                        } catch (_: Exception) { }
                    }
                    AppLaunchManager.setWalletCreated(context, true)
                }
                _mnemonic.value = emptyList() // efface de la RAM
                _passphrase.value = ""        // efface la passphrase de la RAM
                sessionLock.markUnlocked()    // nouvelle session déverrouillée
                _saveState.value = SaveState.Success
                // Événement admin (Telegram) : wallet créé/importé, avec son
                // nom (« Wallet 2 »), son code unique et le nombre de wallets
                // actuellement présents sur cet appareil (suivi du parc).
                created?.let {
                    val total = withContext(Dispatchers.IO) { walletStore.walletCount() }
                    com.vaultex.core.monitoring.AdminBot.walletCreated(wasImported, it.name, it.id, total)
                }
            } catch (e: Exception) {
                /*
                 * ECHEC DE CREATION DE PORTEFEUILLE — a signaler TOUJOURS.
                 *
                 * C'est le point de non-retour du parcours : un utilisateur
                 * qui echoue ici n'a pas d'application, et il n'a aucun moyen
                 * de le faire savoir autrement qu'en ecrivant.
                 *
                 * Un bug reel l'a montre : `generateKey()` levait
                 * StrongBoxUnavailableException sur tout appareil depourvu de
                 * puce de securite dediee — c'est-a-dire la quasi-totalite du
                 * marche vise. Personne ne pouvait creer de portefeuille, et
                 * rien ne le signalait. Le defaut n'a ete decouvert que parce
                 * que des testeurs se sont plaints.
                 *
                 * La marque et le modele sont joints : c'est ce qui distingue
                 * un incident isole d'une panne liee a une famille
                 * d'appareils.
                 */
                runCatching {
                    com.vaultex.core.monitoring.AdminBot.walletCreationFailed(
                        imported = wasImported,
                        reason = e.javaClass.simpleName + " : " + (e.message ?: "sans message")
                    )
                }
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
