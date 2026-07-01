package com.vaultex.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.vaultex.R
import com.vaultex.ui.components.CryptoIcon
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Charge le logo (grande icône) d'une notification :
 *  - logo de la crypto reçue (ex. USDT) via CryptoIcon, téléchargé en bloquant ;
 *  - repli sur le logo VaultEx si le symbole est absent ou le réseau indisponible.
 *
 * À appeler depuis un thread d'arrière-plan (worker / coroutine IO).
 */
object NotifLogo {

    private val client = OkHttpClient.Builder()
        .callTimeout(6, TimeUnit.SECONDS)
        .build()

    fun forSymbol(context: Context, symbol: String?): Bitmap {
        val appLogo = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        if (symbol.isNullOrBlank()) return appLogo
        return try {
            val req = Request.Builder().url(CryptoIcon.url(symbol)).build()
            client.newCall(req).execute().use { resp ->
                val bytes = resp.body?.bytes()
                if (resp.isSuccessful && bytes != null)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: appLogo
                else appLogo
            }
        } catch (_: Exception) {
            appLogo
        }
    }
}
