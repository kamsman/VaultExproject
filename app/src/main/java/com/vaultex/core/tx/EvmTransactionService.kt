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
        passphrase: String,
        toAddress: String,
        amountWei: BigInteger,
        gasPrice: BigInteger,
        gasLimit: BigInteger,
        nonce: BigInteger,
        chainId: Long,
        coinType: Int = 60
    ): String {
        val credentials = Credentials.create(deriveKeyPair(mnemonic, passphrase, coinType))
        val tx = RawTransaction.createEtherTransaction(nonce, gasPrice, gasLimit, toAddress, amountWei)
        return Numeric.toHexString(TransactionEncoder.signMessage(tx, chainId, credentials))
    }

    fun signErc20Transfer(
        mnemonic: String,
        passphrase: String,
        contractAddress: String,
        toAddress: String,
        amountWei: BigInteger,
        gasPrice: BigInteger,
        gasLimit: BigInteger,
        nonce: BigInteger,
        chainId: Long,
        coinType: Int = 60
    ): String {
        val credentials = Credentials.create(deriveKeyPair(mnemonic, passphrase, coinType))
        val tx = RawTransaction.createTransaction(
            nonce, gasPrice, gasLimit, contractAddress, buildErc20Data(toAddress, amountWei)
        )
        return Numeric.toHexString(TransactionEncoder.signMessage(tx, chainId, credentials))
    }

    /**
     * Signe un APPEL DE CONTRAT quelconque : l'appelant fournit la
     * destination et les données déjà encodées.
     *
     * ═══════════════════════════════════════════════════════════════════
     * CETTE FONCTION NE SAIT PAS CE QU'ELLE SIGNE
     * ═══════════════════════════════════════════════════════════════════
     *
     * Les autres signataires de ce fichier construisent eux-mêmes leurs
     * données : un virement, un transfert de jeton, rien d'autre. Leur
     * portée est bornée par leur propre code.
     *
     * Celui-ci signe ce qu'on lui donne. Les octets viennent d'un service
     * distant, et ils peuvent demander n'importe quoi au contrat visé —
     * y compris de vider une allocation. La vérification n'est donc pas ici
     * et ne peut pas y être : elle appartient à l'appelant, qui doit borner
     * le montant, le destinataire et la perte acceptable AVANT d'arriver
     * jusqu'ici.
     *
     * Voir EchangeSurPlaceUseCase, qui est le seul appelant et qui porte
     * ces garde-fous.
     * ═══════════════════════════════════════════════════════════════════
     */
    fun signContractCall(
        mnemonic: String,
        passphrase: String,
        toAddress: String,
        data: String,
        valueWei: BigInteger,
        gasPrice: BigInteger,
        gasLimit: BigInteger,
        nonce: BigInteger,
        chainId: Long,
        coinType: Int = 60
    ): String {
        val credentials = Credentials.create(deriveKeyPair(mnemonic, passphrase, coinType))
        val tx = RawTransaction.createTransaction(
            nonce, gasPrice, gasLimit, toAddress, valueWei, data
        )
        return Numeric.toHexString(TransactionEncoder.signMessage(tx, chainId, credentials))
    }

    // ─── EIP-1559 (type-2) — Ethereum mainnet ────────────────────────
    // Uses Credentials.signMessage(tx, credentials) which correctly encodes type-2
    // transactions (v = 0 or 1, no EIP-155 modification, chainId embedded in tx).

    fun signTransactionEip1559(
        mnemonic: String,
        passphrase: String,
        toAddress: String,
        amountWei: BigInteger,
        maxPriorityFeePerGas: BigInteger,
        maxFeePerGas: BigInteger,
        gasLimit: BigInteger,
        nonce: BigInteger,
        chainId: Long,
        coinType: Int = 60
    ): String {
        val credentials = Credentials.create(deriveKeyPair(mnemonic, passphrase, coinType))
        val tx = RawTransaction.createTransaction(
            chainId, nonce, gasLimit, toAddress, amountWei, "",
            maxPriorityFeePerGas, maxFeePerGas
        )
        return Numeric.toHexString(TransactionEncoder.signMessage(tx, credentials))
    }

    fun signErc20TransferEip1559(
        mnemonic: String,
        passphrase: String,
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
        val credentials = Credentials.create(deriveKeyPair(mnemonic, passphrase, coinType))
        val tx = RawTransaction.createTransaction(
            chainId, nonce, gasLimit, contractAddress, BigInteger.ZERO,
            buildErc20Data(toAddress, amountWei), maxPriorityFeePerGas, maxFeePerGas
        )
        return Numeric.toHexString(TransactionEncoder.signMessage(tx, credentials))
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private fun deriveKeyPair(mnemonic: String, passphrase: String, coinType: Int): ECKeyPair {
        val seed = MnemonicUtils.generateSeed(mnemonic.trim(), passphrase)
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
