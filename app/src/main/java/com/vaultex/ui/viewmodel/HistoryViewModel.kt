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
import com.vaultex.core.crypto.WalletManager
import com.vaultex.core.security.SecureStorage
import com.vaultex.data.local.dao.TransactionDao
import com.vaultex.data.local.entity.TransactionEntity
import com.vaultex.data.remote.api.BitcoinApi
import com.vaultex.data.remote.api.TronApi
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

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
                val addresses = withContext(Dispatchers.IO) { WalletManager.deriveAddresses(mnemonic) }

                withContext(Dispatchers.IO) {
                    fetchTronHistory(addresses.trx)
                    fetchBtcHistory(addresses.btc)
                }
            } catch (_: Exception) {
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun fetchTronHistory(address: String) {
        try {
            val txList = tronApi.getTransactions(address, limit = 50)
            for (tx in txList.data) {
                val contract = tx.rawData.contract.firstOrNull() ?: continue
                val type = contract["type"] as? String ?: "TransferContract"
                val isIncoming = type == "TransferContract" && run {
                    @Suppress("UNCHECKED_CAST")
                    val value = (contract["parameter"] as? Map<String, Any>)?.get("value") as? Map<String, Any>
                    val toAddr = value?.get("to_address") as? String ?: ""
                    toAddr.equals(address, ignoreCase = true) ||
                            toAddr.replace("41", "T").startsWith("T")
                }
                val retCode = tx.ret?.firstOrNull()?.get("contractRet") ?: "SUCCESS"
                val status = if (retCode == "SUCCESS") "confirmed" else "failed"
                val entity = TransactionEntity(
                    hash = tx.txID,
                    type = if (isIncoming) "received" else "sent",
                    blockchain = "TRX",
                    fromAddress = address,
                    toAddress = address,
                    amount = "TRX",
                    tokenSymbol = "TRX",
                    fee = "0",
                    status = status,
                    timestamp = tx.timestamp,
                    confirmations = if (status == "confirmed") 1 else 0,
                    blockNumber = null
                )
                val inserted = transactionDao.insertIgnore(entity)
                if (inserted > 0 && isIncoming) {
                    sendLocalNotification("Nouvelle transaction TRX", "Vous avez reçu des TRX")
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
                    sendLocalNotification(
                        "Vous avez reçu $amount $symbol",
                        "Transaction TRC20 confirmée"
                    )
                }
            }
        } catch (_: Exception) {}
    }

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
                    sendLocalNotification("Vous avez reçu $amount BTC", "Transaction Bitcoin confirmée")
                }
            }
        } catch (_: Exception) {}
    }

    private fun sendLocalNotification(title: String, body: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun TransactionEntity.toDisplay(): TxDisplay {
        val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        val todaySdf = SimpleDateFormat("dd/MM", Locale.getDefault())
        val today = todaySdf.format(Date())
        val txDate = todaySdf.format(Date(timestamp))
        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        val dateFormatted = if (txDate == today) "Auj. $timeStr" else sdf.format(Date(timestamp))

        val sign = if (type == "received") "+" else "-"
        val amountStr = "$sign$amount $tokenSymbol"

        return TxDisplay(
            hash = hash,
            type = if (type == "received") "Reçu" else "Envoyé",
            chain = blockchain,
            amount = amountStr,
            date = dateFormatted,
            isIncoming = type == "received"
        )
    }
}
