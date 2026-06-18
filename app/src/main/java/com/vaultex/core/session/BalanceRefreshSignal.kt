package com.vaultex.core.session

/**
 * Signal « une transaction vient d'être envoyée ». Permet à l'écran d'accueil,
 * dès qu'il redevient visible, de déclencher une rafale de rafraîchissements
 * pour afficher le nouveau solde dès la confirmation on-chain (au lieu
 * d'attendre le rafraîchissement périodique).
 */
object BalanceRefreshSignal {

    @Volatile
    private var pending = false

    fun signalTxSent() {
        pending = true
    }

    /** Lit et efface le signal. */
    fun consumePending(): Boolean {
        val p = pending
        pending = false
        return p
    }
}
