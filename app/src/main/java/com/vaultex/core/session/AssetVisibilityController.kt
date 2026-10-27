package com.vaultex.core.session

import com.vaultex.core.security.SecureStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monnaies activées (visibles) dans « Mes actifs », partagé et persisté.
 * Règle d'affichage : un actif apparaît s'il est activé ICI OU s'il a un
 * solde > 0 (on ne masque jamais des fonds).
 */
@Singleton
class AssetVisibilityController @Inject constructor(
    private val secureStorage: SecureStorage
) {
    private val _visible = MutableStateFlow(secureStorage.getVisibleAssets())
    val visible: StateFlow<Set<String>> = _visible.asStateFlow()

    /** Active/désactive une monnaie. */
    fun toggle(symbol: String) {
        val next = _visible.value.toMutableSet()
        if (!next.add(symbol)) next.remove(symbol)
        secureStorage.setVisibleAssets(next)
        _visible.value = next
    }

    companion object {
        /**
         * Toutes les monnaies supportées par l'app (ordre d'affichage).
         *
         * LE PI N'Y EST QUE S'IL EST ALLUMÉ. Laisser un actif en pause dans
         * cette liste le ferait apparaître dans « Mes actifs » avec un
         * interrupteur qui n'allume rien — le portefeuille ne le construit
         * plus. Voir [com.vaultex.core.config.Fonctionnalites.PI_ACTIF].
         */
        val SUPPORTED = listOfNotNull(
            "BTC", "ETH", "BNB", "SOL", "TRX", "USDT", "USDT-ETH", "USDT-BNB",
            "PI".takeIf { com.vaultex.core.config.Fonctionnalites.PI_ACTIF }
        )
    }
}
