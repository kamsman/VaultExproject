package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.core.crypto.WalletManager
import com.vaultex.core.security.SecureStorage
import com.vaultex.domain.pi.PiCompteService
import com.vaultex.domain.pi.SoldePi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class PiState(
    val adresse: String? = null,
    val solde: SoldePi? = null,
    val chargement: Boolean = false
)

@HiltViewModel
class PiViewModel @Inject constructor(
    private val secureStorage: SecureStorage,
    private val compte: PiCompteService
) : ViewModel() {

    private val _state = MutableStateFlow(PiState())
    val state: StateFlow<PiState> = _state.asStateFlow()

    init { charger() }

    fun charger() {
        viewModelScope.launch {
            _state.update { it.copy(chargement = true) }
            val adresse = withContext(Dispatchers.IO) {
                val m = secureStorage.getMnemonic() ?: return@withContext null
                runCatching {
                    WalletManager.deriveAddresses(m, secureStorage.getPassphrase()).pi
                }.getOrNull()
            }
            if (adresse == null) {
                _state.update { it.copy(chargement = false) }
                return@launch
            }
            _state.update { it.copy(adresse = adresse) }
            val solde = withContext(Dispatchers.IO) { compte.solde(adresse) }
            _state.update { it.copy(solde = solde, chargement = false) }
        }
    }
}
