package com.vaultex.ui.utils

/**
 * DÉPRÉCIÉ — Ne plus utiliser.
 * Remplacé par OnboardingViewModel qui gère la mnémonique sans singleton global non sécurisé.
 */
@Deprecated("Utiliser OnboardingViewModel", level = DeprecationLevel.ERROR)
object WalletMemory {
    var mnemonicWords: List<String> = emptyList()
}