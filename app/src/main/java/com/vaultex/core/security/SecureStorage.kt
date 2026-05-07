package com.vaultex.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stockage sécurisé pour mnemonique chiffrée + métadonnées wallet.
 * Combine EncryptedSharedPreferences (Jetpack) + KeystoreManager personnalisé.
 *
 * Double couche de chiffrement :
 * 1. La mnémonique est chiffrée avec KeystoreManager (AES-256-GCM en TEE)
 * 2. Le ciphertext résultant est stocké dans EncryptedSharedPreferences (chiffrement supplémentaire)
 */
@Singleton
class SecureStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keystoreManager: KeystoreManager
) {

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "vaultex_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * Sauvegarde la mnémonique en double-chiffrement.
     */
    fun saveMnemonic(mnemonic: String) {
        val encrypted = keystoreManager.encrypt(mnemonic.toByteArray(Charsets.UTF_8))
        val base64 = android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP)
        prefs.edit().putString(KEY_MNEMONIC, base64).apply()
    }

    /**
     * Récupère la mnémonique déchiffrée.
     */
    fun getMnemonic(): String? {
        val base64 = prefs.getString(KEY_MNEMONIC, null) ?: return null
        return try {
            val encrypted = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
            String(keystoreManager.decrypt(encrypted), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun hasMnemonic(): Boolean = prefs.contains(KEY_MNEMONIC)

    fun savePin(pinHash: String) {
        prefs.edit().putString(KEY_PIN_HASH, pinHash).apply()
    }

    fun getPinHash(): String? = prefs.getString(KEY_PIN_HASH, null)

    fun savePanicPin(pinHash: String) {
        prefs.edit().putString(KEY_PANIC_PIN_HASH, pinHash).apply()
    }

    fun getPanicPinHash(): String? = prefs.getString(KEY_PANIC_PIN_HASH, null)

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun isBiometricEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)

    fun setAutoLockMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_AUTOLOCK_MIN, minutes).apply()
    }

    fun getAutoLockMinutes(): Int = prefs.getInt(KEY_AUTOLOCK_MIN, 5)

    /**
     * RESET COMPLET — utilisé par le PIN de panique.
     * Efface mnémonique + PIN + détruit la master key.
     */
    fun nukeAllData() {
        prefs.edit().clear().apply()
        keystoreManager.destroyMasterKey()
    }

    companion object {
        private const val KEY_MNEMONIC = "encrypted_mnemonic"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PANIC_PIN_HASH = "panic_pin_hash"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_AUTOLOCK_MIN = "autolock_minutes"
    }
}
