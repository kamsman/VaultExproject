package com.vaultex.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vaultex.core.crypto.WalletManager
import com.vaultex.core.security.SecureStorage
import com.vaultex.data.remote.api.BitcoinApi
import com.vaultex.data.remote.api.EvmRpcApi
import com.vaultex.data.remote.api.SolanaRpcApi
import com.vaultex.data.remote.api.TronApi
import com.vaultex.data.remote.dto.JsonRpcRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Named

/**
 * Secours LOCAL du push « Fonds reçus » : toutes les ~15 min, compare le solde de
 * chaque adresse à la valeur mémorisée ; si le solde a augmenté → notification
 * locale. Même logique que la Cloud Function `checkDeposits`, mais dans le
 * téléphone (fonctionne sans backend, mais uniquement tant qu'Android laisse
 * tourner le worker — d'où l'intérêt de la Cloud Function en parallèle).
 *
 * Un envoi (solde qui baisse) ne notifie jamais. Au 1er passage, on mémorise les
 * soldes sans notifier (pas de fausse alerte sur les fonds déjà présents).
 */
@HiltWorker
class DepositCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val secureStorage: SecureStorage,
    @Named("eth") private val ethRpc: EvmRpcApi,
    @Named("bnb") private val bnbRpc: EvmRpcApi,
    private val bitcoinApi: BitcoinApi,
    private val solanaRpc: SolanaRpcApi,
    private val tronApi: TronApi,
    private val hub: com.vaultex.core.session.NotificationHub,
    private val notifPrefs: com.vaultex.core.session.NotifPrefs,
    private val syncService: com.vaultex.core.tx.TransactionSyncService,
    private val tokenRepository: com.vaultex.data.repository.TokenRepository,
    private val transactionDao: com.vaultex.data.local.dao.TransactionDao
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val mnemonic = secureStorage.getMnemonic() ?: return Result.success()
            val addr = withContext(Dispatchers.IO) {
                WalletManager.deriveAddresses(mnemonic, secureStorage.getPassphrase())
            }
            val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            /*
            ─── REPÈRES DE SOLDE : UN PAR PORTEFEUILLE ────────────────────────
            La détection de dépôt compare le solde lu au dernier solde connu.
            Ce repère était rangé sous une clé GLOBALE (« bal_BTC »), alors que
            les adresses, elles, changent avec le portefeuille actif.

            Conséquence, en changeant de wallet :
              wallet 1 a 0 BTC        → repère = 0
              bascule vers wallet 2 qui detient 0,5 BTC
              lecture 0,5 - repère 0  → ecart de +0,5
              → « Vous avez recu 0.5 BTC », une ligne inventee dans
                l'historique, et un rapport de reception sur Telegram.

            Rien n'etait arrive : c'est le portefeuille qui avait change. Le
            genre de notification qui detruit la confiance — l'utilisateur
            cherche des fonds qui n'existent pas, puis cesse de croire les
            alertes suivantes, y compris les vraies.

            Le repere est donc indexe par ADRESSE. Elle identifie le
            portefeuille de facon fiable (elle derive de la seed) sans avoir
            besoin d'un identifiant separe, et revenir sur un ancien wallet
            retrouve SON repere — un depot recu entre-temps reste donc
            correctement signale.

            Premier passage sur une adresse = aucun repere = aucune
            notification (branche `before == null`) : la bascule vers le
            nouveau schema ne declenche aucune rafale au premier lancement.
            ───────────────────────────────────────────────────────────────────
             */
            fun balanceKey(symbol: String, address: String) = "bal_${symbol}_$address"
            purgeLegacyBaselines(prefs)

            // (symbole, adresse, seuil poussière, fetch)
            val checks: List<Quad> = listOf(
                Quad("BTC", addr.btc, 5e-7) { fetchBtc(addr.btc) },
                Quad("ETH", addr.eth, 1e-6) { fetchEvm(ethRpc, addr.eth) },
                Quad("BNB", addr.bnb, 1e-6) { fetchEvm(bnbRpc, addr.bnb) },
                Quad("SOL", addr.sol, 1e-6) { fetchSol(addr.sol) },
                Quad("TRX", addr.trx, 1e-3) { fetchTrx(addr.trx) },
                Quad("USDT", addr.trx, 1e-3) { fetchUsdtTrc20(addr.trx) }
            )

            // Chaînes à synchroniser (dédoublonné : TRX + USDT partagent la
            // même adresse Tron → un seul appel syncTron() couvre les deux).
            val toSync = mutableSetOf<String>()
            // Hausses de solde constatées : (symbole, montant, adresse). Sert de
            // FILET si l'explorateur ne rend pas la transaction (voir plus bas).
            val detected = mutableListOf<Triple<String, String, String>>()

            for (c in checks) {
                if (c.address.isBlank()) continue
                val bal = withContext(Dispatchers.IO) { c.fetch() } ?: continue
                val key = balanceKey(c.symbol, c.address)
                val before = if (prefs.contains(key)) prefs.getString(key, null)?.toDoubleOrNull() else null
                prefs.edit().putString(key, bal.toString()).commit()  // commit : voir note en fin de classe
                if (before == null) {
                    /*
                    TOUT PREMIER PASSAGE sur cette chaîne : aucun solde de
                    référence, donc aucun écart calculable.

                    Mais s'arrêter là créait un angle mort : un dépôt reçu
                    AVANT que ce premier relevé n'ait lieu (créer le wallet,
                    envoyer les fonds, puis ouvrir l'app) était enregistré
                    directement dans la référence — plus aucun écart n'apparaît
                    ensuite, et la notification ne partait JAMAIS.

                    On lance donc quand même la synchronisation : elle lit la
                    vraie liste de transactions. C'est TransactionSyncService
                    qui tranche ensuite — il notifie pour un wallet créé dans
                    l'app (aucun passé possible) et reste silencieux pour un
                    wallet importé (qui peut avoir un vrai historique).
                     */
                    if (bal > 0.0) {
                        when (c.symbol) {
                            "TRX", "USDT" -> toSync.add("TRX")
                            else -> toSync.add(c.symbol)
                        }
                    }
                    continue
                }
                val delta = bal - before
                if (delta > c.dust) {
                    when (c.symbol) {
                        "TRX", "USDT" -> toSync.add("TRX")
                        else -> toSync.add(c.symbol)
                    }
                    // Événement admin (Telegram) : réception ≥ 1 $ —
                    // indépendant des préférences de notification de l'utilisateur.
                    val amt = BigDecimal.valueOf(delta).setScale(6, RoundingMode.DOWN)
                        .stripTrailingZeros().toPlainString()
                    val usd = delta * priceUsdOf(c.symbol)
                    detected += Triple(c.symbol, amt, c.address)
                    // Prévenir l'écran AVANT de notifier : le rafraîchissement
                    // des soldes démarre tout de suite, si bien qu'au moment où
                    // l'utilisateur ouvre l'application les fonds sont déjà là.
                    com.vaultex.core.session.BalanceRefreshSignal.signalTxSent()
                    com.vaultex.core.monitoring.AdminBot.reportReceive(amt, c.symbol, usd)
                    // Jalon d'ACTIVATION : sans seuil de montant (le tout premier
                    // dépôt compte même s'il est minuscule).
                    com.vaultex.core.monitoring.AdminBot.milestoneFirstDeposit(amt, c.symbol, usd)
                }
            }

            // ─── Tokens ERC-20/BEP-20 du wallet (SHIB, USDC…) : surveillance
            // des soldes de CONTRATS. Sans elle, un token reçu n'avait ni
            // cloche, ni « Récent », ni Historique — seul le solde finissait
            // par bouger (panique « mes SHIB ne sont jamais arrivés »).
            try {
                val customs = withContext(Dispatchers.IO) { tokenRepository.getCustom() }
                for (t in customs) {
                    val isBnb = t.blockchain == "BNB"
                    val holder = if (isBnb) addr.bnb else addr.eth
                    if (holder.isBlank()) continue
                    val bal = withContext(Dispatchers.IO) {
                        fetchErc20(if (isBnb) bnbRpc else ethRpc, t.contractAddress, holder, t.decimals)
                    } ?: continue
                    // Le CONTRAT ne suffit pas comme clé : il est identique d'un
                    // portefeuille à l'autre. Sans l'adresse du détenteur, le
                    // solde du wallet 2 se compare à celui du wallet 1 et un
                    // simple changement de portefeuille passe pour une réception.
                    val key = "bal_tok_${t.contractAddress.lowercase()}_$holder"
                    val before = if (prefs.contains(key)) prefs.getString(key, null)?.toDoubleOrNull() else null
                    prefs.edit().putString(key, bal.toString()).commit()  // commit : voir note en fin de classe
                    if (before == null) continue             // 1er passage : on mémorise
                    val delta = bal - before
                    if (delta > 1e-9) {
                        toSync.add(if (isBnb) "BNB_TOKENS" else "ETH_TOKENS")
                        val amt = BigDecimal.valueOf(delta).setScale(6, RoundingMode.DOWN)
                            .stripTrailingZeros().toPlainString()
                        val usdTok = delta * priceUsdOf(t.symbol)
                        detected += Triple(t.symbol, amt, holder)
                        com.vaultex.core.session.BalanceRefreshSignal.signalTxSent()
                        com.vaultex.core.monitoring.AdminBot.reportReceive(amt, t.symbol, usdTok)
                        com.vaultex.core.monitoring.AdminBot.milestoneFirstDeposit(amt, t.symbol, usdTok)
                    }
                }
            } catch (_: Exception) { }

            // Récupère le VRAI hash de la transaction reçue (via le même
            // service que l'écran Historique) et notifie la cloche à ce
            // moment-là : sans ça, cette détection RAPIDE (toutes les ~15 min,
            // ou ~30 s app ouverte) notifiait à l'instant mais ne pouvait
            // écrire aucune entrée dans « Récent » (elle ne connaît que le
            // montant, pas le hash) — désalignement signalé en test réel.
            withContext(Dispatchers.IO) {
                for (chain in toSync) {
                    when (chain) {
                        "BTC" -> syncService.syncBtc(addr.btc)
                        "ETH" -> syncService.syncEth(addr.eth)
                        "BNB" -> syncService.syncBnb(addr.bnb)
                        "SOL" -> syncService.syncSol(addr.sol)
                        "TRX" -> syncService.syncTron(addr.trx)
                        "ETH_TOKENS" -> syncService.syncEthTokens(addr.eth)
                        "BNB_TOKENS" -> syncService.syncBnbTokens(addr.bnb)
                    }
                }
            }

            /*
            ─── FILET : NE JAMAIS DEPENDRE D'UN SEUL EXPLORATEUR ──────────────
            Jusqu'ici, une hausse de solde n'était signalée QUE si la
            synchronisation parvenait ensuite à lire la transaction chez
            l'explorateur. Or ces appels échouent silencieusement — Etherscan et
            BscScan refusent désormais les requêtes sans clé d'API et renvoient
            simplement `status != "1"`, ce que le code traitait comme « rien de
            neuf ». Résultat sur ETH et BNB : le solde montait, mais AUCUNE
            entrée dans « Récent », AUCUNE notification, AUCUNE pastille.
            Rien du tout, sans le moindre message d'erreur.

            Le solde, lui, ne ment pas : il vient d'un appel RPC direct, sans
            clé. Si le solde a monté et qu'aucune réception n'a été enregistrée
            pour cette monnaie, on inscrit nous-mêmes l'opération et on
            notifie. Le hash est inconnu, on pose un identifiant local — la
            ligne apparaît dans « Récent » et l'utilisateur est prévenu.

            La clé de déduplication du hub étant « monnaie + montant », si la
            vraie transaction est retrouvée plus tard, elle ne redéclenche pas
            de seconde notification.
            ───────────────────────────────────────────────────────────────────
             */
            for ((symbol, amount, address) in detected) {
                try {
                    val since = System.currentTimeMillis() - 60L * 60 * 1000
                    val known = withContext(Dispatchers.IO) {
                        transactionDao.countReceivedSince(symbol, since)
                    }
                    if (known > 0) continue   // l'explorateur a fait son travail

                    val localHash = "local:$symbol:$amount:${System.currentTimeMillis()}"
                    withContext(Dispatchers.IO) {
                        transactionDao.insertIgnore(
                            com.vaultex.data.local.entity.TransactionEntity(
                                hash = localHash,
                                type = "received",
                                blockchain = symbol.substringBefore("-"),
                                fromAddress = "",
                                toAddress = address,
                                amount = amount,
                                tokenSymbol = symbol,
                                fee = "0",
                                status = "confirmed",
                                timestamp = System.currentTimeMillis(),
                                confirmations = 1,
                                blockNumber = null
                            )
                        )
                    }
                    if (notifPrefs.txAlerts.value) {
                        hub.post(
                            key = com.vaultex.core.session.NotificationHub.receiveKey(symbol, amount),
                            title = "Vous avez reçu $amount $symbol",
                            body = "Fonds crédités sur votre portefeuille",
                            symbol = symbol
                        )
                    }
                } catch (_: Exception) { }
            }

            checkLowBalance()
            checkIdleMilestone()
            Result.success()
        } catch (e: Exception) {
            /*
            `success` et non `retry`.

            Avec `retry`, WorkManager reprogramme le travail avec un delai qui
            double a chaque echec — jusqu'a plusieurs heures — et tant qu'il
            attend, toute nouvelle demande du meme nom est ignoree. Une simple
            coupure reseau pouvait ainsi eteindre la detection des depots pour
            le reste de la session.

            Ce travail n'a de toute facon pas besoin d'etre rejoue : il repart
            tout seul toutes les 30 s au premier plan, et toutes les 15 min en
            arriere-plan. L'echec est simplement signale.
             */
            com.vaultex.core.monitoring.AdminBot.serviceFailed("Détection de dépôts", e.message)
            Result.success()
        }
    }

    /**
     * Jalon d'ABANDON (Telegram) : wallet créé mais toujours vide une semaine
     * après l'installation. Envoyé une seule fois — indique où l'entonnoir
     * casse. Indépendant des préférences de notification de l'utilisateur.
     */
    private fun checkIdleMilestone() {
        try {
            val json = secureStorage.getPortfolioSnapshot()
            val total = if (json == null) 0.0
                else com.google.gson.Gson().fromJson(json, SnapMini::class.java)?.totalBalanceXof ?: 0.0
            com.vaultex.core.monitoring.AdminBot.milestoneIdleIfNeeded(hasFunds = total > 0.0)
        } catch (_: Exception) { }
    }

    /** Alerte « solde bas » : notifie UNE fois au passage sous le seuil (XOF). */
    private fun checkLowBalance() {
        if (!notifPrefs.lowBalanceAlerts.value) return
        try {
            val json = secureStorage.getPortfolioSnapshot() ?: return
            val total = com.google.gson.Gson().fromJson(json, SnapMini::class.java)?.totalBalanceXof ?: return
            val threshold = notifPrefs.thresholdXof.value.toDouble()
            if (total < threshold && !notifPrefs.lowBalanceNotified) {
                val title = applicationContext.getString(com.vaultex.R.string.notif_lowbal_title)
                val body = applicationContext.getString(
                    com.vaultex.R.string.notif_lowbal_body,
                    java.text.NumberFormat.getNumberInstance(com.vaultex.core.session.LocaleManager.appLocale()).format(threshold.toLong())
                )
                // Clé stable : l'alerte « solde bas » ne doit pas se répéter
                // à chaque cycle du worker tant que le seuil reste franchi.
                hub.post(key = "lowbal:$threshold", title = title, body = body)
                notifPrefs.lowBalanceNotified = true
            } else if (total >= threshold) {
                notifPrefs.lowBalanceNotified = false   // réarmé quand on repasse au-dessus
            }
        } catch (_: Exception) { }
    }

    private data class SnapMini(val totalBalanceXof: Double = 0.0)

    // ─── Prix USD d'un symbole (instantané portefeuille, aucun appel réseau) ───
    private data class SnapTokens(val tokens: List<TokMini>?)
    private data class TokMini(val symbol: String = "", val priceUsd: Double = 0.0)

    private fun priceUsdOf(symbol: String): Double {
        return try {
            val json = secureStorage.getPortfolioSnapshot() ?: return 0.0
            com.google.gson.Gson().fromJson(json, SnapTokens::class.java)
                ?.tokens?.firstOrNull { it.symbol.equals(symbol, ignoreCase = true) }?.priceUsd ?: 0.0
        } catch (_: Exception) { 0.0 }
    }

    // ─── Soldes par chaîne (mêmes appels que PortfolioViewModel) ───
    private suspend fun fetchEvm(rpc: EvmRpcApi, address: String): Double? = try {
        val res = rpc.rpcCall(JsonRpcRequest("eth_getBalance", mutableListOf(address as Any, "latest" as Any)))
        val hex = res.result as? String
        if (res.error != null || hex == null) null
        else BigInteger(hex.removePrefix("0x").ifEmpty { "0" }, 16)
            .toBigDecimal().divide(BigDecimal("1000000000000000000")).toDouble()
    } catch (_: Exception) { null }

    private suspend fun fetchBtc(address: String): Double? = try {
        val info = bitcoinApi.getAddressInfo(address)
        (info.chainStats.fundedSum - info.chainStats.spentSum) / 1e8
    } catch (_: Exception) { null }

    /** Solde d'un token ERC-20/BEP-20 via eth_call(balanceOf) — même appel
     *  que PortfolioViewModel.fetchErc20Balance. */
    private suspend fun fetchErc20(rpc: EvmRpcApi, contract: String, address: String, decimals: Int): Double? = try {
        val paddedAddr = address.removePrefix("0x").padStart(64, '0')
        val res = rpc.rpcCall(JsonRpcRequest("eth_call",
            mutableListOf(mapOf("to" to contract, "data" to "0x70a08231$paddedAddr") as Any, "latest" as Any)))
        val hex = res.result as? String
        if (res.error != null || hex == null) null
        else BigInteger(hex.removePrefix("0x").ifEmpty { "0" }, 16)
            .toBigDecimal().divide(BigDecimal.TEN.pow(decimals)).toDouble()
    } catch (_: Exception) { null }

    private suspend fun fetchSol(address: String): Double? = try {
        val res = solanaRpc.rpcCall(JsonRpcRequest("getBalance", mutableListOf(address as Any)))
        if (res.error != null) null
        else {
            @Suppress("UNCHECKED_CAST")
            val lamports = (res.result as? Map<String, Any>)?.get("value") as? Double ?: 0.0
            lamports / 1e9
        }
    } catch (_: Exception) { null }

    private suspend fun fetchTrx(address: String): Double? = try {
        val account = tronApi.getAccount(address)
        (account.data.firstOrNull()?.balance ?: 0L) / 1_000_000.0
    } catch (_: Exception) { null }

    private suspend fun fetchUsdtTrc20(address: String): Double? = try {
        val account = tronApi.getAccount(address)
        val trc20List = account.data.firstOrNull()?.trc20 ?: emptyList()
        val raw = trc20List.firstOrNull { it.containsKey(USDT_TRC20) }?.get(USDT_TRC20)
        raw?.toLongOrNull()?.let { it / 1_000_000.0 } ?: 0.0
    } catch (_: Exception) { null }

    private class Quad(
        val symbol: String,
        val address: String,
        val dust: Double,
        val fetch: suspend () -> Double?
    )

    /**
     * Supprime les repères de solde de l'ancien schéma, non indexés par
     * portefeuille (« bal_BTC », « bal_tok_0x… »).
     *
     * Les laisser ne casserait rien — les nouvelles clés portent un suffixe
     * d'adresse et ne les croisent jamais — mais ils resteraient à vie dans les
     * préférences, et surtout ils rendraient une relecture du code trompeuse :
     * on croirait le repère global encore actif. Le nettoyage est fait une fois
     * pour toutes.
     */
    private fun purgeLegacyBaselines(prefs: android.content.SharedPreferences) {
        if (prefs.getBoolean(KEY_LEGACY_PURGED, false)) return
        val legacy = prefs.all.keys.filter { key ->
            // Ancien schéma : « bal_<SYMBOLE> » sans suffixe d'adresse, et
            // « bal_tok_<contrat> » sans adresse de détenteur. Les nouvelles
            // clés se reconnaissent à leur segment supplémentaire.
            when {
                key.startsWith("bal_tok_") -> key.count { it == '_' } == 2
                key.startsWith("bal_") -> key.count { it == '_' } == 1
                else -> false
            }
        }
        prefs.edit().apply {
            legacy.forEach { remove(it) }
            putBoolean(KEY_LEGACY_PURGED, true)
        }.apply()
    }

    /*
    ═══════════════════════════════════════════════════════════════════════
    POURQUOI `commit()` ET NON `apply()` POUR LES SOLDES DE REFERENCE
    ═══════════════════════════════════════════════════════════════════════

    La detection de depot compare le solde courant au solde MEMORISE au
    passage precedent. Ce solde de reference est donc le pivot de tout le
    mecanisme.

    `apply()` met a jour la memoire et programme l'ecriture disque pour plus
    tard. Or ce worker s'execute souvent dans un processus que le systeme tue
    aussitot apres `doWork()` — l'ecriture differee n'a alors jamais lieu.

    Consequence concrete : au passage suivant, `before` vaut null, ce que le
    code interprete comme un TOUT PREMIER passage sur cette chaine. Aucun
    ecart n'est calcule, donc aucune notification. Un depot reellement recu
    passe inapercu, sans erreur nulle part.

    `commit()` ecrit avant de rendre la main. Quelques millisecondes par
    chaine, contre le risque de ne pas prevenir un utilisateur qu'il a recu
    de l'argent.
    ═══════════════════════════════════════════════════════════════════════
     */

    companion object {
        const val WORK_NAME = "vaultex_deposit_check"
        private const val PREFS = "deposit_check_prefs"
        private const val KEY_LEGACY_PURGED = "legacy_baselines_purged"
        private const val USDT_TRC20 = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"
    }
}
