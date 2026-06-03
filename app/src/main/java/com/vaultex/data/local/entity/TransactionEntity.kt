package com.vaultex.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val hash: String,
    val type: String,
    val blockchain: String,
    val fromAddress: String,
    val toAddress: String,
    val amount: String,
    val tokenSymbol: String,
    val fee: String,
    val status: String,
    val timestamp: Long,
    val confirmations: Int,
    val blockNumber: Long?
)
