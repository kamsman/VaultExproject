package com.vaultex.core.monitoring

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Bot Telegram d'administration — envoie les événements clés en TEMPS RÉEL
 * au groupe « Vaultex Administration » :
 *
 *   👤 nouveau wallet (créé / importé)
 *   🔄 nouveau swap · 🚨 gros swap (≥ 20 $)
 *   ❌ swap échoué
 *
 * Fire-and-forget : ne bloque jamais l'UI et n'affiche jamais d'erreur à
 * l'utilisateur. Aucune donnée sensible n'est transmise : ni adresse, ni
 * solde, ni clé, ni identité — uniquement le type d'événement et le montant
 * échangé. Token/chat configurables via local.properties (telegram.admin.*).
 */
object AdminBot {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /** Seuil « gros swap » (valeur en USD ≈ USDT). */
    const val BIG_SWAP_USD = 20.0

    fun send(text: String) {
        val token = com.vaultex.BuildConfig.TG_ADMIN_TOKEN
        val chat = com.vaultex.BuildConfig.TG_ADMIN_CHAT
        if (token.isBlank() || chat.isBlank()) return
        scope.launch {
            try {
                val body = okhttp3.FormBody.Builder()
                    .add("chat_id", chat)
                    .add("text", text)
                    .build()
                val req = okhttp3.Request.Builder()
                    .url("https://api.telegram.org/bot$token/sendMessage")
                    .post(body)
                    .build()
                client.newCall(req).execute().use { resp ->
                    // Diagnostic (logcat, debug uniquement) : la RAISON exacte d'un
                    // échec Telegram (token révoqué = 401, chat non démarré = 403,
                    // chat_id inconnu = 400…) sans jamais gêner l'utilisateur.
                    if (com.vaultex.BuildConfig.DEBUG && !resp.isSuccessful) {
                        android.util.Log.w(
                            "AdminBot",
                            "sendMessage HTTP ${resp.code} : ${resp.body?.string()?.take(200)}"
                        )
                    }
                }
            } catch (e: Exception) {
                // Jamais bloquant, jamais visible pour l'utilisateur.
                if (com.vaultex.BuildConfig.DEBUG) android.util.Log.w("AdminBot", "send failed", e)
            }
        }
    }

    /** 👤 Nouveau wallet créé ou importé (fin d'onboarding). */
    fun walletCreated(imported: Boolean) = send(
        if (imported) "👤 Wallet importé sur VaultEx"
        else "👤 Nouveau wallet créé sur VaultEx"
    )

    /** 🔄 / 🚨 Swap lancé (dépôt en cours). [usd] ≈ contre-valeur du montant. */
    fun swapCreated(amount: String, from: String, to: String, usd: Double) {
        val head = if (usd >= BIG_SWAP_USD) "🚨 Gros swap" else "🔄 Nouveau swap"
        val usdTxt = if (usd > 0.0) String.format(Locale.US, " (≈ $%.2f)", usd) else ""
        send("$head : $amount $from → $to$usdTxt")
    }

    /** ✅ Swap terminé avec succès. */
    fun swapFinished(amount: String, from: String, to: String) =
        send("✅ Swap terminé : $amount $from → $to")

    /** ❌ Swap échoué (dépôt refusé ou statut terminal failed/refunded/expired). */
    fun swapFailed(from: String, to: String, reason: String?) =
        send("❌ Swap échoué : $from → $to" + (reason?.take(160)?.let { "\n$it" } ?: ""))
}
