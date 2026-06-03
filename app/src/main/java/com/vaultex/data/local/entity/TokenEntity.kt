package com.vaultex.data.local.entity

import androidx.room.Entity

@Entity(tableName = "tokens", primaryKeys = ["contractAddress", "blockchain"])
data class TokenEntity(
    val contractAddress: String,
    val blockchain: String,
    val symbol: String,
    val name: String,
    val decimals: Int,
    val iconUrl: String?,
    val isCustom: Boolean,
    val isHidden: Boolean = false
)
