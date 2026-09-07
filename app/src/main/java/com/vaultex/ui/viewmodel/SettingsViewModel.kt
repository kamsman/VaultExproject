package com.vaultex.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.core.security.PinManager
import com.vaultex.core.security.PinVerificationResult
import com.vaultex.core.security.SecureStorage
import com.vaultex.ui.theme.ThemeController
import com.vaultex.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SettingsState(
    val isBiometricEnabled: Boolean = false,
    val autoLockMinutes: Int = 5,
    val selectedCurrency: String = "XOF",
    val selectedLanguage: String = "fr",
    val walletName: String = "",
    val hasPanicPin: Boolean = false,
    val screenshotsAllowed: Boolean = false,
    /* Dialogue de code demandé avant d'AUTORISER les captures. */
    val showPinDialog: Boolean = false,
    val pinInput: String = "",
    val pinError: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val secureStorage: SecureStorage,
    private val themeController: ThemeController,
    private val currencyController: com.vaultex.core.session.CurrencyController,
    private val walletNameController: com.vaultex.core.session.WalletNameController,
    private val sessionLock: com.vaultex.core.session.SessionLockManager,
    private val pinManager: PinManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: Context
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
                hasPanicPin = secureStorage.getPanicPinHash() != null,
                screenshotsAllowed = secureStorage.areScreenshotsAllowed()
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

    /**
     * Autorise ou interdit les captures d'écran.
     *
     * Le ViewModel n'applique PAS le drapeau : il n'a pas d'Activity, et
     * FLAG_SECURE est une propriété de fenêtre. L'écran de réglages, lui,
     * en a une — il appelle ProtectionEcran juste après, pour que l'effet
     * soit immédiat plutôt qu'au prochain démarrage.
     */
    /*
    ═══════════════════════════════════════════════════════════════════════
    AUTORISER LES CAPTURES DEMANDE LE CODE. LES REBLOQUER, NON.
    ═══════════════════════════════════════════════════════════════════════

    L'asymétrie est voulue, et c'est la règle qui compte ici.

    Autoriser les captures AFFAIBLIT la protection : c'est ce geste-là qui
    doit prouver que la personne devant l'écran est bien la propriétaire.
    Sans cela, quelqu'un à qui l'on tend son téléphone deux minutes peut
    lever l'interdiction, revenir plus tard, et photographier la phrase de
    récupération. Le réglage aurait ouvert exactement la porte qu'il ferme.

    Les REBLOQUER resserre la protection. Exiger un code pour cela n'aurait
    protégé personne et aurait découragé le retour en arrière — un réglage
    de sécurité doit toujours pouvoir se refermer sans friction, y compris
    par quelqu'un qui doute d'avoir bien fait.
    */
    fun demanderAutorisationCaptures() = _state.update {
        it.copy(showPinDialog = true, pinInput = "", pinError = null)
    }

    fun annulerDialogueCaptures() = _state.update {
        it.copy(showPinDialog = false, pinInput = "", pinError = null)
    }

    fun setPinInput(v: String) {
        if (v.length <= 6 && v.all { c -> c.isDigit() }) {
            _state.update { it.copy(pinInput = v, pinError = null) }
            if (v.length == 6) verifierCode(v)
        }
    }

    /** Succès biométrique : même effet qu'un code valide. */
    fun onAuthSuccess() {
        _state.update { it.copy(showPinDialog = false, pinInput = "", pinError = null) }
        appliquerCaptures(true)
    }

    private fun verifierCode(pin: String) {
        viewModelScope.launch {
            when (val r = withContext(Dispatchers.Default) { pinManager.verifyPin(pin) }) {
                is PinVerificationResult.Valid -> onAuthSuccess()
                is PinVerificationResult.Invalid -> _state.update {
                    it.copy(
                        pinError = appContext.getString(
                            com.vaultex.R.string.pin_wrong_attempts, r.remainingAttempts
                        ),
                        pinInput = ""
                    )
                }
                is PinVerificationResult.Locked -> _state.update {
                    it.copy(
                        pinError = appContext.getString(
                            com.vaultex.R.string.pin_blocked_seconds, r.unlockInSeconds.toInt()
                        ),
                        pinInput = ""
                    )
                }
                /*
                Code de panique : le portefeuille vient d'être effacé.

                Ce cas DOIT être traité, et pas seulement parce que le
                compilateur l'exige sur une classe scellée. Le traiter comme
                un succès aurait autorisé les captures au moment précis où
                quelqu'un contraint l'utilisateur — soit exactement l'inverse
                de ce que le code de panique existe pour faire.
                */
                is PinVerificationResult.PanicTriggered -> _state.update {
                    it.copy(
                        pinError = appContext.getString(com.vaultex.R.string.pin_panic_wiped),
                        pinInput = ""
                    )
                }
            }
        }
    }

    /** Écrit le choix. Appelé sans code pour rebloquer, après code pour autoriser. */
    fun appliquerCaptures(allowed: Boolean) {
        secureStorage.setScreenshotsAllowed(allowed)
        _state.update { it.copy(screenshotsAllowed = allowed) }
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
