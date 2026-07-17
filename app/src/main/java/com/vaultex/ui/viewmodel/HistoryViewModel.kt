package com.vaultex.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.core.crypto.WalletManager
import com.vaultex.core.security.SecureStorage
import com.vaultex.data.local.dao.TransactionDao
import com.vaultex.data.local.entity.TransactionEntity
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
    private val syncService: com.vaultex.core.tx.TransactionSyncService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        const val CHANNEL_ID = com.vaultex.core.tx.TransactionSyncService.CHANNEL_ID
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
                    // Purge UNE FOIS des historiques BTC/SOL : les anciennes lignes
                    // portaient notre propre adresse en De ET À (insertIgnore ne
                    // les corrigerait jamais). Elles se resynchronisent juste
                    // après avec la vraie contrepartie.
                    val fixPrefs = context.getSharedPreferences("history_fixes", android.content.Context.MODE_PRIVATE)
                    if (!fixPrefs.getBoolean("addr_fix_v1", false)) {
                        try {
                            transactionDao.deleteByBlockchain("BTC")
                            transactionDao.deleteByBlockchain("SOL")
                            fixPrefs.edit().putBoolean("addr_fix_v1", true).apply()
                        } catch (_: Exception) { }
                    }
                    // Récupération/insertion/notification déléguées à un service
                    // PARTAGÉ (TransactionSyncService) : DepositCheckWorker appelle
                    // la même logique dès qu'un solde bouge, pour que « Récent »
                    // s'aligne sur la cloche même en dehors de cet écran.
                    syncService.syncTron(addresses.trx)
                    syncService.syncBtc(addresses.btc)
                    syncService.syncEth(addresses.eth)
                    syncService.syncBnb(addresses.bnb)
                    syncService.syncSol(addresses.sol)
                }
            } catch (_: Exception) {
            } finally {
                _isLoading.value = false
            }
        }
    }

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
