package com.vaultex.data.local.dao

import androidx.room.*
import com.vaultex.data.local.entity.AccountEntity

@Dao
interface AccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accounts: List<AccountEntity>)

    @Query("SELECT * FROM accounts WHERE walletId = :walletId")
    suspend fun getByWallet(walletId: String): List<AccountEntity>

    @Query("DELETE FROM accounts WHERE walletId = :walletId")
    suspend fun deleteByWallet(walletId: String)
}
