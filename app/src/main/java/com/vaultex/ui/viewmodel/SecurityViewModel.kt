package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.vaultex.core.security.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class SecurityState(
    val isBiometricEnabled: Boolean = false,
    val autoLockMinutes: Int = 5,
    val hasPanicPin: Boolean = false
)

@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val secureStorage: SecureStorage
) : ViewModel() {

    private val _state = MutableStateFlow(
        SecurityState(
            isBiometricEnabled = secureStorage.isBiometricEnabled(),
            autoLockMinutes = secureStorage.getAutoLockMinutes(),
            hasPanicPin = secureStorage.getPanicPinHash() != null
        )
    )
    val state: StateFlow<SecurityState> = _state.asStateFlow()

    fun setBiometricEnabled(enabled: Boolean) {
        secureStorage.setBiometricEnabled(enabled)
        _state.update { it.copy(isBiometricEnabled = enabled) }
    }

    fun setAutoLockMinutes(minutes: Int) {
        secureStorage.setAutoLockMinutes(minutes)
        _state.update { it.copy(autoLockMinutes = minutes) }
    }

    /**
     * « Effacer le wallet » : efface TOUT l'appareil (tous les wallets, PIN,
     * historique, caches) — même effacement que le PIN de panique. Les fonds
     * restent sur la blockchain : ré-importables avec la phrase de récupération.
     * L'appelant DOIT redémarrer le process juste après (AppRestart.restart).
     */
    fun wipeAllData() = secureStorage.nukeAllData()
}
