package com.vaultex.core.session

import android.content.Context

object AppLaunchManager {

    private const val PREFS_NAME = "vaultex_prefs"

    private const val KEY_WALLET_CREATED = "wallet_created"

    fun setWalletCreated(
        context: Context,
        created: Boolean
    ) {

        val prefs = context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

        prefs.edit()
            .putBoolean(KEY_WALLET_CREATED, created)
            .apply()
    }

    fun isWalletCreated(
        context: Context
    ): Boolean {

        val prefs = context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

        return prefs.getBoolean(
            KEY_WALLET_CREATED,
            false
        )
    }
}