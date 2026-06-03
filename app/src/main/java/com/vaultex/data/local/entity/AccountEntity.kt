package com.vaultex.data.local.entity

import androidx.room.Entity

@Entity(tableName = "accounts", primaryKeys = ["walletId", "blockchain"])
data class AccountEntity(
    val walletId: String,
    val blockchain: String,
    val address: String,
    val publicKey: String,
    val derivationPath: String,
    val addressIndex: Int
)
