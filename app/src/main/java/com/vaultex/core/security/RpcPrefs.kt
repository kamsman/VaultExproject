package com.vaultex.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Préférences chiffrées dédiées aux URLs RPC personnalisées (M-01).
 * Auparavant stockées en clair (MODE_PRIVATE) — un accès root pouvait
 * rediriger les nœuds. Désormais chiffrées via Keystore.
 *
 * Partagé entre SecureStorage (écriture/validation) et NetworkModule
 * (lecture par l'intercepteur de base URL) pour pointer le même fichier.
 */
object RpcPrefs {
    private const val FILE = "vaultex_rpc_prefs_enc"

    @Volatile
    private var instance: SharedPreferences? = null

    fun get(context: Context): SharedPreferences {
        return instance ?: synchronized(this) {
            instance ?: EncryptedSharedPreferences.create(
                context,
                FILE,
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ).also { instance = it }
        }
    }

    /** Une URL RPC personnalisée doit être en HTTPS. */
    fun isValidRpcUrl(url: String): Boolean =
        url.startsWith("https://", ignoreCase = true) && url.length > 10
}
