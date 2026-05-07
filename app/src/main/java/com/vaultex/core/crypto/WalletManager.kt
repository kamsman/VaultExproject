package com.vaultex.core.crypto

import org.web3j.crypto.Credentials
import org.web3j.crypto.Keys

object WalletManager {

    fun createWallet(): Credentials {
        val keyPair = Keys.createEcKeyPair()
        return Credentials.create(keyPair)
    }

    fun getAddress(credentials: Credentials): String {
        return credentials.address
    }

    fun getPrivateKey(credentials: Credentials): String {
        return credentials.ecKeyPair.privateKey.toString(16)
    }
}