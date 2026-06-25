package com.vaultex.core.tx

import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.core.SegwitAddress
import org.bitcoinj.core.Transaction
import org.bitcoinj.core.TransactionOutPoint
import org.bitcoinj.core.Utils
import org.bitcoinj.crypto.ChildNumber
import org.bitcoinj.crypto.HDKeyDerivation
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.script.ScriptBuilder
import org.web3j.crypto.MnemonicUtils
import javax.inject.Inject
import javax.inject.Singleton

data class Utxo(val txHash: String, val outputIndex: Int, val valueSatoshi: Long)

@Singleton
class BtcTransactionService @Inject constructor() {

    private val params: NetworkParameters = MainNetParams.get()

    fun signTransaction(
        mnemonic: String,
        passphrase: String,
        toAddress: String,
        amountSatoshi: Long,
        feeSatoshi: Long,
        utxos: List<Utxo>
    ): ByteArray {
        val seed = MnemonicUtils.generateSeed(mnemonic.trim(), passphrase)
        val ecKey = deriveKey(seed)

        // Select UTXOs covering amount + fee
        val selected = mutableListOf<Utxo>()
        var inputTotal = 0L
        for (utxo in utxos) {
            selected.add(utxo)
            inputTotal += utxo.valueSatoshi
            if (inputTotal >= amountSatoshi + feeSatoshi) break
        }

        val tx = Transaction(params)

        // Add outputs BEFORE signing (signature commits to outputs)
        val recipientAddr = Address.fromString(params, toAddress)
        tx.addOutput(Coin.valueOf(amountSatoshi), recipientAddr)

        val change = inputTotal - amountSatoshi - feeSatoshi
        if (change > 546) {
            tx.addOutput(Coin.valueOf(change), SegwitAddress.fromKey(params, ecKey))
        }

        // Sign P2WPKH inputs (native SegWit, bc1…). IMPORTANT : il faut passer le
        // scriptPubKey P2WPKH pour que bitcoinj signe via le TÉMOIN (witness) avec
        // un scriptSig VIDE. Passer un script P2PKH le faisait signer en LEGACY
        // (scriptSig non vide) → transaction invalide rejetée par le réseau.
        val witnessScript = ScriptBuilder.createP2WPKHOutputScript(ecKey.pubKeyHash)
        for (utxo in selected) {
            // Blockstream returns txids in display (big-endian) order; BitcoinJ
            // TransactionOutPoint stores hashes in internal (little-endian) order.
            val hashBytes = Utils.HEX.decode(utxo.txHash)
            hashBytes.reverse()
            val txHash = org.bitcoinj.core.Sha256Hash.wrap(hashBytes)
            val outPoint = TransactionOutPoint(params, utxo.outputIndex.toLong(), txHash)
            tx.addSignedInput(outPoint, witnessScript, Coin.valueOf(utxo.valueSatoshi), ecKey, Transaction.SigHash.ALL, false)
        }

        return tx.bitcoinSerialize()
    }

    // m/84'/0'/0'/0/0 — must match WalletManager.deriveBtcAddress()
    private fun deriveKey(seed: ByteArray): ECKey {
        val path = listOf(
            ChildNumber(84, true), ChildNumber(0, true),
            ChildNumber(0, true), ChildNumber(0, false),
            ChildNumber(0, false)
        )
        val deterministicKey = path.fold(HDKeyDerivation.createMasterPrivateKey(seed)) { k, child ->
            HDKeyDerivation.deriveChildKey(k, child)
        }
        return ECKey.fromPrivate(deterministicKey.privKeyBytes)
    }
}
