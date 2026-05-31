package com.vaultex.domain.repository

import com.vaultex.core.crypto.WalletManager

interface WalletRepository {
    suspend fun deriveAddresses(mnemonic: String): WalletManager.WalletAddresses
    fun validateMnemonic(phrase: String): Boolean
}
