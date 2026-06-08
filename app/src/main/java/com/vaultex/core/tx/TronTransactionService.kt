package com.vaultex.core.tx

import org.web3j.crypto.Bip32ECKeyPair
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Hash
import org.web3j.crypto.MnemonicUtils
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import android.util.Base64
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TronTransactionService @Inject constructor() {

    fun deriveAddress(mnemonic: String): String {
        val seed = MnemonicUtils.generateSeed(mnemonic.trim(), "")
        val master = Bip32ECKeyPair.generateKeyPair(seed)
        val path = intArrayOf(
            44 or Bip32ECKeyPair.HARDENED_BIT,
            195 or Bip32ECKeyPair.HARDENED_BIT,
            0 or Bip32ECKeyPair.HARDENED_BIT,
            0, 0
        )
        val keyPair = Bip32ECKeyPair.deriveKeyPair(master, path)
        val evmAddr = Numeric.hexStringToByteArray(
            org.web3j.crypto.Credentials.create(ECKeyPair.create(keyPair.privateKey)).address.removePrefix("0x")
        )
        val raw = ByteArray(21).also {
            it[0] = 0x41.toByte()
            System.arraycopy(evmAddr, 0, it, 1, 20)
        }
        val checksum = sha256d(raw).copyOfRange(0, 4)
        return encodeBase58Check(raw + checksum)
    }

    fun signRawTransaction(mnemonic: String, rawTxHex: String): String {
        val seed = MnemonicUtils.generateSeed(mnemonic.trim(), "")
        val master = Bip32ECKeyPair.generateKeyPair(seed)
        val path = intArrayOf(
            44 or Bip32ECKeyPair.HARDENED_BIT,
            195 or Bip32ECKeyPair.HARDENED_BIT,
            0 or Bip32ECKeyPair.HARDENED_BIT,
            0, 0
        )
        val keyPair = Bip32ECKeyPair.deriveKeyPair(master, path)
        val ecKeyPair = ECKeyPair.create(keyPair.privateKey)

        val txBytes = Numeric.hexStringToByteArray(rawTxHex)
        val txHash = Hash.sha3(txBytes)
        val sigData = Sign.signMessage(txHash, ecKeyPair, false)

        val r = Numeric.toHexStringNoPrefix(sigData.r).padStart(64, '0')
        val s = Numeric.toHexStringNoPrefix(sigData.s).padStart(64, '0')
        val v = sigData.v.firstOrNull()?.toString(16)?.padStart(2, '0') ?: "1b"
        return "$r$s$v"
    }

    private fun sha256d(data: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(md.digest(data))
    }

    private fun encodeBase58Check(input: ByteArray): String {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var num = java.math.BigInteger(1, input)
        val sb = StringBuilder()
        val base = java.math.BigInteger.valueOf(58)
        while (num > java.math.BigInteger.ZERO) {
            val rem = num.mod(base).toInt()
            sb.append(alphabet[rem])
            num = num.divide(base)
        }
        input.takeWhile { it == 0.toByte() }.forEach { _ -> sb.append(alphabet[0]) }
        return sb.reverse().toString()
    }
}
