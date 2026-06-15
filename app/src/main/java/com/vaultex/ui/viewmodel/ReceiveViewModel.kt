package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.core.crypto.WalletManager
import com.vaultex.core.security.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ReceiveViewModel @Inject constructor(
    private val secureStorage: SecureStorage
) : ViewModel() {

    data class State(
        val addresses: Map<String, String> = emptyMap(),
        val isLoading: Boolean = true
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val addresses = withContext(Dispatchers.IO) {
                val mnemonic = secureStorage.getMnemonic() ?: return@withContext emptyMap<String, String>()
                val derived = WalletManager.deriveAddresses(mnemonic, secureStorage.getPassphrase())
                mapOf(
                    "BTC" to derived.btc,
                    "ETH" to derived.eth,
                    "BNB" to derived.bnb,
                    "SOL" to derived.sol,
                    "TRX" to derived.trx
                )
            }
            _state.update { it.copy(addresses = addresses, isLoading = false) }
        }
    }
}
