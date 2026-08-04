package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.vaultex.core.security.PinManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class PanicPinViewModel @Inject constructor(
    private val pinManager: PinManager
) : ViewModel() {

    private val _saved = MutableStateFlow<Boolean?>(null)
    val saved: StateFlow<Boolean?> = _saved.asStateFlow()

    fun savePin(pin: String) {
        try {
            pinManager.setPin(pin, isPanic = true)
            _saved.value = true
        } catch (_: Exception) {
            _saved.value = false
        }
    }
}
