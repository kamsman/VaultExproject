package com.vaultex.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestion sécurisée des clés via Android Keystore (TEE / StrongBox si disponible).
 * Utilise AES-256-GCM pour le chiffrement authentifié de la mnémonique.
 *
 * Les clés ne quittent JAMAIS le hardware sécurisé.
 *
 * Si la clé est invalidée (changement d'empreinte, reset biométrique → KeyPermanentlyInvalidatedException),
 * la clé est recréée. La mnémonique ne peut alors plus être déchiffrée — l'utilisateur doit
 * réimporter son wallet via la phrase mnémonique.
 */
@Singleton
class KeystoreManager @Inject constructor() {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "VaultExMasterKey"
        private const val GCM_TAG_LENGTH = 128  // bits
        private const val GCM_IV_LENGTH = 12    // bytes
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun getOrCreateMasterKey(requireBiometric: Boolean = false): SecretKey {
        val existingKey = keyStore.getKey(MASTER_KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) return existingKey
        return generateMasterKey(requireBiometric)
    }

    private fun generateMasterKey(requireBiometric: Boolean): SecretKey {
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)

        if (requireBiometric) {
            builder.setUserAuthenticationRequired(true)
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        }

        // Tente StrongBox si disponible (Android 9+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            try {
                builder.setIsStrongBoxBacked(true)
            } catch (_: Exception) {
                // Fallback TEE silencieux
            }
        }

        keyGen.init(builder.build())
        return keyGen.generateKey()
    }

    /**
     * Chiffre des données avec AES-256-GCM.
     * Format de retour: [12 bytes IV][ciphertext + tag GCM]
     */
    fun encrypt(plaintext: ByteArray, requireBiometric: Boolean = false): ByteArray {
        val key = getOrCreateMasterKey(requireBiometric)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext
    }

    /**
     * Déchiffre des données AES-256-GCM.
     * Lance [KeyInvalidatedException] si la clé a été invalidée (ex: changement d'empreinte).
     */
    fun decrypt(encrypted: ByteArray): ByteArray {
        require(encrypted.size > GCM_IV_LENGTH) { "Données chiffrées trop courtes" }

        val key = try {
            getOrCreateMasterKey()
        } catch (e: KeyPermanentlyInvalidatedException) {
            // La clé a été invalidée par un changement biométrique ou un reset du Keystore.
            // Supprimer l'ancienne clé invalide et relancer avec une indication à l'utilisateur.
            destroyMasterKey()
            throw KeyInvalidatedException(
                "La clé de chiffrement a été invalidée (changement d'empreinte ou reset). " +
                "Veuillez réimporter votre wallet avec votre phrase mnémonique.", e
            )
        }

        val iv = encrypted.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = encrypted.copyOfRange(GCM_IV_LENGTH, encrypted.size)

        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            cipher.doFinal(ciphertext)
        } catch (e: KeyPermanentlyInvalidatedException) {
            destroyMasterKey()
            throw KeyInvalidatedException("Clé invalide lors du déchiffrement.", e)
        }
    }

    /**
     * Détruit la master key — utilisé pour le reset complet du wallet (ex: PIN de panique).
     */
    fun destroyMasterKey() {
        if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
            keyStore.deleteEntry(MASTER_KEY_ALIAS)
        }
    }

    fun isStrongBoxAvailable(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            try { keyStore.getEntry(MASTER_KEY_ALIAS, null); true } catch (_: Exception) { false }
        } else false
    }
}

/** Lancée quand la clé Keystore a été invalidée par le système (changement biométrique). */
class KeyInvalidatedException(message: String, cause: Throwable? = null) : Exception(message, cause)
