package com.vaultex.ui.utils

/**
 * DÉPRÉCIÉ — Ne plus utiliser.
 * Remplacé par OnboardingViewModel.saveWallet() qui appelle PinManager (PBKDF2+salt).
 */
@Deprecated("Utiliser OnboardingViewModel", level = DeprecationLevel.ERROR)
object PinMemory {
    var pinCode: String = ""
}