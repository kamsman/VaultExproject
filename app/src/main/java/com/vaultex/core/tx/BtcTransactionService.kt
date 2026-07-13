package com.vaultex.core.tx

import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.core.SegwitAddress
import org.bitcoinj.core.Transaction
import org.bitcoinj.core.TransactionInput
import org.bitcoinj.core.TransactionOutPoint
import org.bitcoinj.core.TransactionWitness
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
        utxos: List<Utxo>,
        serviceFeeSatoshi: Long = 0L,
        serviceFeeAddress: String? = null
    ): ByteArray {
        val seed = MnemonicUtils.generateSeed(mnemonic.trim(), passphrase)
        val ecKey = deriveKey(seed)

        // Frais de service VaultEx ajouté comme 2e sortie DANS la même tx (coût
        // quasi nul). Ignoré si sous le seuil de poussière (546 sat) ou sans adresse.
        val svcFee = if (serviceFeeAddress != null && serviceFeeSatoshi > 546) serviceFeeSatoshi else 0L

        // Select UTXOs covering amount + network fee + service fee
        val selected = mutableListOf<Utxo>()
        var inputTotal = 0L
        for (utxo in utxos) {
            selected.add(utxo)
            inputTotal += utxo.valueSatoshi
            if (inputTotal >= amountSatoshi + feeSatoshi + svcFee) break
        }

        val tx = Transaction(params)

        // Add outputs BEFORE signing (signature commits to outputs)
        val recipientAddr = Address.fromString(params, toAddress)
        tx.addOutput(Coin.valueOf(amountSatoshi), recipientAddr)

        if (svcFee > 0L) {
            tx.addOutput(Coin.valueOf(svcFee), Address.fromString(params, serviceFeeAddress!!))
        }

        val change = inputTotal - amountSatoshi - feeSatoshi - svcFee
        if (change > 546) {
            tx.addOutput(Coin.valueOf(change), SegwitAddress.fromKey(params, ecKey))
        }

        // 1) Ajouter TOUTES les entrées (non signées) AVANT de signer quoi que
        // ce soit. BIP143 : le sighash de CHAQUE entrée engage hashPrevouts /
        // hashSequence = la liste de TOUTES les entrées. L'ancien code utilisait
        // addSignedInput (signe à l'ajout) : avec ≥ 2 UTXO, la signature de
        // l'entrée 0 devenait invalide dès l'ajout de l'entrée 1 → rejet nœud
        // « mempool-script-verify-flag-failed (Signature must be zero…), input 0 ».
        // Un envoi à 1 seul UTXO passait, ce qui masquait le bug.
        for (utxo in selected) {
            // Blockstream donne le txid en ordre d'AFFICHAGE (hex). Sha256Hash.wrap
            // le stocke tel quel et TransactionOutPoint le renverse lui-même en
            // petit-boutiste pour le fil réseau. L'ancien reverse() MANUEL doublait
            // l'inversion → outpoint erroné → « bad-txns-inputs-missingorspent ».
            val txHash = org.bitcoinj.core.Sha256Hash.wrap(utxo.txHash)
            val outPoint = TransactionOutPoint(params, utxo.outputIndex.toLong(), txHash)
            tx.addInput(TransactionInput(params, tx, ByteArray(0), outPoint, Coin.valueOf(utxo.valueSatoshi)))
        }

        // 2) Signer chaque entrée maintenant que la transaction est COMPLÈTE.
        // scriptCode BIP143 d'un P2WPKH = forme P2PKH de la clé — exactement ce
        // que bitcoinj fait en interne dans addSignedInput ; le scriptSig reste
        // vide (déjà posé à l'étape 1), la signature va dans le témoin (witness).
        val scriptCode = ScriptBuilder.createP2PKHOutputScript(ecKey)
        selected.forEachIndexed { index, utxo ->
            val sig = tx.calculateWitnessSignature(
                index, ecKey, scriptCode, Coin.valueOf(utxo.valueSatoshi),
                Transaction.SigHash.ALL, false
            )
            tx.getInput(index.toLong()).witness = TransactionWitness.redeemP2WPKH(sig, ecKey)
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
