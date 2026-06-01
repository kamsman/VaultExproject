package com.vaultex.di

import android.content.Context
import androidx.room.Room
import com.vaultex.data.local.dao.*
import com.vaultex.data.local.db.VaultExDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VaultExDatabase =
        Room.databaseBuilder(context, VaultExDatabase::class.java, VaultExDatabase.DATABASE_NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideWalletDao(db: VaultExDatabase): WalletDao = db.walletDao()
    @Provides fun provideAccountDao(db: VaultExDatabase): AccountDao = db.accountDao()
    @Provides fun provideTokenDao(db: VaultExDatabase): TokenDao = db.tokenDao()
    @Provides fun provideTransactionDao(db: VaultExDatabase): TransactionDao = db.transactionDao()
    @Provides fun provideContactDao(db: VaultExDatabase): ContactDao = db.contactDao()
    @Provides fun providePriceAlertDao(db: VaultExDatabase): PriceAlertDao = db.priceAlertDao()
}
