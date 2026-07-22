package com.vaultex.core.session

import com.vaultex.core.security.SecureStorage
import com.vaultex.data.local.dao.PendingSendDao
import com.vaultex.data.local.dao.TransactionDao
import com.vaultex.data.local.dao.WalletDao
import com.vaultex.data.local.entity.WalletEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinateur MULTI-WALLETS — la seule porte d'entrée pour créer, activer et
 * supprimer un wallet. Il garantit les invariants fund-critiques :
 *
 *  1. Chaque wallet garde SON seed chiffré (mnemonic_<id>) : créer un nouveau
 *     wallet n'écrase JAMAIS le seed d'un autre (l'ancien bug faisait
 *     exactement ça via l'emplacement unique KEY_MNEMONIC).
 *  2. Tout changement de wallet actif PURGE les caches financiers (instantané
 *     de solde, transactions locales, envois en attente) : le nouveau wallet
 *     n'affiche jamais les montants de l'ancien, et un envoi en file ne peut
 *     pas être signé avec la mauvaise clé.
 *  3. L'ancien wallet unique (avant multi-wallets) est migré et INSCRIT dans
 *     la liste — il reste sélectionnable, son seed n'est pas perdu.
 *
 * L'API historique SecureStorage.getMnemonic()/getPassphrase() lit toujours le
 * wallet ACTIF : la dérivation d'adresses, l'envoi, le swap et les soldes
 * suivent automatiquement, sans modification du code aval.
 */
@Singleton
class WalletStore @Inject constructor(
    private val secureStorage: SecureStorage,
    private val walletDao: WalletDao,
    private val transactionDao: TransactionDao,
    private val pendingSendDao: PendingSendDao,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
) {

    /**
     * Migration : si un wallet actif existe (ancien wallet unique inclus) mais
     * n'apparaît pas encore dans la table `wallets`, on l'y inscrit. Idempotent.
     */
    suspend fun ensureRegistered() {
        val id = secureStorage.ensureActiveWallet() ?: return
        if (walletDao.getById(id) == null) {
            walletDao.deactivateAll()
            walletDao.insert(
                WalletEntity(
                    id = id,
                    name = secureStorage.getWalletName().ifBlank { "Wallet 1" },
                    isActive = true,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * Ajoute un wallet (création ou import) et l'ACTIVE. Le seed est stocké sous
     * son propre id — les seeds des autres wallets restent intacts.
     */
    suspend fun addWallet(mnemonic: String, passphrase: String, imported: Boolean): WalletEntity {
        ensureRegistered()   // l'ancien wallet doit être listé AVANT d'ajouter le nouveau
        // Id garanti UNIQUE : une collision écraserait le seed d'un autre wallet.
        var id: String
        do {
            id = "w_" + java.util.UUID.randomUUID().toString().take(8)
        } while (secureStorage.hasWalletSecrets(id) || walletDao.getById(id) != null)
        secureStorage.saveWalletSecrets(id, mnemonic, passphrase)
        val number = nextWalletNumber()
        val entity = WalletEntity(
            id = id,
            name = if (imported) "Wallet importé $number" else "Wallet $number",
            isActive = true,
            createdAt = System.currentTimeMillis()
        )
        walletDao.deactivateAll()
        walletDao.insert(entity)
        activateSecrets(entity)
        return entity
    }

    /** Nombre de wallets actuellement présents sur cet appareil. */
    suspend fun walletCount(): Int = try { walletDao.count() } catch (_: Exception) { 0 }

    /**
     * Prochain NUMÉRO de wallet — compteur MONOTONE qui ne se répète jamais,
     * même après suppression d'un wallet du milieu. L'ancien calcul (count + 1)
     * pouvait produire deux « Wallet 3 » (supprimer le 2 puis en créer un).
     * Baseline = max(compteur mémorisé, nombre actuel) → couvre les installs
     * existants qui n'avaient pas ce compteur.
     */
    private suspend fun nextWalletNumber(): Int {
        val prefs = appContext.getSharedPreferences("vaultex_wallet_seq", android.content.Context.MODE_PRIVATE)
        val current = try { walletDao.count() } catch (_: Exception) { 0 }
        val next = maxOf(prefs.getInt("last_number", 0), current) + 1
        prefs.edit().putInt("last_number", next).apply()
        return next
    }

    /** Bascule vers un autre wallet (son seed doit exister). */
    suspend fun switchWallet(id: String): Boolean {
        val entity = walletDao.getById(id) ?: return false
        if (!secureStorage.hasWalletSecrets(id)) return false   // jamais activer un wallet sans seed
        walletDao.deactivateAll()
        walletDao.activate(id)
        activateSecrets(entity)
        return true
    }

    /**
     * Supprime un wallet INACTIF : entité + seed chiffré. Refuse le wallet
     * actif (il faut d'abord basculer sur un autre).
     */
    suspend fun deleteWallet(id: String): Boolean {
        if (id == secureStorage.activeWalletId()) return false
        walletDao.delete(id)
        secureStorage.deleteWalletSecrets(id)
        // Sa liste de tokens personnalisés disparaît avec lui.
        try {
            appContext.getSharedPreferences(
                com.vaultex.data.repository.TokenRepository.OWNERSHIP_PREFS,
                android.content.Context.MODE_PRIVATE
            ).edit().remove(id).apply()
        } catch (_: Exception) { }
        return true
    }

    /** Rend [entity] actif côté secrets + purge les caches financiers. */
    private suspend fun activateSecrets(entity: WalletEntity) {
        secureStorage.setActiveWalletId(entity.id)
        secureStorage.saveWalletName(entity.name)
        // PURGE : aucun montant/tx de l'ancien wallet ne doit survivre.
        secureStorage.clearWalletCaches()
        try { transactionDao.deleteAll() } catch (_: Exception) { }
        try { pendingSendDao.deleteAll() } catch (_: Exception) { }
        // Soldes de référence du détecteur de dépôts : remis à zéro, sinon il
        // comparerait le solde du NOUVEAU wallet à celui de l'ancien et
        // enverrait de fausses notifications « dépôt reçu ». Après cette purge,
        // son premier passage re-mémorise sans notifier.
        try {
            appContext.getSharedPreferences("deposit_check_prefs", android.content.Context.MODE_PRIVATE)
                .edit().clear().apply()
        } catch (_: Exception) { }
        // Force l'accueil à recharger les soldes du nouveau wallet dès son retour.
        BalanceRefreshSignal.signalTxSent()
    }
}
