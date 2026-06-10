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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(token: TokenEntity)

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(transaction: TransactionEntity): Long

    @Query("UPDATE transactions SET status = :status, confirmations = :conf WHERE hash = :hash")
    suspend fun updateStatus(hash: String, status: String, conf: Int)
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
    @Query("SELECT * FROM price_alerts WHERE isActive = 1")
    fun observeActive(): Flow<List<PriceAlertEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alert: PriceAlertEntity)

    @Query("UPDATE price_alerts SET isActive = :active WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean)

    @Query("DELETE FROM price_alerts WHERE id = :id")
    suspend fun delete(id: String)
}
