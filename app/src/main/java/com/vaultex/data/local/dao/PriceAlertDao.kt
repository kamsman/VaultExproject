package com.vaultex.data.local.dao

import androidx.room.*
import com.vaultex.data.local.entity.PriceAlertEntity
import kotlinx.coroutines.flow.Flow

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
