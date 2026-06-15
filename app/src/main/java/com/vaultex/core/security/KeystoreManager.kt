package com.vaultex.core.security

import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
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

    /**
     * Génère ou récupère la master key dans le Keystore.
     * Si l'appareil supporte StrongBox (Pixel 3+, Samsung S20+), elle y est stockée.
     */
    private fun getOrCreateMasterKey(requireBiometric: Boolean = false): SecretKey {
        val existingKey = keyStore.getKey(MASTER_KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) return existingKey

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)

        // Demander l'auth biométrique pour usage de la clé (sensitive data)
        if (requireBiometric) {
            builder.setUserAuthenticationRequired(true)
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            // P4 : invalide la clé si une nouvelle empreinte est enrôlée
            // (empêche un attaquant ayant ajouté sa biométrie de l'utiliser).
            builder.setInvalidatedByBiometricEnrollment(true)
        }

        // Tente StrongBox si disponible (Android 9+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            try {
                builder.setIsStrongBoxBacked(true)
            } catch (e: Exception) {
                // Fallback TEE
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

        val iv = cipher.iv  // 12 bytes générés automatiquement
        val ciphertext = cipher.doFinal(plaintext)

        return iv + ciphertext
    }

    /**
     * Déchiffre des données AES-256-GCM.
     */
    fun decrypt(encrypted: ByteArray): ByteArray {
        require(encrypted.size > GCM_IV_LENGTH) { "Données chiffrées trop courtes" }
        val key = getOrCreateMasterKey()

        val iv = encrypted.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = encrypted.copyOfRange(GCM_IV_LENGTH, encrypted.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        return cipher.doFinal(ciphertext)
    }

    /**
     * Détruit la master key — utilisé pour le reset complet du wallet (ex: PIN de panique).
     */
    fun destroyMasterKey() {
        if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
            keyStore.deleteEntry(MASTER_KEY_ALIAS)
        }
    }

    /** Détection réelle du module hardware StrongBox (m-03). */
    fun isStrongBoxAvailable(context: android.content.Context): Boolean {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
    }
}
