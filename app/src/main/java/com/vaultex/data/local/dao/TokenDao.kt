package com.vaultex.data.local.dao

import androidx.room.*
import com.vaultex.data.local.entity.TokenEntity
import kotlinx.coroutines.flow.Flow

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
