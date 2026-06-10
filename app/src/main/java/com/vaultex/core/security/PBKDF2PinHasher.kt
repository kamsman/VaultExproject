package com.vaultex.core.security

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import android.util.Base64

object PBKDF2PinHasher {
    private const val ITERATIONS = 100_000
    private const val KEY_LENGTH = 256
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val SALT_SIZE = 32

    fun hashPin(pin: String): String {
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(pin.toCharArray(), salt)
        val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val hashB64 = Base64.encodeToString(hash, Base64.NO_WRAP)
        return "$saltB64:$hashB64"
    }

    fun verifyPin(pin: String, stored: String): Boolean {
        return try {
            val parts = stored.split(":")
            if (parts.size != 2) return false
            val salt = Base64.decode(parts[0], Base64.NO_WRAP)
            val expectedHash = Base64.decode(parts[1], Base64.NO_WRAP)
            val actualHash = pbkdf2(pin.toCharArray(), salt)
            actualHash.contentEquals(expectedHash)
        } catch (e: Exception) {
            false
        }
    }

    private fun pbkdf2(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH)
        return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
    }
}
