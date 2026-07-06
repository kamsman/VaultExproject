package com.vaultex.service

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vaultex.R
import com.vaultex.app.VaultExApplication
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
    private val notificationCenter: com.vaultex.core.session.NotificationCenter,
    private val notifPrefs: com.vaultex.core.session.NotifPrefs
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val mnemonic = secureStorage.getMnemonic() ?: return Result.success()
            val addr = withContext(Dispatchers.IO) {
                WalletManager.deriveAddresses(mnemonic, secureStorage.getPassphrase())
            }
            val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

            // (symbole, adresse, seuil poussière, fetch)
            val checks: List<Quad> = listOf(
                Quad("BTC", addr.btc, 5e-7) { fetchBtc(addr.btc) },
                Quad("ETH", addr.eth, 1e-6) { fetchEvm(ethRpc, addr.eth) },
                Quad("BNB", addr.bnb, 1e-6) { fetchEvm(bnbRpc, addr.bnb) },
                Quad("SOL", addr.sol, 1e-6) { fetchSol(addr.sol) },
                Quad("TRX", addr.trx, 1e-3) { fetchTrx(addr.trx) },
                Quad("USDT", addr.trx, 1e-3) { fetchUsdtTrc20(addr.trx) }
            )

            for (c in checks) {
                if (c.address.isBlank()) continue
                val bal = withContext(Dispatchers.IO) { c.fetch() } ?: continue
                val key = "bal_${c.symbol}"
                val before = if (prefs.contains(key)) prefs.getString(key, null)?.toDoubleOrNull() else null
                prefs.edit().putString(key, bal.toString()).apply()
                if (before == null) continue                 // 1er passage : on mémorise
                val delta = bal - before
                if (delta > c.dust && notifPrefs.txAlerts.value) notify(c.symbol, delta)
            }
            checkLowBalance()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
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
                    java.text.NumberFormat.getNumberInstance(java.util.Locale.FRANCE).format(threshold.toLong())
                )
                com.vaultex.core.util.LocalNotifier.show(applicationContext, title, body)
                notificationCenter.push(title, body)
                notifPrefs.lowBalanceNotified = true
            } else if (total >= threshold) {
                notifPrefs.lowBalanceNotified = false   // réarmé quand on repasse au-dessus
            }
        } catch (_: Exception) { }
    }

    private data class SnapMini(val totalBalanceXof: Double = 0.0)

    private fun notify(symbol: String, amount: Double) {
        val amt = BigDecimal.valueOf(amount).setScale(6, RoundingMode.DOWN)
            .stripTrailingZeros().toPlainString()
        val title = "Fonds reçus"
        val body = "Vous avez reçu $amt $symbol"
        val logo = NotifLogo.forSymbol(applicationContext, symbol)  // logo de la crypto reçue
        val n = NotificationCompat.Builder(applicationContext, VaultExApplication.FCM_DEFAULT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setLargeIcon(logo)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        applicationContext.getSystemService(NotificationManager::class.java)
            .notify("dep_$symbol".hashCode(), n)
        notificationCenter.push(title, body, symbol)
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

    companion object {
        const val WORK_NAME = "vaultex_deposit_check"
        private const val PREFS = "deposit_check_prefs"
        private const val USDT_TRC20 = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"
    }
}
