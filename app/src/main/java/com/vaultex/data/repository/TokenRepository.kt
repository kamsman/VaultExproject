package com.vaultex.data.repository

import android.content.Context
import com.vaultex.data.local.dao.TokenDao
import com.vaultex.data.local.entity.TokenEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tokens personnalisés (ajoutés par contrat) — désormais PAR WALLET.
 *
 * La table Room reste globale (définitions de contrats), mais chaque wallet a
 * sa propre liste de propriété (prefs "vaultex_wallet_tokens" : walletId →
 * ensemble de "contrat|chaîne"). Résultat :
 *  - un NOUVEAU wallet démarre PROPRE, avec uniquement les monnaies par défaut
 *    (BTC, ETH, BNB, SOL, TRX, USDT) — il n'hérite pas des tokens de l'ancien ;
 *  - re-basculer sur un wallet redonne SA liste de tokens, intacte.
 *
 * Migration : au premier passage, les tokens existants (pré-multi-wallets)
 * sont adoptés par le wallet actif (l'ancien wallet unique). Idempotent.
 */
@Singleton
class TokenRepository @Inject constructor(
    private val tokenDao: TokenDao,
    private val secureStorage: com.vaultex.core.security.SecureStorage,
    @ApplicationContext context: Context
) {
    private val ownPrefs = context.getSharedPreferences(OWNERSHIP_PREFS, Context.MODE_PRIVATE)

    private fun keyOf(contract: String, blockchain: String) = "${contract.lowercase()}|$blockchain"
    private fun keyOf(t: TokenEntity) = keyOf(t.contractAddress, t.blockchain)
    private fun ownedBy(walletId: String): MutableSet<String> =
        (ownPrefs.getStringSet(walletId, emptySet()) ?: emptySet()).toMutableSet()
    private fun saveOwned(walletId: String, set: Set<String>) {
        ownPrefs.edit().putStringSet(walletId, set).apply()
    }
    private fun activeId(): String? = secureStorage.ensureActiveWallet()

    /** Adoption UNIQUE : les tokens d'avant multi-wallets → wallet actif. */
    @Synchronized
    private fun ensureOwnershipInit(existing: List<TokenEntity>) {
        if (ownPrefs.getBoolean(KEY_INIT, false)) return
        val id = activeId()
        if (id != null && existing.isNotEmpty()) {
            saveOwned(id, existing.map { keyOf(it) }.toSet())
        }
        ownPrefs.edit().putBoolean(KEY_INIT, true).apply()
    }

    fun observeAll(): Flow<List<TokenEntity>> = tokenDao.observeAll()

    suspend fun getByBlockchain(blockchain: String): List<TokenEntity> =
        tokenDao.getByBlockchain(blockchain)

    /** Tokens personnalisés visibles DU WALLET ACTIF uniquement. */
    suspend fun getCustom(): List<TokenEntity> {
        ensureOwnershipInit(tokenDao.getAllCustom())
        val id = activeId() ?: return emptyList()
        val owned = ownedBy(id)
        return tokenDao.getCustom().filter { keyOf(it) in owned }
    }

    /** Ajoute un token AU WALLET ACTIF (définition globale + propriété locale). */
    suspend fun addToken(token: TokenEntity) {
        ensureOwnershipInit(tokenDao.getAllCustom())
        tokenDao.insert(token)
        activeId()?.let { id -> saveOwned(id, ownedBy(id).apply { add(keyOf(token)) }) }
    }

    suspend fun setHidden(address: String, blockchain: String, hidden: Boolean) =
        tokenDao.setHidden(address, blockchain, hidden)

    /**
     * Retire le token du WALLET ACTIF ; la définition n'est supprimée de la
     * base que si plus aucun wallet ne la possède.
     */
    suspend fun delete(address: String, blockchain: String) {
        val k = keyOf(address, blockchain)
        activeId()?.let { id -> saveOwned(id, ownedBy(id).apply { remove(k) }) }
        val stillOwned = ownPrefs.all.any { (pk, v) ->
            pk != KEY_INIT && (v as? Set<*>)?.contains(k) == true
        }
        if (!stillOwned) tokenDao.delete(address, blockchain)
    }

    companion object {
        const val OWNERSHIP_PREFS = "vaultex_wallet_tokens"
        private const val KEY_INIT = "initialized"
    }
}
