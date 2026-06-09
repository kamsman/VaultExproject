package com.vaultex.core.tx

import org.web3j.crypto.Bip32ECKeyPair
import org.web3j.crypto.Credentials
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

    // ─── Legacy (type-0) — BSC and fallback ──────────────────────────

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
        val keyPair = deriveKeyPair(mnemonic, coinType)
        val tx = RawTransaction.createEtherTransaction(nonce, gasPrice, gasLimit, toAddress, amountWei)
        return Numeric.toHexString(TransactionEncoder.signMessage(tx, chainId, keyPair))
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
        val keyPair = deriveKeyPair(mnemonic, coinType)
        val tx = RawTransaction.createTransaction(
            nonce, gasPrice, gasLimit, contractAddress, buildErc20Data(toAddress, amountWei)
        )
        return Numeric.toHexString(TransactionEncoder.signMessage(tx, chainId, keyPair))
    }

    // ─── EIP-1559 (type-2) — Ethereum mainnet ────────────────────────
    // Uses Credentials.signMessage(tx, credentials) which correctly encodes type-2
    // transactions (v = 0 or 1, no EIP-155 modification, chainId embedded in tx).

    fun signTransactionEip1559(
        mnemonic: String,
        toAddress: String,
        amountWei: BigInteger,
        maxPriorityFeePerGas: BigInteger,
        maxFeePerGas: BigInteger,
        gasLimit: BigInteger,
        nonce: BigInteger,
        chainId: Long,
        coinType: Int = 60
    ): String {
        val credentials = Credentials.create(deriveKeyPair(mnemonic, coinType))
        val tx = RawTransaction.createTransaction(
            chainId, nonce, gasLimit, toAddress, amountWei, "",
            maxPriorityFeePerGas, maxFeePerGas
        )
        return Numeric.toHexString(TransactionEncoder.signMessage(tx, credentials))
    }

    fun signErc20TransferEip1559(
        mnemonic: String,
        contractAddress: String,
        toAddress: String,
        amountWei: BigInteger,
        maxPriorityFeePerGas: BigInteger,
        maxFeePerGas: BigInteger,
        gasLimit: BigInteger,
        nonce: BigInteger,
        chainId: Long,
        coinType: Int = 60
    ): String {
        val credentials = Credentials.create(deriveKeyPair(mnemonic, coinType))
        val tx = RawTransaction.createTransaction(
            chainId, nonce, gasLimit, contractAddress, BigInteger.ZERO,
            buildErc20Data(toAddress, amountWei), maxPriorityFeePerGas, maxFeePerGas
        )
        return Numeric.toHexString(TransactionEncoder.signMessage(tx, credentials))
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private fun deriveKeyPair(mnemonic: String, coinType: Int): ECKeyPair {
        val seed = MnemonicUtils.generateSeed(mnemonic.trim(), "")
        val master = Bip32ECKeyPair.generateKeyPair(seed)
        val path = intArrayOf(
            44 or Bip32ECKeyPair.HARDENED_BIT,
            coinType or Bip32ECKeyPair.HARDENED_BIT,
            0 or Bip32ECKeyPair.HARDENED_BIT,
            0, 0
        )
        return ECKeyPair.create(Bip32ECKeyPair.deriveKeyPair(master, path).privateKey)
    }

    private fun buildErc20Data(to: String, amount: BigInteger): String {
        val paddedTo = to.removePrefix("0x").padStart(64, '0')
        val paddedAmount = amount.toString(16).padStart(64, '0')
        return "0xa9059cbb$paddedTo$paddedAmount"
    }
}
