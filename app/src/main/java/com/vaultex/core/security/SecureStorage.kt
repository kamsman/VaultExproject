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

    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
    }

    fun getThemeMode(): String = prefs.getString(KEY_THEME_MODE, "SYSTEM") ?: "SYSTEM"

    /**
     * Clé de chiffrement de la base SQLCipher (C-03).
     * Générée aléatoirement une fois, stockée dans les prefs chiffrées
     * (protégées par le Keystore). Retourne une copie (SQLCipher efface
     * le tableau après usage).
     */
    fun getOrCreateDatabaseKey(): ByteArray {
        val existing = prefs.getString(KEY_DB_KEY, null)
        if (existing != null) {
            return android.util.Base64.decode(existing, android.util.Base64.NO_WRAP)
        }
        val key = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_DB_KEY, android.util.Base64.encodeToString(key, android.util.Base64.NO_WRAP))
            .apply()
        return key.copyOf()
    }

    fun setBalanceHidden(hidden: Boolean) {
        prefs.edit().putBoolean(KEY_BALANCE_HIDDEN, hidden).apply()
    }

    fun isBalanceHidden(): Boolean = prefs.getBoolean(KEY_BALANCE_HIDDEN, false)

    // ──────────────────────────────────────────────────────────
    // Persistance de l'état de lockout PIN (survit aux relances de process)
    // ──────────────────────────────────────────────────────────

    fun saveFailedPinAttempts(count: Int) {
        prefs.edit().putInt(KEY_PIN_FAILED_ATTEMPTS, count).apply()
    }

    fun getFailedPinAttempts(): Int = prefs.getInt(KEY_PIN_FAILED_ATTEMPTS, 0)

    fun savePinLockedUntil(timestamp: Long) {
        prefs.edit().putLong(KEY_PIN_LOCKED_UNTIL, timestamp).apply()
    }

    fun getPinLockedUntil(): Long = prefs.getLong(KEY_PIN_LOCKED_UNTIL, 0L)

    fun clearPinLockout() {
        prefs.edit()
            .putInt(KEY_PIN_FAILED_ATTEMPTS, 0)
            .putLong(KEY_PIN_LOCKED_UNTIL, 0L)
            .apply()
    }

    /**
     * RESET COMPLET — utilisé par le PIN de panique.
     * Efface mnémonique + PIN + détruit la master key.
     */
    fun nukeAllData() {
        prefs.edit().clear().apply()
        keystoreManager.destroyMasterKey()
    }

    private val rpcPrefs: SharedPreferences by lazy { RpcPrefs.get(context) }

    /** @return false si l'URL est rejetée (non HTTPS). */
    fun setRpcUrl(chain: String, url: String): Boolean {
        if (!RpcPrefs.isValidRpcUrl(url)) return false
        rpcPrefs.edit().putString("rpc_$chain", url).apply()
        return true
    }

    fun getRpcUrl(chain: String, default: String): String =
        rpcPrefs.getString("rpc_$chain", default) ?: default

    companion object {
        private const val KEY_MNEMONIC = "encrypted_mnemonic"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PANIC_PIN_HASH = "panic_pin_hash"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_AUTOLOCK_MIN = "autolock_minutes"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_BALANCE_HIDDEN = "balance_hidden"
        private const val KEY_DB_KEY = "db_encryption_key"
        private const val KEY_PIN_FAILED_ATTEMPTS = "pin_failed_attempts"
        private const val KEY_PIN_LOCKED_UNTIL = "pin_locked_until"
    }
}
