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
        /*
        ─── POURQUOI LE NOMBRE D'ITÉRATIONS EST STOCKÉ AVEC L'EMPREINTE ────
        Le format était « sel:empreinte ». Le nombre d'itérations n'y figurant
        pas, la vérification utilisait forcément la constante du code — donc
        AUGMENTER cette constante rendait FAUX tous les PIN déjà enregistrés.
        Chaque utilisateur existant se serait retrouvé enfermé dehors, sans
        aucun message compréhensible, obligé de réinstaller et de ressaisir ses
        12 mots. Les fonds ne sont pas perdus (le seed est sur papier), mais
        pour l'utilisateur ça y ressemble exactement.

        C'est le piège classique : une recommandation de durcissement
        parfaitement juste sur le principe (« passez à 300 000 itérations »)
        devient une panne massive si le paramètre n'est pas versionné avec la
        donnée. On stocke donc « itérations:sel:empreinte ».

        L'ancien format à deux champs reste accepté et se met à niveau tout
        seul à la première saisie réussie : personne n'est bloqué, et le
        renforcement se propage sans intervention.
        ───────────────────────────────────────────────────────────────────
         */
        private const val PBKDF2_ITERATIONS = 300_000

        /** Itérations des empreintes écrites avant que le format ne les porte. */
        private const val LEGACY_ITERATIONS = 100_000

        private const val SALT_LENGTH = 16
        private const val HASH_LENGTH = 256
        private const val MAX_ATTEMPTS = 5
    }

    /** Un PIN principal est-il déjà défini ? (distingue création vs changement). */
    fun hasPin(): Boolean = !secureStorage.getPinHash().isNullOrBlank()

    fun setPin(pin: String, isPanic: Boolean = false) {
        require(pin.length == 6 && pin.all { it.isDigit() }) { "PIN doit être 6 chiffres" }
        val combined = encode(pin)
        if (isPanic) secureStorage.savePanicPin(combined) else secureStorage.savePin(combined)
    }

    /** « itérations:sel:empreinte » — le paramètre voyage avec la donnée. */
    private fun encode(pin: String): String {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(pin, salt, PBKDF2_ITERATIONS)
        return "$PBKDF2_ITERATIONS:${toHex(salt)}:${toHex(hash)}"
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
            // Remise à niveau silencieuse : une empreinte à l'ancien nombre
            // d'itérations est réécrite au nouveau, maintenant qu'on tient le
            // PIN en clair et qu'il vient d'être prouvé correct. Le
            // renforcement se propage sans jamais bloquer personne.
            if (needsUpgrade(storedNormal)) {
                runCatching { secureStorage.savePin(encode(pin)) }
            }
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

    /** true si l'empreinte est à l'ancien nombre d'itérations (format 2 champs). */
    private fun needsUpgrade(stored: String): Boolean =
        (stored.split(":").firstOrNull()?.toIntOrNull() ?: 0) < PBKDF2_ITERATIONS

    private fun verifyAgainst(pin: String, stored: String): Boolean {
        val parts = stored.split(":")
        // 3 champs = format courant (itérations portées) ; 2 champs = ancien
        // format, dont les itérations valaient forcément LEGACY_ITERATIONS.
        val (iterations, saltHex, hashHex) = when (parts.size) {
            3 -> Triple(parts[0].toIntOrNull() ?: return false, parts[1], parts[2])
            2 -> Triple(LEGACY_ITERATIONS, parts[0], parts[1])
            else -> return false
        }
        return try {
            MessageDigest.isEqual(fromHex(hashHex), pbkdf2(pin, fromHex(saltHex), iterations))
        } catch (_: Exception) { false }
    }

    private fun pbkdf2(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, HASH_LENGTH)
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
