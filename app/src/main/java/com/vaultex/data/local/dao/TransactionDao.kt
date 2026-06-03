package com.vaultex.data.local.dao

import androidx.room.*
import com.vaultex.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE fromAddress = :address OR toAddress = :address ORDER BY timestamp DESC")
    fun observeByAddress(address: String): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity)

    @Query("UPDATE transactions SET status = :status, confirmations = :conf WHERE hash = :hash")
    suspend fun updateStatus(hash: String, status: String, conf: Int)
}
