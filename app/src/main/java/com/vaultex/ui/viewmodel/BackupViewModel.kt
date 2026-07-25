package com.vaultex.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.core.crypto.WalletManager
import com.vaultex.core.security.PinManager
import com.vaultex.core.security.PinVerificationResult
import com.vaultex.core.security.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Action protégée par PIN/biométrie sur l'écran Sauvegarde. */
enum class BackupAuthAction { PHRASE, KEY }

data class BackupState(
    val mnemonic: String? = null,
    val isRevealed: Boolean = false,
    val showPinDialog: Boolean = false,
    val pinInput: String = "",
    val pinError: String? = null,
    val pendingAction: BackupAuthAction? = null,
    val selectedChain: String = "BTC",
    val exportedKey: String? = null,
    val phraseBackedUp: Boolean = false
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val secureStorage: SecureStorage,
    private val pinManager: PinManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: Context
) : ViewModel() {

    private val prefs = appContext.getSharedPreferences("vaultex_backup", Context.MODE_PRIVATE)

    /** Clé PAR WALLET : sans ça, un flag global marquerait un NOUVEAU wallet
     *  comme « déjà sauvegardé » simplement parce qu'un ancien l'était. */
    private fun backedUpKey(): String {
        val id = secureStorage.activeWalletId() ?: "legacy"
        return "phrase_backed_up_$id"
    }

    private val _state = MutableStateFlow(
        BackupState(phraseBackedUp = prefs.getBoolean(backedUpKey(), false))
    )
    val state: StateFlow<BackupState> = _state.asStateFlow()

    /** Demande d'affichage de la phrase (PIN ou biométrie d'abord). */
    fun requestReveal() = _state.update {
        it.copy(showPinDialog = true, pendingAction = BackupAuthAction.PHRASE, pinInput = "", pinError = null)
    }

    /** Demande d'export de la clé privée de la chaîne sélectionnée. */
    fun requestExport() = _state.update {
        it.copy(showPinDialog = true, pendingAction = BackupAuthAction.KEY, pinInput = "", pinError = null)
    }

    fun selectChain(chain: String) = _state.update { it.copy(selectedChain = chain, exportedKey = null) }

    fun setPinInput(v: String) {
        if (v.length <= 6 && v.all { it.isDigit() }) {
            _state.update { it.copy(pinInput = v, pinError = null) }
            if (v.length == 6) verifyPin(v)
        }
    }

    /** Succès biométrique : même effet qu'un PIN valide. */
    fun onAuthSuccess() {
        val action = _state.value.pendingAction ?: return
        _state.update { it.copy(showPinDialog = false, pinInput = "", pinError = null) }
        perform(action)
    }

    private fun verifyPin(pin: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) { pinManager.verifyPin(pin) }
            when (result) {
                is PinVerificationResult.Valid -> onAuthSuccess()
                is PinVerificationResult.Invalid ->
                    _state.update { it.copy(pinError = appContext.getString(com.vaultex.R.string.pin_wrong_attempts, result.remainingAttempts), pinInput = "") }
                is PinVerificationResult.Locked ->
                    _state.update { it.copy(pinError = appContext.getString(com.vaultex.R.string.pin_blocked_seconds, result.unlockInSeconds.toInt()), pinInput = "") }
                is PinVerificationResult.PanicTriggered ->
                    _state.update { it.copy(pinError = appContext.getString(com.vaultex.R.string.pin_panic_wiped), pinInput = "") }
            }
        }
    }

    private fun perform(action: BackupAuthAction) {
        when (action) {
            BackupAuthAction.PHRASE -> {
                val mnemonic = secureStorage.getMnemonic()
                // La phrase a été vue → on marque la sauvegarde comme faite (statut),
                // pour CE wallet précisément.
                prefs.edit().putBoolean(backedUpKey(), true).apply()
                // Jalon SÉCURITÉ (Telegram) : combien d'utilisateurs protègent
                // réellement leurs fonds. Envoyé une seule fois par installation.
                com.vaultex.core.monitoring.AdminBot.milestoneBackupDone()
                _state.update { it.copy(isRevealed = true, mnemonic = mnemonic, phraseBackedUp = true) }
            }
            BackupAuthAction.KEY -> viewModelScope.launch {
                val chain = _state.value.selectedChain
                val key = withContext(Dispatchers.Default) {
                    try {
                        val mnemonic = secureStorage.getMnemonic() ?: return@withContext null
                        WalletManager.exportPrivateKey(mnemonic, secureStorage.getPassphrase(), chain)
                    } catch (_: Exception) { null }
                }
                _state.update {
                    if (key != null) it.copy(exportedKey = key)
                    else it.copy(pinError = appContext.getString(com.vaultex.R.string.backup_load_error))
                }
            }
        }
    }

    fun dismissPinDialog() = _state.update {
        it.copy(showPinDialog = false, pinInput = "", pinError = null, pendingAction = null)
    }

    fun hide() = _state.update { it.copy(mnemonic = null, isRevealed = false) }

    fun hideKey() = _state.update { it.copy(exportedKey = null) }
}
