package com.vaultex.data.repository

import com.vaultex.domain.repository.WalletRepository
import com.vaultex.core.crypto.WalletManager
import org.web3j.crypto.Credentials
import javax.inject.Inject

class WalletRepositoryImpl @Inject constructor() : WalletRepository {

    override fun createWallet(): Credentials {
        return WalletManager.createWallet()
    }

    override fun getAddress(credentials: Credentials): String {
        return WalletManager.getAddress(credentials)
    }

    override fun getPrivateKey(credentials: Credentials): String {
        return WalletManager.getPrivateKey(credentials)
    }
}