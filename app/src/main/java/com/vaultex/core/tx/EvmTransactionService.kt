package com.vaultex.core.tx

import org.web3j.crypto.Bip32ECKeyPair
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.MnemonicUtils
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.utils.Numeric
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EvmTransactionService @Inject constructor() {

    fun signTransaction(
        mnemonic: String,
        toAddress: String,
        amountWei: BigInteger,
        gasPrice: BigInteger,
        gasLimit: BigInteger,
        nonce: BigInteger,
        chainId: Long,
        coinType: Int = 60
    ): String {
        val seed = MnemonicUtils.generateSeed(mnemonic.trim(), "")
        val master = Bip32ECKeyPair.generateKeyPair(seed)
        val path = intArrayOf(
            44 or Bip32ECKeyPair.HARDENED_BIT,
            coinType or Bip32ECKeyPair.HARDENED_BIT,
            0 or Bip32ECKeyPair.HARDENED_BIT,
            0, 0
        )
        val keyPair = Bip32ECKeyPair.deriveKeyPair(master, path)
        val ecKeyPair = ECKeyPair.create(keyPair.privateKey)

        val tx = RawTransaction.createEtherTransaction(nonce, gasPrice, gasLimit, toAddress, amountWei)
        val signed = TransactionEncoder.signMessage(tx, chainId, ecKeyPair)
        return Numeric.toHexString(signed)
    }

    fun signErc20Transfer(
        mnemonic: String,
        contractAddress: String,
        toAddress: String,
        amountWei: BigInteger,
        gasPrice: BigInteger,
        gasLimit: BigInteger,
        nonce: BigInteger,
        chainId: Long,
        coinType: Int = 60
    ): String {
        val seed = MnemonicUtils.generateSeed(mnemonic.trim(), "")
        val master = Bip32ECKeyPair.generateKeyPair(seed)
        val path = intArrayOf(
            44 or Bip32ECKeyPair.HARDENED_BIT,
            coinType or Bip32ECKeyPair.HARDENED_BIT,
            0 or Bip32ECKeyPair.HARDENED_BIT,
            0, 0
        )
        val keyPair = Bip32ECKeyPair.deriveKeyPair(master, path)
        val ecKeyPair = ECKeyPair.create(keyPair.privateKey)

        val transferData = buildErc20TransferData(toAddress, amountWei)
        val tx = RawTransaction.createTransaction(nonce, gasPrice, gasLimit, contractAddress, transferData)
        val signed = TransactionEncoder.signMessage(tx, chainId, ecKeyPair)
        return Numeric.toHexString(signed)
    }

    private fun buildErc20TransferData(to: String, amount: BigInteger): String {
        val methodId = "a9059cbb"
        val paddedTo = to.removePrefix("0x").padStart(64, '0')
        val paddedAmount = amount.toString(16).padStart(64, '0')
        return "0x$methodId$paddedTo$paddedAmount"
    }
}
