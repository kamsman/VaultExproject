package com.vaultex.core.session

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stocke l'état de création du wallet dans EncryptedSharedPreferences.
 * NOTE: La source de vérité réelle est SecureStorage.hasMnemonic() —
 * ce flag sert uniquement au routage de navigation au démarrage.
 */
object AppLaunchManager {

    private const val PREFS_NAME = "vaultex_launch_prefs"
    private const val KEY_WALLET_CREATED = "wallet_created"

    private fun getPrefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun setWalletCreated(context: Context, created: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_WALLET_CREATED, created).apply()
    }

    fun isWalletCreated(context: Context): Boolean = try {
        getPrefs(context).getBoolean(KEY_WALLET_CREATED, false)
    } catch (_: Exception) {
        false
    }
}
