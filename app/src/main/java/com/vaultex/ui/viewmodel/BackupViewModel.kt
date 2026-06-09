package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.vaultex.core.security.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class BackupState(
    val mnemonic: String? = null,
    val isRevealed: Boolean = false
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val secureStorage: SecureStorage
) : ViewModel() {

    private val _state = MutableStateFlow(BackupState())
    val state: StateFlow<BackupState> = _state.asStateFlow()

    fun reveal() {
        val mnemonic = secureStorage.getMnemonic()
        _state.update { it.copy(mnemonic = mnemonic, isRevealed = true) }
    }

    fun hide() {
        _state.update { it.copy(mnemonic = null, isRevealed = false) }
    }
}
