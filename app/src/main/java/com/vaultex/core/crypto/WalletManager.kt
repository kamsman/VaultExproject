package com.vaultex.core.crypto

import org.bitcoinj.core.SegwitAddress
import org.bitcoinj.crypto.ChildNumber
import org.bitcoinj.crypto.HDKeyDerivation
import org.bitcoinj.params.MainNetParams
import org.web3j.crypto.Bip32ECKeyPair
import org.web3j.crypto.Credentials
import org.web3j.crypto.MnemonicUtils
import org.web3j.utils.Numeric
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Génération BIP39 + dérivation HD wallet BIP44/SLIP-10 pour 5 blockchains.
 * Toutes les adresses sont déterministes depuis la même seed.
 */
object WalletManager {

    data class WalletAddresses(
        val btc: String,
        val eth: String,
        val bnb: String,
        val sol: String,
        val trx: String
    )

    /** Génère 12 mots BIP39 (128 bits d'entropie via SecureRandom). */
    fun generateMnemonic(): List<String> {
        val entropy = ByteArray(16)
        SecureRandom().nextBytes(entropy)
        return MnemonicUtils.generateMnemonic(entropy).split(" ")
    }

    /** Valide une phrase mnémonique BIP39. */
    fun validateMnemonic(phrase: String): Boolean = try {
        MnemonicUtils.validateMnemonic(phrase.trim())
    } catch (_: Exception) {
        false
    }

    /**
     * Dérive les adresses des 5 blockchains à partir d'une mnémonique et
     * d'une passphrase BIP39 optionnelle (« 13e mot »). Passphrase vide
     * = BIP39 standard. CPU-intensif — appeler sur Dispatchers.IO.
     */
    fun deriveAddresses(mnemonic: String, passphrase: String): WalletAddresses {
        val seed = MnemonicUtils.generateSeed(mnemonic.trim(), passphrase)
        return WalletAddresses(
            btc = deriveBtcAddress(seed),
            eth = deriveEvmAddress(seed, coinType = 60),
            bnb = deriveEvmAddress(seed, coinType = 60), // BSC partage le coin_type EVM
            sol = deriveSolanaAddress(seed),
            trx = deriveTronAddress(seed)
        )
    }

    // EVM (ETH / BNB): m/44'/coinType'/0'/0/0
    private fun deriveEvmAddress(seed: ByteArray, coinType: Int): String {
        val master = Bip32ECKeyPair.generateKeyPair(seed)
        val path = intArrayOf(
            44 or Bip32ECKeyPair.HARDENED_BIT,
            coinType or Bip32ECKeyPair.HARDENED_BIT,
            0 or Bip32ECKeyPair.HARDENED_BIT,
            0, 0
        )
        return Credentials.create(Bip32ECKeyPair.deriveKeyPair(master, path)).address
    }

    // Bitcoin: m/84'/0'/0'/0/0 → P2WPKH native SegWit (bech32, bc1…)
    private fun deriveBtcAddress(seed: ByteArray): String {
        val path = listOf(
            ChildNumber(84, true), ChildNumber(0, true),
            ChildNumber(0, true),  ChildNumber(0, false),
            ChildNumber(0, false)
        )
        val key = path.fold(HDKeyDerivation.createMasterPrivateKey(seed)) { k, child ->
            HDKeyDerivation.deriveChildKey(k, child)
        }
        return SegwitAddress.fromKey(MainNetParams.get(), key).toString()
    }

    // Solana: m/44'/501'/0'/0' → ed25519 via SLIP-10
    private fun deriveSolanaAddress(seed: ByteArray): String {
        // Int.MIN_VALUE = 0x80000000 signé = bit hardened BIP44
        val path = intArrayOf(
            44 or Int.MIN_VALUE,
            501 or Int.MIN_VALUE,
            0 or Int.MIN_VALUE,
            0 or Int.MIN_VALUE
        )
        val derived = Slip10.deriveEd25519Key(seed, path)
        return Base58.encode(Ed25519Utils.publicKeyFromPrivate(derived.privateKey))
    }

    // Tron: m/44'/195'/0'/0/0 → keccak hash + préfixe 0x41 + Base58Check
    private fun deriveTronAddress(seed: ByteArray): String {
        val master = Bip32ECKeyPair.generateKeyPair(seed)
        val path = intArrayOf(
            44 or Bip32ECKeyPair.HARDENED_BIT,
            195 or Bip32ECKeyPair.HARDENED_BIT,
            0 or Bip32ECKeyPair.HARDENED_BIT,
            0, 0
        )
        val addrBytes = Numeric.hexStringToByteArray(
            Credentials.create(Bip32ECKeyPair.deriveKeyPair(master, path)).address.removePrefix("0x")
        )
        val raw = ByteArray(21).also {
            it[0] = 0x41.toByte()
            System.arraycopy(addrBytes, 0, it, 1, 20)
        }
        val checksum = sha256d(raw).copyOfRange(0, 4)
        return Base58.encode(raw + checksum)
    }

    private fun sha256d(data: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(md.digest(data))
    }

    /**
     * Exporte la clé privée d'une chaîne, au format attendu par les wallets
     * de référence pour l'import :
     *  - BTC : WIF (m/84'/0'/0'/0/0) — Electrum/BlueWallet
     *  - ETH/BNB : hex 0x… (m/44'/60'/0'/0/0) — MetaMask/Trust
     *  - TRX : hex sans 0x (m/44'/195'/0'/0/0) — TronLink
     *  - SOL : Base58 des 64 octets privé+public (m/44'/501'/0'/0') — Phantom
     * CPU-intensif — appeler hors du thread UI. Ne JAMAIS journaliser le retour.
     */
    fun exportPrivateKey(mnemonic: String, passphrase: String, chain: String): String {
        val seed = MnemonicUtils.generateSeed(mnemonic.trim(), passphrase)
        return when (chain.uppercase()) {
            "BTC" -> {
                val path = listOf(
                    ChildNumber(84, true), ChildNumber(0, true),
                    ChildNumber(0, true), ChildNumber(0, false), ChildNumber(0, false)
                )
                val key = path.fold(HDKeyDerivation.createMasterPrivateKey(seed)) { k, child ->
                    HDKeyDerivation.deriveChildKey(k, child)
                }
                key.getPrivateKeyAsWiF(MainNetParams.get())
            }
            "ETH", "BNB" -> "0x" + evmPrivHex(seed, 60)
            "TRX" -> evmPrivHex(seed, 195)
            "SOL" -> {
                val path = intArrayOf(
                    44 or Int.MIN_VALUE, 501 or Int.MIN_VALUE,
                    0 or Int.MIN_VALUE, 0 or Int.MIN_VALUE
                )
                val derived = Slip10.deriveEd25519Key(seed, path)
                val pub = Ed25519Utils.publicKeyFromPrivate(derived.privateKey)
                Base58.encode(derived.privateKey + pub)
            }
            else -> throw IllegalArgumentException("Chaîne inconnue : $chain")
        }
    }

    /** Clé privée secp256k1 en hex zero-padded (64 caractères), sans préfixe. */
    private fun evmPrivHex(seed: ByteArray, coinType: Int): String {
        val master = Bip32ECKeyPair.generateKeyPair(seed)
        val path = intArrayOf(
            44 or Bip32ECKeyPair.HARDENED_BIT,
            coinType or Bip32ECKeyPair.HARDENED_BIT,
            0 or Bip32ECKeyPair.HARDENED_BIT,
            0, 0
        )
        return Numeric.toHexStringNoPrefixZeroPadded(
            Bip32ECKeyPair.deriveKeyPair(master, path).privateKey, 64
        )
    }
}
