package com.vaultex.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.vaultex.data.local.dao.*
import com.vaultex.data.local.entity.*

@Database(
    entities = [
        WalletEntity::class,
        AccountEntity::class,
        TokenEntity::class,
        TransactionEntity::class,
        ContactEntity::class,
        PriceAlertEntity::class,
        PendingSendEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class VaultExDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao
    abstract fun accountDao(): AccountDao
    abstract fun tokenDao(): TokenDao
    abstract fun transactionDao(): TransactionDao
    abstract fun contactDao(): ContactDao
    abstract fun priceAlertDao(): PriceAlertDao
    abstract fun pendingSendDao(): PendingSendDao

    companion object {
        const val DATABASE_NAME = "vaultex.db"
    }
}
