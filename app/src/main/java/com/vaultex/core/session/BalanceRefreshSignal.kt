package com.vaultex.core.session

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Signal « les soldes ont changé, rafraîchis-les MAINTENANT ».
 *
 * Le problème qu'il résout : deux horloges tournaient sans se connaître. Le
 * détecteur de dépôts interroge les chaînes toutes les 30 s et notifie dès
 * qu'un solde monte ; l'écran d'accueil, lui, rafraîchit ses montants selon
 * son propre cycle de 45 s.
 *
 * Résultat pour l'utilisateur, selon lequel des deux tombait en premier :
 * soit la notification arrivait AVANT que les fonds ne s'affichent — il ouvre
 * l'application et ne voit rien, ce qui est angoissant sur un portefeuille —
 * soit le solde avait déjà bougé et la notification tombait APRÈS, pour
 * annoncer quelque chose de déjà vu.
 *
 * Le signal est désormais OBSERVABLE : celui qui détecte le dépôt prévient
 * directement l'écran, qui rafraîchit dans la seconde. Les deux événements
 * cessent d'être indépendants.
 *
 * [consumePending] reste utile pour le cas « je reviens sur l'écran après un
 * envoi » : l'écran était détruit au moment de l'émission, personne n'écoutait.
 */
object BalanceRefreshSignal {

    /**
     * Émissions vues par les écrans VIVANTS. `replay = 0` : un écran qui
     * s'ouvre ne rejoue pas les anciens signaux, il n'écoute que la suite.
     * Le tampon d'une unité avec écrasement évite tout blocage de l'émetteur,
     * qui est souvent un worker d'arrière-plan.
     */
    private val _events = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<Unit> = _events

    @Volatile
    private var pending = false

    fun signalTxSent() {
        pending = true
        // tryEmit : appelable depuis du code non-suspendu (workers, callbacks).
        _events.tryEmit(Unit)
    }

    /** Lit et efface le signal — pour un écran qui redevient visible. */
    fun consumePending(): Boolean {
        val p = pending
        pending = false
        return p
    }
}
