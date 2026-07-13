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

    // ─── Code d'installation : SUIVI intelligent sans données personnelles ───
    // Un code court, unique PAR TÉLÉPHONE (généré au premier lancement, stable
    // ensuite), apposé sur chaque message. L'admin peut ainsi relier les
    // événements d'un même utilisateur (wallet créé → swaps → échecs) et
    // compter les installations, sans identité ni adresse.
    @Volatile private var appContext: android.content.Context? = null

    fun init(context: android.content.Context) { appContext = context.applicationContext }

    private val installCode: String by lazy {
        try {
            val prefs = appContext!!.getSharedPreferences("vaultex_admin_bot", android.content.Context.MODE_PRIVATE)
            prefs.getString("install_code", null) ?: run {
                val code = "VX-" + java.util.UUID.randomUUID().toString()
                    .replace("-", "").take(6).uppercase(Locale.US)
                prefs.edit().putString("install_code", code).apply()
                code
            }
        } catch (_: Exception) { "VX-??????" }
    }

    /** Signature apposée en bas de chaque message. */
    private fun signature(): String = "🆔 $installCode"

    // ─── 📲 Nouvelle installation : annoncée UNE SEULE FOIS par téléphone ───
    /**
     * Premier lancement de l'app : compte les installations réelles (avant même
     * la création d'un wallet) avec la langue, la version Android et la version
     * de l'app — sans aucune donnée personnelle. Idempotent (flag persistant).
     */
    fun announceInstallOnce() {
        try {
            val prefs = appContext!!.getSharedPreferences("vaultex_admin_bot", android.content.Context.MODE_PRIVATE)
            if (prefs.getBoolean("install_announced", false)) return
            prefs.edit().putBoolean("install_announced", true).apply()
            val lang = Locale.getDefault().language.uppercase(Locale.US)
            send(
                "📲 Nouvelle installation VaultEx" +
                    "\n🌍 Langue $lang · Android ${android.os.Build.VERSION.RELEASE}" +
                    " · v${com.vaultex.BuildConfig.VERSION_NAME}"
            )
        } catch (_: Exception) { }
    }

    // ─── 💥 Crashs : rapport minimal en temps réel ────────────────────────
    /**
     * Installe un rapporteur de crash qui envoie le type d'erreur + l'endroit
     * (première frame VaultEx de la pile) puis RELAIE au handler existant
     * (Crashlytics / système) — il ne remplace rien, il s'ajoute devant.
     * Envoi SYNCHRONE court (le process meurt juste après le handler).
     */
    fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                val where = e.stackTrace.firstOrNull { it.className.startsWith("com.vaultex") }
                    ?.let { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
                    ?: e.stackTrace.firstOrNull()?.toString()?.take(120) ?: "?"
                sendSync(
                    "💥 Crash VaultEx v${com.vaultex.BuildConfig.VERSION_NAME}" +
                        " · Android ${android.os.Build.VERSION.RELEASE}" +
                        "\n${e.javaClass.simpleName} : ${e.message?.take(160) ?: "(sans message)"}" +
                        "\n📍 $where"
                )
            } catch (_: Exception) { }
            previous?.uncaughtException(thread, e)
        }
    }

    /** Envoi bloquant (≤ ~4 s) — réservé au handler de crash. */
    private fun sendSync(text: String) {
        val token = com.vaultex.BuildConfig.TG_ADMIN_TOKEN
        val chat = com.vaultex.BuildConfig.TG_ADMIN_CHAT
        if (token.isBlank() || chat.isBlank()) return
        try {
            val body = okhttp3.FormBody.Builder()
                .add("chat_id", chat)
                .add("text", text + "\n" + signature())
                .build()
            val req = okhttp3.Request.Builder()
                .url("https://api.telegram.org/bot$token/sendMessage")
                .post(body)
                .build()
            okhttp3.OkHttpClient.Builder()
                .callTimeout(4, TimeUnit.SECONDS)
                .build()
                .newCall(req).execute().close()
        } catch (_: Exception) { }
    }

    fun send(text: String) {
        val token = com.vaultex.BuildConfig.TG_ADMIN_TOKEN
        val chat = com.vaultex.BuildConfig.TG_ADMIN_CHAT
        if (token.isBlank() || chat.isBlank()) return
        scope.launch {
            try {
                val body = okhttp3.FormBody.Builder()
                    .add("chat_id", chat)
                    .add("text", text + "\n" + signature())
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

    /**
     * Compteur PERSISTANT de wallets créés/importés sur CE téléphone. Il
     * numérote chaque création (n°1, n°2, …) et ne redescend jamais, même
     * après suppression — c'est un compteur de créations, pas d'actifs.
     */
    private fun nextWalletSeq(): Int = try {
        val prefs = appContext!!.getSharedPreferences("vaultex_admin_bot", android.content.Context.MODE_PRIVATE)
        val next = prefs.getInt("wallets_created_seq", 0) + 1
        prefs.edit().putInt("wallets_created_seq", next).apply()
        next
    } catch (_: Exception) { 0 }

    /**
     * 👤 Nouveau wallet créé ou importé. [name] = « Wallet 2 »… , [walletId]
     * = code unique (w_xxxxxxxx), [totalOnDevice] = nombre de wallets présents
     * MAINTENANT sur cet appareil. Le message porte aussi un numéro de création
     * (n°X) pour suivre la croissance du parc de chaque installation.
     */
    fun walletCreated(imported: Boolean, name: String = "", walletId: String = "", totalOnDevice: Int = 0) {
        val seq = nextWalletSeq()
        val head = if (imported) "👤 Wallet importé sur VaultEx" else "👤 Nouveau wallet créé sur VaultEx"
        val detail = buildString {
            if (name.isNotBlank()) append("\n📛 $name")
            if (walletId.isNotBlank()) append(" · code $walletId")
            append("\n📊 ")
            if (seq > 0) append("Création n°$seq")
            if (totalOnDevice > 0) {
                if (seq > 0) append(" · ")
                append("$totalOnDevice wallet(s) sur cet appareil")
            }
        }
        send(head + detail)
    }

    /** 🔄 / 🚨 Swap lancé (dépôt en cours). [usd] ≈ contre-valeur du montant. */
    fun swapCreated(amount: String, from: String, to: String, usd: Double) {
        val head = if (usd >= BIG_SWAP_USD) "🚨 Gros swap" else "🔄 Nouveau swap"
        val usdTxt = if (usd > 0.0) String.format(Locale.US, " (≈ $%.2f)", usd) else ""
        send("$head : $amount $from → $to$usdTxt")
    }

    /**
     * ✅ Swap terminé avec succès. [usdFee] = commission VaultEx estimée
     * (1,5 % du montant, en contre-valeur USD) — suivi du revenu.
     */
    fun swapFinished(amount: String, from: String, to: String, usdFee: Double = 0.0) {
        val feeTxt = if (usdFee > 0.0) String.format(Locale.US, "\n💰 Commission ≈ $%.2f", usdFee) else ""
        send("✅ Swap terminé : $amount $from → $to$feeTxt")
    }

    /** ❌ Envoi échoué (raison technique) — détecte les pannes récurrentes (RPC, frais…). */
    fun sendFailed(symbol: String, reason: String?) =
        send("❌ Envoi échoué : $symbol" + (reason?.take(160)?.let { "\n$it" } ?: ""))

    /** 💸 GROS ENVOI (≥ 20 $) — rien en dessous du seuil (pas de spam). */
    fun bigSend(amount: String, symbol: String, usd: Double) {
        if (usd < BIG_SWAP_USD) return
        send("💸 Gros envoi : $amount $symbol" + String.format(Locale.US, " (≈ $%.2f)", usd))
    }

    /** 📥 GROSSE RÉCEPTION (≥ 20 $) — rien en dessous du seuil. */
    fun bigReceive(amount: String, symbol: String, usd: Double) {
        if (usd < BIG_SWAP_USD) return
        send("📥 Grosse réception : $amount $symbol" + String.format(Locale.US, " (≈ $%.2f)", usd))
    }

    /** ❌ Swap échoué (dépôt refusé ou statut terminal failed/refunded/expired). */
    fun swapFailed(from: String, to: String, reason: String?) =
        send("❌ Swap échoué : $from → $to" + (reason?.take(160)?.let { "\n$it" } ?: ""))
}
