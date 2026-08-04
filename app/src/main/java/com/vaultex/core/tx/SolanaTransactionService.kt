package com.vaultex.core.tx

import com.vaultex.core.crypto.Base58
import com.vaultex.core.crypto.Ed25519Utils
import com.vaultex.core.crypto.Slip10
import org.web3j.crypto.MnemonicUtils
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SolanaTransactionService @Inject constructor() {

    fun deriveAddress(mnemonic: String, passphrase: String): String {
        val seed = MnemonicUtils.generateSeed(mnemonic.trim(), passphrase)
        val path = intArrayOf(
            44 or Int.MIN_VALUE,
            501 or Int.MIN_VALUE,
            0 or Int.MIN_VALUE,
            0 or Int.MIN_VALUE
        )
        val derived = Slip10.deriveEd25519Key(seed, path)
        val pubKey = Ed25519Utils.publicKeyFromPrivate(derived.privateKey)
        return Base58.encode(pubKey)
    }

    fun signTransaction(mnemonic: String, passphrase: String, serializedMessage: ByteArray): ByteArray {
        val seed = MnemonicUtils.generateSeed(mnemonic.trim(), passphrase)
        val path = intArrayOf(
            44 or Int.MIN_VALUE,
            501 or Int.MIN_VALUE,
            0 or Int.MIN_VALUE,
            0 or Int.MIN_VALUE
        )
        val derived = Slip10.deriveEd25519Key(seed, path)
        return Ed25519Utils.sign(serializedMessage, derived.privateKey)
    }
}
