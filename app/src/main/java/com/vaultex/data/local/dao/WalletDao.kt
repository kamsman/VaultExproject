package com.vaultex.data.local.dao

import androidx.room.*
import com.vaultex.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletDao {
    @Query("SELECT * FROM wallets ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<WalletEntity>>

    @Query("SELECT * FROM wallets WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): WalletEntity?

    @Query("SELECT * FROM wallets WHERE id = :id")
    suspend fun getById(id: String): WalletEntity?

    @Query("SELECT COUNT(*) FROM wallets")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(wallet: WalletEntity)

    @Query("UPDATE wallets SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE wallets SET isActive = 1 WHERE id = :id")
    suspend fun activate(id: String)

    @Query("UPDATE wallets SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("DELETE FROM wallets WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface AccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accounts: List<AccountEntity>)

    @Query("SELECT * FROM accounts WHERE walletId = :walletId")
    suspend fun getByWallet(walletId: String): List<AccountEntity>

    @Query("DELETE FROM accounts WHERE walletId = :walletId")
    suspend fun deleteByWallet(walletId: String)
}

@Dao
interface TokenDao {
    @Query("SELECT * FROM tokens")
    fun observeAll(): Flow<List<TokenEntity>>

    @Query("SELECT * FROM tokens WHERE blockchain = :blockchain")
    suspend fun getByBlockchain(blockchain: String): List<TokenEntity>

    @Query("SELECT * FROM tokens WHERE isCustom = 1 AND isHidden = 0")
    suspend fun getCustom(): List<TokenEntity>

    @Query("SELECT * FROM tokens WHERE isCustom = 1")
    suspend fun getAllCustom(): List<TokenEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(token: TokenEntity)

    @Query("DELETE FROM tokens WHERE contractAddress = :address AND blockchain = :blockchain")
    suspend fun delete(address: String, blockchain: String)

    @Query("UPDATE tokens SET isHidden = :hidden WHERE contractAddress = :address AND blockchain = :blockchain")
    suspend fun setHidden(address: String, blockchain: String, hidden: Boolean)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE fromAddress = :address OR toAddress = :address ORDER BY timestamp DESC")
    fun observeByAddress(address: String): Flow<List<TransactionEntity>>

    @Query("SELECT hash FROM transactions WHERE hash = :hash LIMIT 1")
    suspend fun getHash(hash: String): String?

    @Query("SELECT * FROM transactions WHERE hash = :hash LIMIT 1")
    suspend fun getByHash(hash: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(transaction: TransactionEntity): Long

    @Query("UPDATE transactions SET status = :status, confirmations = :conf WHERE hash = :hash")
    suspend fun updateStatus(hash: String, status: String, conf: Int)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("DELETE FROM transactions WHERE blockchain = :blockchain")
    suspend fun deleteByBlockchain(blockchain: String)

    /** Réceptions d'une monnaie enregistrées depuis [since] — sert à savoir si
     *  une hausse de solde a déjà été expliquée par une transaction connue. */
    @Query("SELECT COUNT(*) FROM transactions WHERE tokenSymbol = :symbol AND type = 'received' AND timestamp >= :since")
    suspend fun countReceivedSince(symbol: String, since: Long): Int

    /**
     * Échanges non encore aboutis, du plus récent au plus ancien.
     *
     * Le suivi d'un swap vivait uniquement dans le `viewModelScope` de l'écran
     * Swap : quitter l'écran l'annulait, donc plus de notification de fin, plus
     * de badge sur la monnaie reçue. Cette requête permet à un worker de
     * reprendre le suivi depuis la base, indépendamment de l'écran.
     */
    @Query("SELECT * FROM transactions WHERE type = 'swap' AND status = 'pending' ORDER BY timestamp DESC")
    suspend fun getPendingSwaps(): List<TransactionEntity>
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY name ASC")
    fun observeAll(): Flow<List<ContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: ContactEntity)

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface PriceAlertDao {
    @Query("SELECT * FROM price_alerts")
    fun observeAll(): Flow<List<PriceAlertEntity>>

    @Query("SELECT * FROM price_alerts WHERE isActive = 1")
    fun observeActive(): Flow<List<PriceAlertEntity>>

    @Query("SELECT * FROM price_alerts WHERE isActive = 1")
    suspend fun getActiveOnce(): List<PriceAlertEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alert: PriceAlertEntity)

    @Query("UPDATE price_alerts SET isActive = :active WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean)

    @Query("DELETE FROM price_alerts WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface PendingSendDao {
    @Query("SELECT * FROM pending_sends ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<PendingSendEntity>>

    @Query("SELECT COUNT(*) FROM pending_sends WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM pending_sends WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPending(): List<PendingSendEntity>

    /**
     * Une intention IDENTIQUE (même chaîne, même destinataire, même montant)
     * attend-elle déjà son départ ?
     *
     * Sert au garde anti-doublon de l'écran d'envoi : sans lui, chaque appui
     * sur « Envoyer » hors ligne insérait une NOUVELLE ligne, et le retour du
     * réseau diffusait autant de transactions RÉELLES que d'appuis.
     */
    @Query(
        "SELECT COUNT(*) FROM pending_sends WHERE status = 'PENDING' " +
            "AND chain = :chain AND toAddress = :toAddress AND amount = :amount"
    )
    suspend fun countSamePending(chain: String, toAddress: String, amount: String): Int

    @Insert
    suspend fun insert(item: PendingSendEntity): Long

    @Query("UPDATE pending_sends SET status = :status, txHash = :txHash, lastError = :error, attempts = :attempts WHERE id = :id")
    suspend fun updateResult(id: Long, status: String, txHash: String?, error: String?, attempts: Int)

    @Query("DELETE FROM pending_sends WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM pending_sends WHERE status = 'SENT'")
    suspend fun clearSent()

    @Query("DELETE FROM pending_sends")
    suspend fun deleteAll()
}
