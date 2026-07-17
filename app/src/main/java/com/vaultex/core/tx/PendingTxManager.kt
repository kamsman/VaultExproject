package com.vaultex.core.tx

import com.vaultex.core.session.BalanceRefreshSignal
import com.vaultex.core.session.PendingTxStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sonde en arrière-plan les transactions sortantes en attente jusqu'à leur
 * confirmation. Singleton à durée de vie « process » : continue de tourner
 * quand l'utilisateur quitte l'écran d'envoi pour le dashboard, ce qui permet
 * au badge « ! » de se mettre à jour puis de disparaître automatiquement.
 */
@Singleton
class PendingTxManager @Inject constructor(
    private val store: PendingTxStore,
    private val checker: ConfirmationChecker,
    private val transactionDao: com.vaultex.data.local.dao.TransactionDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)

    /** Enregistre une nouvelle transaction à suivre et lance la sonde. */
    fun track(symbol: String, chainKey: String, hash: String) {
        store.add(symbol, chainKey, hash, ConfirmationChecker.targetFor(chainKey), System.currentTimeMillis())
        kick()
    }

    /** (Re)lance la boucle de sondage si elle n'est pas déjà active. */
    fun kick() {
        if (!running.compareAndSet(false, true)) return
        scope.launch {
            try {
                while (true) {
                    val items = store.items.value
                    if (items.isEmpty()) break

                    val now = System.currentTimeMillis()
                    var anyConfirmedNow = false
                    for (tx in items) {
                        if (tx.confirmed) {
                            // Nettoyage différé : on garde 25 s l'état « confirmé »
                            // pour laisser les écrans afficher le succès, puis on retire.
                            if (now - tx.confirmedAt > 25_000L) store.remove(tx.hash)
                            continue
                        }
                        // Abandon des transactions trop vieilles jamais vues (45 min).
                        if (now - tx.createdAt > 45 * 60_000L) { store.remove(tx.hash); continue }

                        val status = checker.check(tx.chainKey, tx.hash)
                        if (status.failed) {
                            store.remove(tx.hash)
                        } else {
                            store.update(tx.hash, status.confirmations, status.confirmed, now)
                            if (status.confirmed) {
                                anyConfirmedNow = true
                                // Aligne « Récent » (Dashboard) sur la confirmation
                                // réelle : l'entrée locale créée à l'envoi passe de
                                // « pending » à « confirmed » sans attendre la
                                // prochaine synchro d'Historique.
                                try { transactionDao.updateStatus(tx.hash, "confirmed", status.confirmations) } catch (_: Exception) { }
                            }
                        }
                    }
                    // Une confirmation peut changer le solde → on demande un refresh.
                    if (anyConfirmedNow) BalanceRefreshSignal.signalTxSent()

                    delay(12_000L)
                }
            } finally {
                running.set(false)
                // Si des éléments sont réapparus entre-temps, on relance.
                if (store.items.value.isNotEmpty()) kick()
            }
        }
    }
}
