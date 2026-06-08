package com.vaultex.di

import android.content.Context
import com.vaultex.core.security.KeystoreManager
import com.vaultex.core.security.SecureStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideKeystoreManager(): KeystoreManager = KeystoreManager()

    @Provides
    @Singleton
    fun provideSecureStorage(
        @ApplicationContext context: Context,
        keystoreManager: KeystoreManager
    ): SecureStorage = SecureStorage(context, keystoreManager)
}
