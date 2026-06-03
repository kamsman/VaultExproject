package com.vaultex.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "price_alerts")
data class PriceAlertEntity(
    @PrimaryKey val id: String,
    val tokenSymbol: String,
    val condition: String,
    val targetPrice: String,
    val isActive: Boolean
)
