package com.vaultex.core.validation

import com.vaultex.core.crypto.Base58
import java.security.MessageDigest

object AddressValidator {

    fun isValid(address: String, chain: String): Boolean = when (chain) {
        "ETH", "BNB", "USDT-ETH", "USDT-BNB" -> isValidEvm(address)
        "BTC"        -> isValidBtc(address)
        "TRX", "USDT" -> isValidTron(address)
        "SOL"        -> isValidSolana(address)
        else         -> address.length >= 26
    }

    fun isValidEvm(address: String): Boolean =
        address.matches(Regex("^0x[0-9a-fA-F]{40}$"))

    fun isValidBtc(address: String): Boolean =
        address.matches(Regex("^(1[a-km-zA-HJ-NP-Z1-9]{25,34}|3[a-km-zA-HJ-NP-Z1-9]{25,34}|bc1[a-z0-9]{39,59})$"))

    fun isValidTron(address: String): Boolean {
        if (!address.startsWith("T") || address.length != 34) return false
        return try {
            val decoded = Base58.decode(address)
            if (decoded.size != 25 || decoded[0] != 0x41.toByte()) return false
            val payload = decoded.copyOfRange(0, 21)
            val checksum = decoded.copyOfRange(21, 25)
            sha256d(payload).copyOfRange(0, 4).contentEquals(checksum)
        } catch (_: Exception) { false }
    }

    fun isValidSolana(address: String): Boolean =
        address.matches(Regex("^[1-9A-HJ-NP-Za-km-z]{32,44}$"))

    private fun sha256d(data: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(md.digest(data))
    }
}
