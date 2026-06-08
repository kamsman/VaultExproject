package com.vaultex.core.tx

import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.core.Transaction
import org.bitcoinj.core.TransactionOutPoint
import org.bitcoinj.core.TransactionOutput
import org.bitcoinj.crypto.ChildNumber
import org.bitcoinj.crypto.HDKeyDerivation
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.script.Script
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
        toAddress: String,
        amountSatoshi: Long,
        feeSatoshi: Long,
        utxos: List<Utxo>
    ): ByteArray {
        val seed = MnemonicUtils.generateSeed(mnemonic.trim(), "")
        val ecKey = deriveKey(seed)

        val tx = Transaction(params)
        var inputTotal = 0L

        for (utxo in utxos) {
            val txHash = org.bitcoinj.core.Sha256Hash.wrap(utxo.txHash)
            val outPoint = TransactionOutPoint(params, utxo.outputIndex.toLong(), txHash)
            tx.addInput(outPoint, Script(ByteArray(0)))
            inputTotal += utxo.valueSatoshi
            if (inputTotal >= amountSatoshi + feeSatoshi) break
        }

        val recipientAddr = Address.fromString(params, toAddress)
        tx.addOutput(Coin.valueOf(amountSatoshi), recipientAddr)

        val change = inputTotal - amountSatoshi - feeSatoshi
        if (change > 546) {
            val changeAddr = Address.fromKey(params, ecKey)
            tx.addOutput(Coin.valueOf(change), changeAddr)
        }

        tx.inputs.forEach { input ->
            val script = ScriptBuilder.createInputScript(
                tx.calculateSignature(
                    tx.inputs.indexOf(input),
                    ecKey,
                    ScriptBuilder.createOutputScript(Address.fromKey(params, ecKey)),
                    Transaction.SigHash.ALL,
                    false
                ),
                ecKey
            )
            input.scriptSig = script
        }

        return tx.bitcoinSerialize()
    }

    private fun deriveKey(seed: ByteArray): ECKey {
        val path = listOf(
            ChildNumber(44, true), ChildNumber(0, true),
            ChildNumber(0, true), ChildNumber(0, false),
            ChildNumber(0, false)
        )
        val deterministicKey = path.fold(HDKeyDerivation.createMasterPrivateKey(seed)) { k, child ->
            HDKeyDerivation.deriveChildKey(k, child)
        }
        return ECKey.fromPrivate(deterministicKey.privKeyBytes)
    }
}
