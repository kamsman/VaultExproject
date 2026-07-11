package com.vaultex.ui.viewmodel

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.R
import com.vaultex.app.MainActivity
import com.vaultex.core.config.ApiKeys
import com.vaultex.core.crypto.Base58
import com.vaultex.core.crypto.WalletManager
import com.vaultex.core.security.SecureStorage
import com.vaultex.data.local.dao.TransactionDao
import com.vaultex.data.local.entity.TransactionEntity
import com.vaultex.data.remote.api.BitcoinApi
import com.vaultex.data.remote.api.EtherscanApi
import com.vaultex.data.remote.api.SolanaRpcApi
import com.vaultex.data.remote.api.TronApi
import com.vaultex.data.remote.dto.JsonRpcRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named

data class TxDisplay(
    val hash: String,
    val type: String,
    val chain: String,
    val amount: String,
    val date: String,
    val isIncoming: Boolean
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val transactionDao: TransactionDao,
    private val secureStorage: SecureStorage,
    private val tronApi: TronApi,
    private val bitcoinApi: BitcoinApi,
    @Named("etherscan") private val etherscanApi: EtherscanApi,
    @Named("bscscan") private val bscScanApi: EtherscanApi,
    private val solanaRpc: SolanaRpcApi,
    private val notificationCenter: com.vaultex.core.session.NotificationCenter,
    private val notifPrefs: com.vaultex.core.session.NotifPrefs,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        const val CHANNEL_ID = "vaultex_notifications"
        private const val USDT_TRC20_CONTRACT = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"
    }

    private val _filteredChain = MutableStateFlow<String?>(null)
    val filteredChain: StateFlow<String?> = _filteredChain.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val transactions: StateFlow<List<TransactionEntity>> =
        transactionDao.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filtered: StateFlow<List<TxDisplay>> = combine(transactions, _filteredChain) { txs, chain ->
        val list = if (chain == null) txs else txs.filter { it.blockchain == chain }
        list.map { it.toDisplay() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init { syncBlockchainHistory() }

    fun filterByChain(chain: String?) = _filteredChain.update { chain }
    fun refresh() = syncBlockchainHistory()

    private fun syncBlockchainHistory() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val mnemonic = secureStorage.getMnemonic() ?: return@launch
                val addresses = withContext(Dispatchers.IO) { WalletManager.deriveAddresses(mnemonic, secureStorage.getPassphrase()) }
                withContext(Dispatchers.IO) {
                    fetchTronHistory(addresses.trx)
                    fetchBtcHistory(addresses.btc)
                    fetchEvmHistory(etherscanApi, addresses.eth, "ETH", "ETH", ApiKeys.ETHERSCAN)
                    fetchEvmHistory(bscScanApi, addresses.bnb, "BNB", "BNB", ApiKeys.BSCSCAN)
                    fetchSolHistory(addresses.sol)
                }
            } catch (_: Exception) {
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ─── TRON TRX ────────────────────────────────────────────────────

    private suspend fun fetchTronHistory(address: String) {
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
                // TronGrid returns hex addresses; convert our Base58 wallet address to hex for comparison
                val ourHex = tronBase58ToHex(address)
                val isIncoming = toAddrHex.equals(ourHex, ignoreCase = true)

                val amountSun = when (val raw = paramValue?.get("amount")) {
                    is Double -> raw.toLong()
                    is Long   -> raw
                    is Int    -> raw.toLong()
                    else      -> 0L
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
                    sendLocalNotification("Vous avez reçu $amount TRX", "Transaction TRON confirmée", "TRX", entity.timestamp)
                }
            }
        } catch (_: Exception) {}

        // ─── TRC20 (USDT) ────────────────────────────────────────────
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
                    sendLocalNotification("Vous avez reçu $amount $symbol", "Transaction TRC20 confirmée", symbol, entity.timestamp)
                }
            }
        } catch (_: Exception) {}
    }

    // ─── BITCOIN ─────────────────────────────────────────────────────

    private suspend fun fetchBtcHistory(address: String) {
        try {
            val txList = bitcoinApi.getTransactions(address)
            for (tx in txList) {
                val received = tx.vout.filter { it.address == address }.sumOf { it.value }
                val sent = tx.vin.mapNotNull { it.prevout }.filter { it.address == address }.sumOf { it.value }
                val isIncoming = received > sent
                val netSatoshi = if (isIncoming) received - sent else sent - received
                val amount = "%.6f".format(netSatoshi / 1e8)

                val entity = TransactionEntity(
                    hash = tx.txid,
                    type = if (isIncoming) "received" else "sent",
                    blockchain = "BTC",
                    fromAddress = address,
                    toAddress = address,
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
                    sendLocalNotification("Vous avez reçu $amount BTC", "Transaction Bitcoin confirmée", "BTC", entity.timestamp)
                }
            }
        } catch (_: Exception) {}
    }

    // ─── ETH / BNB via Etherscan-compatible API ───────────────────────

    private suspend fun fetchEvmHistory(
        api: EtherscanApi,
        address: String,
        blockchain: String,
        symbol: String,
        apiKey: String = ""
    ) {
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
                    sendLocalNotification("Vous avez reçu $amount $symbol", "Transaction $blockchain confirmée", symbol, entity.timestamp)
                }
            }
        } catch (_: Exception) {}
    }

    // ─── SOLANA ──────────────────────────────────────────────────────

    private suspend fun fetchSolHistory(address: String) {
        try {
            val sigsRes = solanaRpc.rpcCall(
                JsonRpcRequest(
                    "getSignaturesForAddress",
                    mutableListOf(address as Any, mapOf("limit" to 50) as Any)
                )
            )
            @Suppress("UNCHECKED_CAST")
            val sigs = sigsRes.result as? List<Map<String, Any>> ?: return

            for (sig in sigs.take(20)) {
                val signature = sig["signature"] as? String ?: continue
                val blockTime = (sig["blockTime"] as? Double)?.toLong() ?: continue
                val err = sig["err"]
                val status = if (err == null) "confirmed" else "failed"

                // Récupérer le détail de la transaction pour le montant
                val txRes = solanaRpc.rpcCall(
                    JsonRpcRequest(
                        "getTransaction",
                        mutableListOf(
                            signature as Any,
                            mapOf("encoding" to "jsonParsed", "maxSupportedTransactionVersion" to 0) as Any
                        )
                    )
                )
                @Suppress("UNCHECKED_CAST")
                val txData = txRes.result as? Map<String, Any>
                val meta = txData?.get("meta") as? Map<String, Any>
                val preBalances = meta?.get("preBalances") as? List<Double>
                val postBalances = meta?.get("postBalances") as? List<Double>

                // Index 0 = fee payer / sender
                val pre0 = preBalances?.getOrNull(0) ?: 0.0
                val post0 = postBalances?.getOrNull(0) ?: 0.0
                val fee = (meta?.get("fee") as? Double) ?: 0.0
                val delta = post0 - pre0 + fee  // positive = received, negative = sent
                val isIncoming = delta > 0
                val lamports = Math.abs(delta).toLong()
                val amount = "%.6f".format(lamports / 1e9)

                val entity = TransactionEntity(
                    hash = signature,
                    type = if (isIncoming) "received" else "sent",
                    blockchain = "SOL",
                    fromAddress = address,
                    toAddress = address,
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
                    sendLocalNotification("Vous avez reçu $amount SOL", "Transaction Solana confirmée", "SOL", entity.timestamp)
                }
            }
        } catch (_: Exception) {}
    }

    // ─── Notification ────────────────────────────────────────────────

    /**
     * true si l'horodatage est RÉCENT (< 15 min). Sans ce garde-fou, la 1re
     * synchro (ou une re-synchro après changement de wallet / réinstall, qui
     * repeuple la base vide) redéclencherait une notif « Vous avez reçu… » pour
     * CHAQUE ancienne réception → « des notifications sans que rien ne se
     * passe ». Normalise secondes→ms (les timestamps ne sont pas homogènes).
     */
    private fun isRecentTx(ts: Long): Boolean {
        if (ts <= 0L) return false
        val ms = if (ts < 1_000_000_000_000L) ts * 1000L else ts
        return System.currentTimeMillis() - ms in 0 until 15 * 60 * 1000L
    }

    private fun sendLocalNotification(title: String, body: String, symbol: String? = null, timestamp: Long) {
        if (!notifPrefs.txAlerts.value) return   // Alertes transactions désactivées
        if (!isRecentTx(timestamp)) return       // ancienne tx (backfill) → pas de notif
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

    // ─── Helpers ─────────────────────────────────────────────────────

    /** Converts a Tron Base58Check address (T…) to the hex format TronGrid uses (41XXXX…). */
    private fun tronBase58ToHex(address: String): String = try {
        val decoded = Base58.decode(address)   // 25 bytes: version(1) + payload(20) + checksum(4)
        decoded.copyOfRange(0, 21).joinToString("") { "%02x".format(it) }
    } catch (_: Exception) { "" }

    // ─── Display mapper ──────────────────────────────────────────────

    private fun TransactionEntity.toDisplay(): TxDisplay {
        val todaySdf = SimpleDateFormat("dd/MM", Locale.getDefault())
        val fullSdf  = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        val timeSdf  = SimpleDateFormat("HH:mm", Locale.getDefault())
        val today = todaySdf.format(Date())
        val txDay = todaySdf.format(Date(timestamp))
        val dateFormatted = if (txDay == today) "Auj. ${timeSdf.format(Date(timestamp))}"
                            else fullSdf.format(Date(timestamp))
        val sign = if (type == "received") "+" else "-"
        return TxDisplay(
            hash = hash,
            type = if (type == "received") "Reçu" else "Envoyé",
            chain = blockchain,
            amount = "$sign$amount $tokenSymbol",
            date = dateFormatted,
            isIncoming = type == "received"
        )
    }
}
