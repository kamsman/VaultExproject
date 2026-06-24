package com.vaultex.core.session

import com.vaultex.core.security.SecureStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Suivi des transactions SORTANTES en attente de confirmation on-chain.
 * Partagé (singleton) et persisté : survit au changement d'écran et au
 * redémarrage du process, pour que le badge « ! » sur le dashboard reste
 * cohérent jusqu'à la confirmation.
 */
@Singleton
class PendingTxStore @Inject constructor(
    private val secureStorage: SecureStorage
) {
    /**
     * @param symbol  Monnaie affichée (ex: USDT, USDT-BNB, SHIB) — sert au badge.
     * @param chainKey Chaîne de confirmation (BTC/ETH/BNB/SOL/TRX).
     * @param hash    Hash de la transaction.
     * @param confirmations / target  Progression X / Y.
     * @param confirmed  Vrai dès que la cible est atteinte (le badge disparaît).
     * @param confirmedAt  Horodatage de confirmation (pour le nettoyage différé).
     */
    data class PendingTx(
        val symbol: String,
        val chainKey: String,
        val hash: String,
        val createdAt: Long,
        val confirmations: Int = 0,
        val target: Int = 1,
        val confirmed: Boolean = false,
        val confirmedAt: Long = 0L
    )

    private val gson = com.google.gson.Gson()
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<PendingTx>> = _items.asStateFlow()

    private data class Wrapper(val list: List<PendingTx> = emptyList())

    private fun load(): List<PendingTx> = try {
        val json = secureStorage.getPendingTxs() ?: return emptyList()
        gson.fromJson(json, Wrapper::class.java)?.list ?: emptyList()
    } catch (_: Exception) { emptyList() }

    private fun persist(list: List<PendingTx>) {
        _items.value = list
        try { secureStorage.savePendingTxs(gson.toJson(Wrapper(list))) } catch (_: Exception) {}
    }

    @Synchronized
    fun add(symbol: String, chainKey: String, hash: String, target: Int, now: Long) {
        if (_items.value.any { it.hash == hash }) return
        persist(_items.value + PendingTx(symbol, chainKey, hash, now, 0, target, false, 0L))
    }

    @Synchronized
    fun update(hash: String, confirmations: Int, confirmed: Boolean, now: Long) {
        persist(_items.value.map {
            if (it.hash != hash) it
            else it.copy(
                confirmations = confirmations,
                confirmed = confirmed,
                confirmedAt = if (confirmed && it.confirmedAt == 0L) now else it.confirmedAt
            )
        })
    }

    @Synchronized
    fun remove(hash: String) {
        persist(_items.value.filterNot { it.hash == hash })
    }
}
