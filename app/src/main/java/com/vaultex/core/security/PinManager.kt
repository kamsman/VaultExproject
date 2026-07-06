package com.vaultex.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject

/**
 * Gestion sécurisée du PIN — hash PBKDF2 avec salt.
 * Le PIN n'est JAMAIS stocké en clair.
 *
 * Bloquage après 5 tentatives ratées avec délai exponentiel.
 * L'état de lockout est persisté dans SecureStorage pour survivre aux relances.
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

    /** Un PIN principal est-il déjà défini ? (distingue création vs changement). */
    fun hasPin(): Boolean = !secureStorage.getPinHash().isNullOrBlank()

    fun setPin(pin: String, isPanic: Boolean = false) {
        require(pin.length == 6 && pin.all { it.isDigit() }) { "PIN doit être 6 chiffres" }
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(pin, salt)
        val combined = toHex(salt) + ":" + toHex(hash)
        if (isPanic) secureStorage.savePanicPin(combined) else secureStorage.savePin(combined)
    }

    fun verifyPin(pin: String): PinVerificationResult {
        val now = System.currentTimeMillis()

        // Charger l'état persisté (survit aux kills de process)
        val lockedUntil = secureStorage.getPinLockedUntil()
        var failedAttempts = secureStorage.getFailedPinAttempts()

        if (now < lockedUntil) {
            return PinVerificationResult.Locked((lockedUntil - now) / 1000)
        }

        val storedNormal = secureStorage.getPinHash()
        if (storedNormal != null && verifyAgainst(pin, storedNormal)) {
            secureStorage.clearPinLockout()
            return PinVerificationResult.Valid
        }

        val storedPanic = secureStorage.getPanicPinHash()
        if (storedPanic != null && verifyAgainst(pin, storedPanic)) {
            secureStorage.nukeAllData()
            return PinVerificationResult.PanicTriggered
        }

        failedAttempts++
        secureStorage.saveFailedPinAttempts(failedAttempts)

        if (failedAttempts >= MAX_ATTEMPTS) {
            val penaltyMinutes = (1 shl (failedAttempts - MAX_ATTEMPTS).coerceAtMost(10))
            val newLockedUntil = now + penaltyMinutes * 60_000L
            secureStorage.savePinLockedUntil(newLockedUntil)
            return PinVerificationResult.Locked(penaltyMinutes * 60L)
        }

        return PinVerificationResult.Invalid(MAX_ATTEMPTS - failedAttempts)
    }

    private fun verifyAgainst(pin: String, stored: String): Boolean {
        val parts = stored.split(":")
        if (parts.size != 2) return false
        val salt = fromHex(parts[0])
        val expected = fromHex(parts[1])
        return MessageDigest.isEqual(expected, pbkdf2(pin, salt))
    }

    private fun pbkdf2(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, HASH_LENGTH)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    private fun fromHex(hex: String): ByteArray = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

sealed class PinVerificationResult {
    data object Valid : PinVerificationResult()
    data class Invalid(val remainingAttempts: Int) : PinVerificationResult()
    data class Locked(val unlockInSeconds: Long) : PinVerificationResult()
    data object PanicTriggered : PinVerificationResult()
}
