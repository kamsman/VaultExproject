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
    val isIncoming: Boolean,
    /**
     * Échange, et donc ni une entrée ni une sortie.
     *
     * [isIncoming] seul ne suffisait pas : un booléen ne sait dire que deux
     * choses, et il y en a trois. Tout ce qui n'était pas « reçu » tombait
     * dans « envoyé », flèche rouge et signe moins compris.
     */
    val estEchange: Boolean = false
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
                    // Transferts de TOKENS (SHIB, USDC…) — APRÈS la synchro
                    // native : leur REPLACE remplace la ligne « 0 ETH » porteuse.
                    syncService.syncEthTokens(addresses.eth)
                    syncService.syncBnbTokens(addresses.bnb)
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
        val ctx = com.vaultex.core.session.LocaleManager.wrap(context)
        val dateFormatted =
            if (txDay == today) ctx.getString(com.vaultex.R.string.history_today) + " " + timeSdf.format(Date(timestamp))
            else fullSdf.format(Date(timestamp))
        /*
        ═══════════════════════════════════════════════════════════════════
        TROIS TYPES, ET IL N'Y EN AVAIT QUE DEUX
        ═══════════════════════════════════════════════════════════════════

        « received » d'un côté, TOUT LE RESTE de l'autre. Un échange tombait
        donc dans « Envoyé », avec sa flèche rouge et son signe moins.

        Constaté sur appareil : l'utilisateur croyait ses échanges absents de
        l'historique. Ils y étaient — déguisés en envois, et impossibles à
        distinguer d'un vrai envoi.

        Un échange n'est ni une entrée ni une sortie : les fonds quittent une
        monnaie et reviennent dans une autre. Lui coller un signe moins
        affirme une perte qui n'a pas eu lieu. Il n'en porte donc aucun.

        Le symbole est tronqué avant la flèche, comme sur l'accueil :
        recordSwap écrit « USDT→BTC » dans tokenSymbol, et « -0.5 USDT→BTC »
        ne veut rien dire.

        Les trois libellés viennent des ressources. Ils étaient écrits en
        français dans le code, donc affichés en français en anglais comme en
        arabe — et « Auj. » avec eux.
        ═══════════════════════════════════════════════════════════════════
        */
        val echange = type == "swap"
        val entrant = type == "received"
        val signe = when {
            echange -> ""
            entrant -> "+"
            else -> "-"
        }
        val symbole = if (echange) tokenSymbol.substringBefore("→") else tokenSymbol
        val libelle = ctx.getString(
            when {
                echange -> com.vaultex.R.string.swapped
                entrant -> com.vaultex.R.string.received
                else -> com.vaultex.R.string.sent
            }
        )
        return TxDisplay(
            hash = hash,
            type = libelle,
            chain = blockchain,
            amount = "$signe$amount $symbole",
            date = dateFormatted,
            isIncoming = entrant,
            estEchange = echange
        )
    }
}
