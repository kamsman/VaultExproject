package com.vaultex.ui.theme

import com.vaultex.core.security.SecureStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { LIGHT, DARK, SYSTEM }

/**
 * Source de vérité du thème choisi par l'utilisateur, persisté dans
 * SecureStorage. Exposé en StateFlow pour que la racine Compose
 * recompose automatiquement au changement.
 */
@Singleton
class ThemeController @Inject constructor(
    private val secureStorage: SecureStorage
) {
    private val _mode = MutableStateFlow(readMode())
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun setMode(mode: ThemeMode) {
        secureStorage.setThemeMode(mode.name)
        _mode.value = mode
    }

    private fun readMode(): ThemeMode = runCatching {
        ThemeMode.valueOf(secureStorage.getThemeMode())
    }.getOrDefault(ThemeMode.SYSTEM)
}
