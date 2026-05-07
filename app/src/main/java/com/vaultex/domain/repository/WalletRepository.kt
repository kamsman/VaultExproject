package com.vaultex.domain.repository

import org.web3j.crypto.Credentials

interface WalletRepository {

    fun createWallet(): Credentials

    fun getAddress(credentials: Credentials): String

    fun getPrivateKey(credentials: Credentials): String
}