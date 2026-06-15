package com.vaultex.data.repository

import com.vaultex.core.crypto.WalletManager
import com.vaultex.domain.repository.WalletRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class WalletRepositoryImpl @Inject constructor() : WalletRepository {

    override suspend fun deriveAddresses(mnemonic: String, passphrase: String): WalletManager.WalletAddresses =
        withContext(Dispatchers.Default) {
            WalletManager.deriveAddresses(mnemonic, passphrase)
        }

    override fun validateMnemonic(phrase: String): Boolean =
        WalletManager.validateMnemonic(phrase)
}
