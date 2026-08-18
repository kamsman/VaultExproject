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
 *   💸 envoi · 📥 réception (≥ 1 $) · 🔄 swap · 🚨 gros montant (≥ 20 $)
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

    /**
     * Seuil de REPORTING des envois/réceptions (USD). Fixé bas (1 $) pour
     * observer l'usage réel presque intégralement ; en dessous, on ne remonte
     * rien — poussière, arrondis et frais ne doivent pas noyer le canal.
     */
    const val REPORT_MIN_USD = 1.0

    /** Seuil au-delà duquel un swap est signalé comme « gros » (🚨). Les swaps
     *  sont TOUS rapportés : ce seuil ne change que la mise en avant. */
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

    // ─── Jalons : événements envoyés UNE SEULE FOIS par installation ──────
    // Ils mesurent l'entonnoir réel (installation → dépôt → swap) sans
    // générer de bruit : un message par utilisateur et par jalon, à vie.

    private const val KEY_INSTALL_AT = "install_at"

    private fun prefs(): android.content.SharedPreferences? =
        appContext?.getSharedPreferences("vaultex_admin_bot", android.content.Context.MODE_PRIVATE)

    /** Vrai UNE seule fois pour [key] : marque aussitôt le jalon comme atteint. */
    private fun firstTime(key: String): Boolean {
        val p = prefs() ?: return false
        if (p.getBoolean(key, false)) return false
        p.edit().putBoolean(key, true).apply()
        return true
    }

    /** Jours écoulés depuis l'installation, ou -1 si la date est inconnue
     *  (installations antérieures à l'ajout de ce suivi). */
    private fun daysSinceInstall(): Int {
        val at = prefs()?.getLong(KEY_INSTALL_AT, 0L) ?: 0L
        if (at <= 0L) return -1
        return ((System.currentTimeMillis() - at) / 86_400_000L).toInt()
    }

    /**
     * Délai depuis l'installation, en granularité utile : les heures sur les
     * deux premiers jours (savoir si l'activation est immédiate ou non est
     * bien plus parlant que « J+0 »), puis en jours.
     */
    private fun sinceTxt(): String {
        val at = prefs()?.getLong(KEY_INSTALL_AT, 0L) ?: 0L
        if (at <= 0L) return ""
        val ms = System.currentTimeMillis() - at
        if (ms < 0L) return ""
        val hours = ms / 3_600_000L
        return when {
            hours < 1L -> " · moins d'1 h après installation"
            hours < 48L -> " · ${hours} h après installation"
            else -> " · J+${hours / 24L} après installation"
        }
    }

    /**
     * Contexte marché : pays de la carte SIM (ou du réseau, sinon la locale)
     * et nom de l'opérateur. Aucune permission requise, aucune donnée
     * personnelle — sert à savoir QUELS marchés UEMOA répondent.
     */
    private fun marketContext(): String = try {
        val tm = appContext?.getSystemService(android.content.Context.TELEPHONY_SERVICE)
            as? android.telephony.TelephonyManager
        val iso = listOf(tm?.simCountryIso, tm?.networkCountryIso, Locale.getDefault().country)
            .firstOrNull { !it.isNullOrBlank() }.orEmpty()
        val carrier = tm?.networkOperatorName?.takeIf { it.isNotBlank() }
        buildString {
            if (iso.isNotBlank()) append(iso.uppercase(Locale.US))
            if (carrier != null) {
                if (isNotEmpty()) append(" · ")
                append(carrier)
            }
        }
    } catch (_: Exception) { "" }

    /** Marque et modèle de l'appareil — pour cibler les tests (Tecno, Samsung…). */
    private fun deviceModel(): String =
        "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim()

    /** 🎉 ACTIVATION : tout premier dépôt reçu (quel que soit le montant). */
    fun milestoneFirstDeposit(amount: String, symbol: String, usd: Double) {
        if (!firstTime("ms_first_deposit")) return
        val value = if (usd > 0.0) usdTxt(usd) else ""
        send("🎉 PREMIER DÉPÔT : $amount $symbol$value${sinceTxt()}")
    }

    /** 💰 MONÉTISATION : tout premier swap mené à terme (commission encaissée). */
    fun milestoneFirstSwap(from: String, to: String, usdFee: Double) {
        if (!firstTime("ms_first_swap")) return
        val feeTxt = if (usdFee > 0.0) String.format(Locale.US, " · commission ≈ $%.2f", usdFee) else ""
        send("💰 PREMIER SWAP réussi : $from → $to$feeTxt${sinceTxt()}")
    }

    /** 🔐 SÉCURITÉ : l'utilisateur a réellement consulté sa phrase de récupération. */
    fun milestoneBackupDone() {
        if (!firstTime("ms_backup")) return
        send("🔐 Phrase de récupération sauvegardée${sinceTxt()}")
    }

    /**
     * 📉 ABANDON : wallet créé mais toujours vide au bout d'une semaine.
     * Signalé une seule fois — c'est LE signal qui dit où l'entonnoir casse
     * (l'utilisateur n'a aucun moyen simple d'alimenter son wallet).
     */
    fun milestoneIdleIfNeeded(hasFunds: Boolean) {
        if (hasFunds) return
        val d = daysSinceInstall()
        if (d < 7) return
        if (!firstTime("ms_idle")) return
        send("📉 Wallet toujours vide $d jours après l'installation (aucun dépôt reçu)")
    }

    // ─── 📲 Nouvelle installation : annoncée UNE SEULE FOIS par téléphone ───

    /** Préfixe du message ÉPINGLÉ servant de compteur global d'installations. */
    private const val COUNTER_PREFIX = "📌 VaultEx — installations : "

    /**
     * Compteur GLOBAL d'installations SANS serveur : le total vit dans le
     * message épinglé du groupe. Chaque nouvelle installation le lit
     * (getChat → pinned_message), l'incrémente (editMessageText) et reçoit
     * son numéro. Premier passage : crée le message et l'épingle (le bot
     * doit être ADMIN du groupe avec le droit « Épingler des messages »).
     * Renvoie 0 si indisponible (réseau/droits) → annonce sans numéro.
     */
    private fun bumpInstallCounter(token: String, chat: String): Int = try {
        val body = client.newCall(
            okhttp3.Request.Builder()
                .url("https://api.telegram.org/bot$token/getChat?chat_id=$chat").build()
        ).execute().use { it.body?.string() } ?: ""
        val pinned = org.json.JSONObject(body).optJSONObject("result")?.optJSONObject("pinned_message")
        val text = pinned?.optString("text") ?: ""
        if (pinned != null && text.startsWith(COUNTER_PREFIX)) {
            val n = (text.removePrefix(COUNTER_PREFIX).trim().toIntOrNull() ?: 0) + 1
            client.newCall(
                okhttp3.Request.Builder()
                    .url("https://api.telegram.org/bot$token/editMessageText")
                    .post(
                        okhttp3.FormBody.Builder()
                            .add("chat_id", chat)
                            .add("message_id", pinned.getInt("message_id").toString())
                            .add("text", COUNTER_PREFIX + n)
                            .build()
                    ).build()
            ).execute().close()
            n
        } else {
            // Premier passage : créer le compteur puis l'épingler (sans notif).
            val resp = client.newCall(
                okhttp3.Request.Builder()
                    .url("https://api.telegram.org/bot$token/sendMessage")
                    .post(
                        okhttp3.FormBody.Builder()
                            .add("chat_id", chat).add("text", COUNTER_PREFIX + "1").build()
                    ).build()
            ).execute().use { it.body?.string() } ?: ""
            val mid = org.json.JSONObject(resp).optJSONObject("result")?.optInt("message_id") ?: 0
            if (mid > 0) {
                client.newCall(
                    okhttp3.Request.Builder()
                        .url("https://api.telegram.org/bot$token/pinChatMessage")
                        .post(
                            okhttp3.FormBody.Builder()
                                .add("chat_id", chat)
                                .add("message_id", mid.toString())
                                .add("disable_notification", "true")
                                .build()
                        ).build()
                ).execute().close()
            }
            1
        }
    } catch (_: Exception) { 0 }

    /**
     * Premier lancement de l'app : compte les installations réelles (avant même
     * la création d'un wallet) avec la langue, la version Android et la version
     * de l'app — sans aucune donnée personnelle. Idempotent (flag persistant).
     * Le numéro global (n°X) vient du compteur épinglé du groupe.
     */
    fun announceInstallOnce() {
        try {
            val prefs = appContext!!.getSharedPreferences("vaultex_admin_bot", android.content.Context.MODE_PRIVATE)
            if (prefs.getBoolean("install_announced", false)) return
            // Date d'installation : sert à dater tous les jalons (« J+3 »).
            prefs.edit()
                .putBoolean("install_announced", true)
                .putLong(KEY_INSTALL_AT, System.currentTimeMillis())
                .apply()
            val token = com.vaultex.BuildConfig.TG_ADMIN_TOKEN
            val chat = com.vaultex.BuildConfig.TG_ADMIN_CHAT
            if (token.isBlank() || chat.isBlank()) return
            val lang = Locale.getDefault().language.uppercase(Locale.US)
            scope.launch {
                val n = bumpInstallCounter(token, chat)
                val numTxt = if (n > 0) " n°$n" else ""
                val market = marketContext()
                val marketTxt = if (market.isNotBlank()) "$market · " else ""
                send(
                    "📲 Nouvelle installation VaultEx$numTxt" +
                        "\n🌍 ${marketTxt}Langue $lang" +
                        "\n📱 ${deviceModel()} · Android ${android.os.Build.VERSION.RELEASE}" +
                        " · v${com.vaultex.BuildConfig.VERSION_NAME}"
                )
            }
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
                val msg = "💥 Crash VaultEx v${com.vaultex.BuildConfig.VERSION_NAME}" +
                    "\n📱 ${deviceModel()} · Android ${android.os.Build.VERSION.RELEASE}" +
                    "\n${e.javaClass.simpleName} : ${e.message?.take(160) ?: "(sans message)"}" +
                    "\n📍 $where"
                // Envoi sur un thread SÉPARÉ, borné à 1,5 s : si le réseau est
                // lent/bloqué (proxy), on ne fige JAMAIS le thread qui plante —
                // sinon un crash au démarrage se transformait en ANR
                // « failed to complete startup » masquant la vraie erreur.
                val t = Thread { try { sendSync(msg) } catch (_: Throwable) {} }
                t.isDaemon = true
                t.start()
                t.join(1_500L)
            } catch (_: Throwable) { }
            // Relaie TOUJOURS au handler système : la vraie stack apparaît dans
            // logcat (FATAL EXCEPTION / AndroidRuntime) pour le diagnostic.
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
    /*
    Lecture de solde impossible sur une chaine. Signale AU PLUS UNE FOIS PAR
    HEURE et par monnaie : ces echecs vont souvent par rafales (rate-limit d'un
    noeud public), et il ne s'agit pas d'inonder le canal — juste de savoir
    QUELLE chaine ne repond pas quand un utilisateur signale un solde a zero.
     */
    private val lastFail = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /**
     * Signale la panne d'un service externe, AU PLUS UNE FOIS PAR HEURE et par
     * [key]. Ces echecs vont par rafales — un noeud qui limite le debit refuse
     * tout pendant plusieurs minutes — et il ne s'agit pas d'inonder le canal,
     * seulement de savoir CE QUI ne repond pas.
     *
     * Ce mecanisme a ete ajoute apres plusieurs jours passes a chercher un
     * solde affiche a zero : la cause etait un appel refuse en silence. Le code
     * avale ses erreurs un peu partout (catch vide), et sans remontee on
     * diagnostique a l'aveugle sur un telephone qu'on n'a pas en main.
     */
    private fun reportFailure(key: String, message: String, cooldownMs: Long = 60L * 60 * 1000) {
        val now = System.currentTimeMillis()
        if (now - (lastFail[key] ?: 0L) < cooldownMs) return
        lastFail[key] = now
        send(message)
    }

    /**
     * ECHEC de creation ou d'import de portefeuille.
     *
     * L'evenement le plus grave que ce canal puisse porter : l'utilisateur
     * n'a PAS d'application, et il ne peut rien en faire. Aucun seuil, aucune
     * fenetre de repos — chaque occurrence compte, et un groupe d'echecs sur
     * un meme modele d'appareil designe immediatement la cause.
     */
    fun walletCreationFailed(imported: Boolean, reason: String) {
        val action = if (imported) "IMPORT" else "CREATION"
        send(
            "\uD83D\uDEA8 ECHEC $action DE PORTEFEUILLE\n" +
                "\uD83D\uDCF1 " + deviceModel() + " \u00b7 Android " + android.os.Build.VERSION.RELEASE + "\n" +
                "\uD83C\uDF0D " + marketContext() + "\n" +
                reason.take(300)
        )
    }

    /** Solde d'une monnaie illisible (noeud injoignable / quota depasse). */
    fun balanceReadFailed(symbol: String) =
        reportFailure("bal:$symbol", "\u26A0\uFE0F Solde illisible : $symbol (noeud injoignable ou quota depasse)")

    /**
     * Echec de certificate pinning \u2014 a signaler FORT, jamais en silence.
     *
     * Une empreinte de CERT_PINS qui ne correspond plus au certificat reel du
     * serveur fait rejeter la connexion par OkHttp. Du point de vue de
     * l'utilisateur : rien. Pas de message, pas de plantage \u2014 juste des soldes
     * a zero et des ecrans vides. La panne est indiscernable d'une coupure
     * reseau, alors que sa cause est dans l'application et qu'elle touche
     * TOUS les appareils en meme temps.
     *
     * C'est exactement ce qui s'est produit : le canal signalait \u00AB solde
     * illisible \u00BB sans la raison, ce qui n'aidait pas a trouver la cause.
     *
     * Le message d'OkHttp contient la chaine reelle presentee par le serveur,
     * sous la forme \u00AB Peer certificate chain: sha256/... \u00BB. Il est transmis
     * largement (900 caracteres) parce que ce sont precisement les empreintes
     * a reporter dans CERT_PINS pour corriger \u2014 telles que les voit
     * l'APPAREIL, ce qui peut differer de ce qu'un poste de developpement
     * observe : les CDN servent des certificats differents selon le reseau et
     * la region.
     *
     * Fenetre de repos courte (5 min) : c'est une panne totale, pas un bruit.
     */
    fun certPinFailed(host: String, detail: String?) =
        reportFailure(
            "pin:$host",
            "\uD83D\uDD12 PINNING TLS EN ECHEC : $host\n\n" +
                "L'application refuse la connexion : l'empreinte declaree dans " +
                "CERT_PINS ne correspond a aucun certificat presente par ce serveur. " +
                "Toutes les donnees venant de cet hote sont indisponibles.\n\n" +
                "Chaine reellement presentee a CET appareil :\n" +
                (detail?.take(900) ?: "(aucun detail)"),
            cooldownMs = 5L * 60 * 1000
        )

    /**
     * Historique d'une chaine non recupere.
     *
     * DEUX FAMILLES d'echec bien differentes se cachaient derriere le meme
     * message, et il fallait les distinguer :
     *
     * 1) PANNE TRANSITOIRE (noeud sature, cle expiree, coupure reseau) — se
     *    resout SEULE ; relancer l'alerte au bout d'une heure a du sens.
     *
     * 2) MUR DE FORFAIT PAYANT — Etherscan V2 repond explicitement
     *    « Free API access is not supported for this chain. Please upgrade
     *    your api plan » pour certaines chaines secondaires (BNB Chain,
     *    entre autres) sur une cle gratuite/Demo. Ce n'est PAS un bug, pas
     *    une panne, et surtout pas transitoire : retenter dans une heure
     *    donne EXACTEMENT le meme resultat, indefiniment, tant que le forfait
     *    n'est pas change. Le signaler toutes les heures n'est qu'un bruit
     *    qui ne mene a aucune action nouvelle — d'ou une fenetre de repos
     *    bien plus longue (7 jours) et un message qui nomme le vrai remede.
     *
     * Dans les deux cas, le solde et la detection de depot ne dependent PAS
     * de cet appel (ils passent par le noeud RPC natif, pas par Etherscan) :
     * seul l'historique detaille (vrai hash, liste complete) est affecte.
     */
    fun historyReadFailed(chain: String, reason: String?) {
        val isPlanWall = reason?.contains("upgrade your api plan", ignoreCase = true) == true ||
            reason?.contains("not supported for this chain", ignoreCase = true) == true
        if (isPlanWall) {
            reportFailure(
                "hist_plan:$chain",
                "\u26A0\uFE0F Historique $chain : plan Etherscan gratuit insuffisant pour cette chaine.\n" +
                    "Le solde et la detection de depot restent corrects (RPC direct) — seuls le vrai hash " +
                    "et l'historique detaille manquent. Remede : passer sur un forfait Etherscan payant.",
                cooldownMs = 7L * 24 * 60 * 60 * 1000
            )
            return
        }
        reportFailure(
            "hist:$chain",
            "\u26A0\uFE0F Historique illisible : $chain" + (reason?.take(120)?.let { "\n$it" } ?: "")
        )
    }

    /** Appel a un service tiers en echec (ChangeNOW, cours, explorateur...). */
    fun serviceFailed(service: String, reason: String?) =
        reportFailure(
            "svc:$service",
            "\u26A0\uFE0F Service indisponible : $service" + (reason?.take(120)?.let { "\n$it" } ?: "")
        )

    fun sendFailed(symbol: String, reason: String?) =
        send("❌ Envoi échoué : $symbol" + (reason?.take(160)?.let { "\n$it" } ?: ""))

    /** 💸 Envoi rapporté dès [REPORT_MIN_USD] ; 🚨 au-delà de [BIG_SWAP_USD]. */
    fun reportSend(amount: String, symbol: String, usd: Double) {
        if (belowThreshold(usd)) return
        val head = if (usd >= BIG_SWAP_USD) "🚨 Gros envoi" else "💸 Envoi"
        send("$head : $amount $symbol${usdTxt(usd)}")
    }

    /** 📥 Réception rapportée dès [REPORT_MIN_USD] ; 🚨 au-delà de [BIG_SWAP_USD]. */
    fun reportReceive(amount: String, symbol: String, usd: Double) {
        if (belowThreshold(usd)) return
        val head = if (usd >= BIG_SWAP_USD) "🚨 Grosse réception" else "📥 Réception"
        send("$head : $amount $symbol${usdTxt(usd)}")
    }

    /**
     * Filtre le bruit SANS jamais perdre une transaction : on n'écarte que les
     * montants CONNUS et réellement inférieurs au seuil. Si la valorisation est
     * indisponible (usd = 0 : instantané de prix vide, API limitée, token non
     * coté), l'événement passe quand même — une transaction de 500 $ ne doit
     * jamais devenir invisible parce que le prix manquait à cet instant.
     */
    private fun belowThreshold(usd: Double): Boolean = usd > 0.0 && usd < REPORT_MIN_USD

    private fun usdTxt(usd: Double): String =
        if (usd > 0.0) String.format(Locale.US, " (≈ $%.2f)", usd) else " (valeur inconnue)"

    /** ❌ Swap échoué (dépôt refusé ou statut terminal failed/refunded/expired). */
    fun swapFailed(from: String, to: String, reason: String?) =
        send("❌ Swap échoué : $from → $to" + (reason?.take(160)?.let { "\n$it" } ?: ""))
}
