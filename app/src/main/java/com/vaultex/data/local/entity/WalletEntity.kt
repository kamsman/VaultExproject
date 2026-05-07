package com.vaultex.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wallets")
data class WalletEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isActive: Boolean,
    val createdAt: Long
)

@Entity(tableName = "accounts", primaryKeys = ["walletId", "blockchain"])
data class AccountEntity(
    val walletId: String,
    val blockchain: String,
    val address: String,
    val publicKey: String,
    val derivationPath: String,
    val addressIndex: Int
)

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

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val id: String,
    val name: String,
    val addressesJson: String,
    val notes: String?,
    val avatarColor: Int
)

@Entity(tableName = "price_alerts")
data class PriceAlertEntity(
    @PrimaryKey val id: String,
    val tokenSymbol: String,
    val condition: String,
    val targetPrice: String,
    val isActive: Boolean
)
