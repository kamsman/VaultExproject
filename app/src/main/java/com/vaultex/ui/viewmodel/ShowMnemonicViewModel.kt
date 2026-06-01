package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.core.security.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ShowMnemonicViewModel @Inject constructor(
    private val secureStorage: SecureStorage
) : ViewModel() {

    private val _words = MutableStateFlow<List<String>>(emptyList())
    val words: StateFlow<List<String>> = _words.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val mnemonic = withContext(Dispatchers.IO) { secureStorage.getMnemonic() }
            _words.value = mnemonic?.split(" ") ?: emptyList()
        }
    }

    // Efface la mnémonique de la RAM dès que l'écran est quitté
    fun clear() {
        _words.value = emptyList()
    }
}
