package com.vaultex.data.local.dao

import androidx.room.*
import com.vaultex.data.local.entity.WalletEntity
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
