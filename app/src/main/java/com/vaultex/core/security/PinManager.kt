package com.vaultex.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject

/**
 * Gestion sécurisée du PIN — hash PBKDF2 avec salt.
 * Le PIN n'est JAMAIS stocké en clair, même chiffré.
 *
 * Bloquage après 5 tentatives ratées avec délai exponentiel.
 */
class PinManager @Inject constructor(
    private val secureStorage: SecureStorage
) {

    companion object {
        private const val PBKDF2_ITERATIONS = 100_000
        private const val SALT_LENGTH = 16
        private const val HASH_LENGTH = 256
        private const val MAX_ATTEMPTS = 5
    }

    private var failedAttempts = 0
    private var lockedUntil = 0L

    /**
     * Définit un nouveau PIN.
     */
    fun setPin(pin: String, isPanic: Boolean = false) {
        require(pin.length == 6 && pin.all { it.isDigit() }) { "PIN doit être 6 chiffres" }
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(pin, salt)
        val combined = saltToHex(salt) + ":" + hashToHex(hash)
        if (isPanic) secureStorage.savePanicPin(combined) else secureStorage.savePin(combined)
    }

    /**
     * Vérifie un PIN saisi.
     * @return true si correct, false sinon. Lance LockoutException si compte bloqué.
     */
    fun verifyPin(pin: String): PinVerificationResult {
        val now = System.currentTimeMillis()
        if (now < lockedUntil) {
            val remainingSec = (lockedUntil - now) / 1000
            return PinVerificationResult.Locked(remainingSec)
        }

        // Vérifie d'abord le PIN normal
        val storedNormal = secureStorage.getPinHash()
        if (storedNormal != null && verifyAgainst(pin, storedNormal)) {
            failedAttempts = 0
            return PinVerificationResult.Valid
        }

        // Puis le PIN de panique
        val storedPanic = secureStorage.getPanicPinHash()
        if (storedPanic != null && verifyAgainst(pin, storedPanic)) {
            // ⚠️ Le PIN de panique efface TOUT
            secureStorage.nukeAllData()
            return PinVerificationResult.PanicTriggered
        }

        // PIN invalide
        failedAttempts++
        if (failedAttempts >= MAX_ATTEMPTS) {
            // Délai exponentiel : 2^(attempts - MAX) minutes
            val penaltyMinutes = (1 shl (failedAttempts - MAX_ATTEMPTS).coerceAtMost(10))
            lockedUntil = now + penaltyMinutes * 60_000L
            return PinVerificationResult.Locked(penaltyMinutes * 60L)
        }
        return PinVerificationResult.Invalid(MAX_ATTEMPTS - failedAttempts)
    }

    private fun verifyAgainst(pin: String, stored: String): Boolean {
        val parts = stored.split(":")
        if (parts.size != 2) return false
        val salt = hexToBytes(parts[0])
        val expectedHash = hexToBytes(parts[1])
        val computedHash = pbkdf2(pin, salt)
        return MessageDigest.isEqual(expectedHash, computedHash)
    }

    private fun pbkdf2(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, HASH_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun saltToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }
    private fun hashToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }
    private fun hexToBytes(hex: String): ByteArray =
        hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

sealed class PinVerificationResult {
    data object Valid : PinVerificationResult()
    data class Invalid(val remainingAttempts: Int) : PinVerificationResult()
    data class Locked(val unlockInSeconds: Long) : PinVerificationResult()
    data object PanicTriggered : PinVerificationResult()
}
