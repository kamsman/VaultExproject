package com.vaultex.di

import android.content.Context
import androidx.room.Room
import com.vaultex.core.security.SecureStorage
import com.vaultex.data.local.dao.*
import com.vaultex.data.local.db.VaultExDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Base Room chiffrée via SQLCipher (C-03). La clé provient des
     * préférences chiffrées (protégées par le Keystore Android).
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        secureStorage: SecureStorage
    ): VaultExDatabase {
        SQLiteDatabase.loadLibs(context)
        val factory = SupportFactory(secureStorage.getOrCreateDatabaseKey())
        return Room.databaseBuilder(context, VaultExDatabase::class.java, VaultExDatabase.DATABASE_NAME)
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides @Singleton fun provideWalletDao(db: VaultExDatabase): WalletDao = db.walletDao()
    @Provides @Singleton fun provideAccountDao(db: VaultExDatabase): AccountDao = db.accountDao()
    @Provides @Singleton fun provideTokenDao(db: VaultExDatabase): TokenDao = db.tokenDao()
    @Provides @Singleton fun provideTransactionDao(db: VaultExDatabase): TransactionDao = db.transactionDao()
    @Provides @Singleton fun provideContactDao(db: VaultExDatabase): ContactDao = db.contactDao()
    @Provides @Singleton fun providePriceAlertDao(db: VaultExDatabase): PriceAlertDao = db.priceAlertDao()
}
