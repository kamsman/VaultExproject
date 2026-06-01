package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.core.crypto.Blockchain
import com.vaultex.core.crypto.WalletManager
import com.vaultex.core.security.SecureStorage
import com.vaultex.data.repository.BalanceRepository
import com.vaultex.data.repository.PriceRepository
import com.vaultex.data.repository.TxStatusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val secureStorage: SecureStorage,
    private val priceRepository: PriceRepository,
    private val balanceRepository: BalanceRepository,
    private val txStatusRepository: TxStatusRepository
) : ViewModel() {

    data class TokenItem(
        val symbol: String,
        val name: String,
        val blockchain: Blockchain,
        val balance: String,
        val balanceRaw: Double,
        val valueUsd: String,
        val address: String = ""
    )

    data class DashboardUiState(
        val totalBalance: String = "—",
        val tokens: List<TokenItem> = emptyList(),
        val isLoading: Boolean = true,
        val addresses: WalletManager.WalletAddresses? = null,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val coinGeckoIds = listOf("bitcoin", "ethereum", "binancecoin", "solana", "tron")

    init { loadDashboard() }

    fun loadDashboard() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            // Refresh pending tx statuses in background — non-blocking
            launch { try { txStatusRepository.refreshPendingStatuses() } catch (_: Exception) {} }
            try {
                val addresses = withContext(Dispatchers.IO) {
                    val mnemonic = secureStorage.getMnemonic() ?: return@withContext null
                    WalletManager.deriveAddresses(mnemonic)
                }

                // Prices et balances en parallèle
                val pricesDeferred = async(Dispatchers.IO) {
                    priceRepository.getMultiplePrices(coinGeckoIds)
                }
                val balancesDeferred = if (addresses != null) {
                    async(Dispatchers.IO) { balanceRepository.fetchAll(addresses) }
                } else null

                val prices = pricesDeferred.await()
                val balances = balancesDeferred?.await()

                val tokens = buildTokenList(addresses, prices, balances)
                val total = tokens.sumOf { it.balanceRaw * priceForSymbol(it.symbol, prices) }

                _uiState.value = DashboardUiState(
                    totalBalance = "%.2f".format(total),
                    tokens = tokens,
                    isLoading = false,
                    addresses = addresses
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun tokenBySymbol(symbol: String): TokenItem? = _uiState.value.tokens.find { it.symbol == symbol }

    private fun buildTokenList(
        addresses: WalletManager.WalletAddresses?,
        prices: Map<String, Double>,
        balances: BalanceRepository.Balances?
    ): List<TokenItem> {
        val b = balances ?: BalanceRepository.Balances()
        val btcPrice  = prices["bitcoin"] ?: 0.0
        val ethPrice  = prices["ethereum"] ?: 0.0
        val bnbPrice  = prices["binancecoin"] ?: 0.0
        val solPrice  = prices["solana"] ?: 0.0
        val trxPrice  = prices["tron"] ?: 0.0

        return listOf(
            token("BTC", "Bitcoin",   Blockchain.BITCOIN,   b.btc, btcPrice, addresses?.btc),
            token("ETH", "Ethereum",  Blockchain.ETHEREUM,  b.eth, ethPrice, addresses?.eth),
            token("BNB", "BNB Chain", Blockchain.BNB_CHAIN, b.bnb, bnbPrice, addresses?.bnb),
            token("SOL", "Solana",    Blockchain.SOLANA,    b.sol, solPrice, addresses?.sol),
            token("TRX", "Tron",      Blockchain.TRON,      b.trx, trxPrice, addresses?.trx)
        )
    }

    private fun token(
        symbol: String, name: String, blockchain: Blockchain,
        balance: Double, price: Double, address: String?
    ) = TokenItem(
        symbol = symbol, name = name, blockchain = blockchain,
        balance = if (balance == 0.0) "0.000000" else "%.6f".format(balance),
        balanceRaw = balance,
        valueUsd = "$%.2f".format(balance * price),
        address = address ?: ""
    )

    private fun priceForSymbol(symbol: String, prices: Map<String, Double>): Double = when (symbol) {
        "BTC" -> prices["bitcoin"] ?: 0.0
        "ETH" -> prices["ethereum"] ?: 0.0
        "BNB" -> prices["binancecoin"] ?: 0.0
        "SOL" -> prices["solana"] ?: 0.0
        "TRX" -> prices["tron"] ?: 0.0
        else  -> 0.0
    }
}
