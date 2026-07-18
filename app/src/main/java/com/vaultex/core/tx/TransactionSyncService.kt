package com.vaultex.core.tx

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.vaultex.R
import com.vaultex.app.MainActivity
import com.vaultex.core.config.ApiKeys
import com.vaultex.core.crypto.Base58
import com.vaultex.data.local.dao.TransactionDao
import com.vaultex.data.local.entity.TransactionEntity
import com.vaultex.data.remote.api.BitcoinApi
import com.vaultex.data.remote.api.EtherscanApi
import com.vaultex.data.remote.api.SolanaRpcApi
import com.vaultex.data.remote.api.TronApi
import com.vaultex.data.remote.dto.JsonRpcRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Récupère l'historique on-chain PAR ADRESSE, l'enregistre dans la table
 * locale `transactions` (source de « Récent » au Dashboard et de l'écran
 * Historique) et notifie la cloche pour toute réception RÉCENTE (< 15 min).
 *
 * Extrait de HistoryViewModel pour être appelable aussi depuis
 * DepositCheckWorker : sans ce partage, seule l'ouverture de l'écran
 * Historique alimentait « Récent » — la détection RAPIDE des dépôts
 * (diff de solde, en arrière-plan) notifiait la cloche instantanément mais
 * ne pouvait pas écrire l'entrée correspondante (elle ne connaît que le
 * montant, pas le hash) → désalignement cloche/« Récent » signalé en test
 * réel. En appelant ICI la même logique dès qu'un solde bouge, l'entrée
 * réelle (avec son vrai hash) arrive aussi vite que la cloche.
 */
@Singleton
class TransactionSyncService @Inject constructor(
    private val transactionDao: TransactionDao,
    private val bitcoinApi: BitcoinApi,
    private val tronApi: TronApi,
    private val solanaRpc: SolanaRpcApi,
    @Named("etherscan") private val etherscanApi: EtherscanApi,
    @Named("bscscan") private val bscScanApi: EtherscanApi,
    private val notificationCenter: com.vaultex.core.session.NotificationCenter,
    private val notifPrefs: com.vaultex.core.session.NotifPrefs,
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "vaultex_notifications"
        private const val USDT_TRC20_CONTRACT = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"
    }

    // ─── TRON (TRX natif + TRC20/USDT) ────────────────────────────────

    suspend fun syncTron(address: String) {
        try {
            val txList = tronApi.getTransactions(address, limit = 50)
            for (tx in txList.data) {
                val contract = tx.rawData.contract.firstOrNull() ?: continue
                val type = contract["type"] as? String ?: "TransferContract"
                if (type != "TransferContract") continue

                @Suppress("UNCHECKED_CAST")
                val paramValue = (contract["parameter"] as? Map<String, Any>)
                    ?.get("value") as? Map<String, Any>

                val toAddrHex = paramValue?.get("to_address") as? String ?: ""
                val ourHex = tronBase58ToHex(address)
                val isIncoming = toAddrHex.equals(ourHex, ignoreCase = true)

                val amountSun = when (val raw = paramValue?.get("amount")) {
                    is Double -> raw.toLong()
                    is Long -> raw
                    is Int -> raw.toLong()
                    else -> 0L
                }
                val amount = "%.6f".format(amountSun / 1_000_000.0)
                val retCode = tx.ret?.firstOrNull()?.get("contractRet") ?: "SUCCESS"
                val status = if (retCode == "SUCCESS") "confirmed" else "failed"

                val entity = TransactionEntity(
                    hash = tx.txID,
                    type = if (isIncoming) "received" else "sent",
                    blockchain = "TRX",
                    fromAddress = paramValue?.get("owner_address") as? String ?: address,
                    toAddress = toAddrHex.ifEmpty { address },
                    amount = amount,
                    tokenSymbol = "TRX",
                    fee = "0",
                    status = status,
                    timestamp = tx.timestamp,
                    confirmations = if (status == "confirmed") 1 else 0,
                    blockNumber = null
                )
                val inserted = transactionDao.insertIgnore(entity)
                if (inserted > 0 && isIncoming) {
                    notify("Vous avez reçu $amount TRX", "Transaction TRON confirmée", "TRX", entity.timestamp)
                }
            }
        } catch (_: Exception) {}

        try {
            val trc20List = tronApi.getTrc20Transactions(address, limit = 50)
            for (tx in trc20List.data) {
                val symbol = tx.tokenInfo?.get("symbol") as? String ?: continue
                val isIncoming = tx.to.equals(address, ignoreCase = true)
                val decimals = (tx.tokenInfo["decimals"] as? Double)?.toInt() ?: 6
                val rawAmount = tx.value.toLongOrNull() ?: 0L
                val divisor = Math.pow(10.0, decimals.toDouble())
                val amount = "%.2f".format(rawAmount / divisor)

                val entity = TransactionEntity(
                    hash = tx.txId,
                    type = if (isIncoming) "received" else "sent",
                    blockchain = if (symbol == "USDT") "USDT" else "TRX",
                    fromAddress = tx.from,
                    toAddress = tx.to,
                    amount = amount,
                    tokenSymbol = symbol,
                    fee = "0",
                    status = "confirmed",
                    timestamp = tx.timestamp,
                    confirmations = 1,
                    blockNumber = null
                )
                val inserted = transactionDao.insertIgnore(entity)
                if (inserted > 0 && isIncoming) {
                    notify("Vous avez reçu $amount $symbol", "Transaction TRC20 confirmée", symbol, entity.timestamp)
                }
            }
        } catch (_: Exception) {}
    }

    // ─── BITCOIN ─────────────────────────────────────────────────────

    suspend fun syncBtc(address: String) {
        try {
            val txList = bitcoinApi.getTransactions(address)
            for (tx in txList) {
                val received = tx.vout.filter { it.address == address }.sumOf { it.value }
                val sent = tx.vin.mapNotNull { it.prevout }.filter { it.address == address }.sumOf { it.value }
                val isIncoming = received > sent
                val netSatoshi = if (isIncoming) received - sent else sent - received
                val amount = "%.6f".format(netSatoshi / 1e8)

                val counterOut = tx.vout.filter { it.address != null && it.address != address }
                    .maxByOrNull { it.value }?.address
                val counterIn = tx.vin.mapNotNull { it.prevout?.address }.firstOrNull { it != address }

                val entity = TransactionEntity(
                    hash = tx.txid,
                    type = if (isIncoming) "received" else "sent",
                    blockchain = "BTC",
                    fromAddress = if (isIncoming) (counterIn ?: "") else address,
                    toAddress = if (isIncoming) address else (counterOut ?: address),
                    amount = amount,
                    tokenSymbol = "BTC",
                    fee = "%.6f".format(tx.fee / 1e8),
                    status = if (tx.status.confirmed) "confirmed" else "pending",
                    timestamp = tx.status.blockTime ?: System.currentTimeMillis(),
                    confirmations = if (tx.status.confirmed) 1 else 0,
                    blockNumber = tx.status.blockHeight
                )
                val inserted = transactionDao.insertIgnore(entity)
                if (inserted > 0 && isIncoming) {
                    notify("Vous avez reçu $amount BTC", "Transaction Bitcoin confirmée", "BTC", entity.timestamp)
                }
            }
        } catch (_: Exception) {}
    }

    // ─── ETH / BNB via API compatible Etherscan ────────────────────────

    suspend fun syncEth(address: String) = syncEvm(etherscanApi, address, "ETH", "ETH", ApiKeys.ETHERSCAN)
    suspend fun syncBnb(address: String) = syncEvm(bscScanApi, address, "BNB", "BNB", ApiKeys.BSCSCAN)

    private suspend fun syncEvm(api: EtherscanApi, address: String, blockchain: String, symbol: String, apiKey: String) {
        try {
            val response = api.getTransactions(address = address, apiKey = apiKey)
            if (response.status != "1") return
            for (tx in response.result ?: emptyList()) {
                val isIncoming = tx.to.equals(address, ignoreCase = true)
                val amountWei = tx.value.toBigDecimalOrNull() ?: BigDecimal.ZERO
                val amount = "%.6f".format(amountWei.divide(BigDecimal("1000000000000000000")).toDouble())
                val status = if (tx.isError == "0") "confirmed" else "failed"
                val confirmations = tx.confirmations.toIntOrNull() ?: 0

                val entity = TransactionEntity(
                    hash = tx.hash,
                    type = if (isIncoming) "received" else "sent",
                    blockchain = blockchain,
                    fromAddress = tx.from,
                    toAddress = tx.to,
                    amount = amount,
                    tokenSymbol = symbol,
                    fee = "0",
                    status = status,
                    timestamp = tx.timeStamp.toLongOrNull()?.times(1000) ?: System.currentTimeMillis(),
                    confirmations = confirmations,
                    blockNumber = null
                )
                val inserted = transactionDao.insertIgnore(entity)
                if (inserted > 0 && isIncoming && status == "confirmed") {
                    notify("Vous avez reçu $amount $symbol", "Transaction $blockchain confirmée", symbol, entity.timestamp)
                }
            }
        } catch (_: Exception) {}
    }

    // ─── Transferts de TOKENS ERC-20 / BEP-20 (tokentx) ───────────────
    // txlist ne liste QUE les transactions natives : sans cette synchro,
    // un SHIB/USDC reçu n'apparaissait ni dans « Récent », ni dans
    // l'Historique, ni à la cloche — seul le solde finissait par bouger
    // (la panique « mes SHIB ne sont jamais arrivés » du test réel).

    suspend fun syncEthTokens(address: String) = syncEvmTokens(etherscanApi, address, "ETH", ApiKeys.ETHERSCAN)
    suspend fun syncBnbTokens(address: String) = syncEvmTokens(bscScanApi, address, "BNB", ApiKeys.BSCSCAN)

    private suspend fun syncEvmTokens(api: EtherscanApi, address: String, blockchain: String, apiKey: String) {
        try {
            val response = api.getTokenTransactions(address = address, apiKey = apiKey)
            if (response.status != "1") return
            for (tx in response.result ?: emptyList()) {
                val isIncoming = tx.to.equals(address, ignoreCase = true)
                val decimals = tx.tokenDecimal?.toIntOrNull() ?: 18
                val raw = tx.value.toBigDecimalOrNull() ?: BigDecimal.ZERO
                val amount = "%.6f".format(raw.divide(BigDecimal.TEN.pow(decimals)).toDouble())
                val symbol = tx.tokenSymbol?.takeIf { it.isNotBlank() } ?: "TOKEN"

                val entity = TransactionEntity(
                    hash = tx.hash,
                    type = if (isIncoming) "received" else "sent",
                    blockchain = blockchain,
                    fromAddress = tx.from,
                    toAddress = tx.to,
                    amount = amount,
                    tokenSymbol = symbol,
                    fee = "0",
                    status = "confirmed",
                    timestamp = tx.timeStamp.toLongOrNull()?.times(1000) ?: System.currentTimeMillis(),
                    confirmations = tx.confirmations.toIntOrNull() ?: 1,
                    blockNumber = null
                )
                // REPLACE volontaire : pour NOS envois de tokens, la synchro
                // native insère déjà le même hash comme « 0 ETH envoyé » (l'appel
                // du contrat) — on remplace ce bruit par l'info du token, bien
                // plus parlante. La notification, elle, ne part que si la ligne
                // n'existait pas encore (pas de doublon à chaque synchro).
                val existedBefore = transactionDao.getHash(tx.hash) != null
                transactionDao.insert(entity)
                if (!existedBefore && isIncoming) {
                    notify("Vous avez reçu $amount $symbol", "Transaction $blockchain confirmée", symbol, entity.timestamp)
                }
            }
        } catch (_: Exception) {}
    }

    // ─── SOLANA ──────────────────────────────────────────────────────

    suspend fun syncSol(address: String) {
        try {
            val sigsRes = solanaRpc.rpcCall(
                JsonRpcRequest("getSignaturesForAddress", mutableListOf(address as Any, mapOf("limit" to 50) as Any))
            )
            @Suppress("UNCHECKED_CAST")
            val sigs = sigsRes.result as? List<Map<String, Any>> ?: return

            for (sig in sigs.take(20)) {
                val signature = sig["signature"] as? String ?: continue
                val blockTime = (sig["blockTime"] as? Double)?.toLong() ?: continue
                val err = sig["err"]
                val status = if (err == null) "confirmed" else "failed"

                val txRes = solanaRpc.rpcCall(
                    JsonRpcRequest(
                        "getTransaction",
                        mutableListOf(signature as Any, mapOf("encoding" to "jsonParsed", "maxSupportedTransactionVersion" to 0) as Any)
                    )
                )
                @Suppress("UNCHECKED_CAST")
                val txData = txRes.result as? Map<String, Any>
                val meta = txData?.get("meta") as? Map<String, Any>
                val preBalances = meta?.get("preBalances") as? List<Double>
                val postBalances = meta?.get("postBalances") as? List<Double>

                @Suppress("UNCHECKED_CAST")
                val accountKeys = ((txData?.get("transaction") as? Map<String, Any>)
                    ?.get("message") as? Map<String, Any>)
                    ?.get("accountKeys") as? List<Map<String, Any>>
                val pubkeys = accountKeys?.mapNotNull { it["pubkey"] as? String } ?: emptyList()
                val myIdx = pubkeys.indexOf(address).let { if (it < 0) 0 else it }

                val preMe = preBalances?.getOrNull(myIdx) ?: 0.0
                val postMe = postBalances?.getOrNull(myIdx) ?: 0.0
                val fee = (meta?.get("fee") as? Double) ?: 0.0
                val feePaidByMe = if (myIdx == 0) fee else 0.0
                val delta = postMe - preMe + feePaidByMe
                val isIncoming = delta > 0
                val lamports = Math.abs(delta).toLong()
                val amount = "%.6f".format(lamports / 1e9)

                var counterparty = ""
                var bestMove = 0.0
                pubkeys.forEachIndexed { i, pk ->
                    if (i == myIdx) return@forEachIndexed
                    val d = (postBalances?.getOrNull(i) ?: 0.0) - (preBalances?.getOrNull(i) ?: 0.0)
                    if (isIncoming) { if (d < bestMove) { bestMove = d; counterparty = pk } }
                    else { if (d > bestMove) { bestMove = d; counterparty = pk } }
                }

                val entity = TransactionEntity(
                    hash = signature,
                    type = if (isIncoming) "received" else "sent",
                    blockchain = "SOL",
                    fromAddress = if (isIncoming) counterparty else address,
                    toAddress = if (isIncoming) address else counterparty.ifEmpty { address },
                    amount = amount,
                    tokenSymbol = "SOL",
                    fee = "%.6f".format(fee / 1e9),
                    status = status,
                    timestamp = blockTime * 1000L,
                    confirmations = if (status == "confirmed") 1 else 0,
                    blockNumber = null
                )
                val inserted = transactionDao.insertIgnore(entity)
                if (inserted > 0 && isIncoming) {
                    notify("Vous avez reçu $amount SOL", "Transaction Solana confirmée", "SOL", entity.timestamp)
                }
            }
        } catch (_: Exception) {}
    }

    // ─── Notification ────────────────────────────────────────────────

    /** true si l'horodatage est RÉCENT (< 15 min) — évite de spammer sur du backfill. */
    private fun isRecentTx(ts: Long): Boolean {
        if (ts <= 0L) return false
        val ms = if (ts < 1_000_000_000_000L) ts * 1000L else ts
        return System.currentTimeMillis() - ms in 0 until 15 * 60 * 1000L
    }

    private fun notify(title: String, body: String, symbol: String? = null, timestamp: Long) {
        if (!notifPrefs.txAlerts.value) return
        if (!isRecentTx(timestamp)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        notificationCenter.push(title, body, symbol)
        val logo = com.vaultex.service.NotifLogo.forSymbol(context, symbol)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setLargeIcon(logo)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(System.currentTimeMillis().toInt(), notification)
    }

    /** Adresse Tron Base58Check (T…) → format hex utilisé par TronGrid (41…). */
    private fun tronBase58ToHex(address: String): String = try {
        val decoded = Base58.decode(address)
        decoded.copyOfRange(0, 21).joinToString("") { "%02x".format(it) }
    } catch (_: Exception) { "" }
}
