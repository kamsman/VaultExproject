package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.core.crypto.Blockchain
import com.vaultex.core.security.SecureStorage
import com.vaultex.core.crypto.WalletManager
import com.vaultex.data.repository.PriceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val secureStorage: SecureStorage,
    private val priceRepository: PriceRepository
) : ViewModel() {

    data class TokenItem(
        val symbol: String,
        val name: String,
        val blockchain: Blockchain,
        val balance: String,
        val valueUsd: String,
        val address: String = ""
    )

    data class DashboardUiState(
        val totalBalance: String = "—",
        val tokens: List<TokenItem> = emptyList(),
        val isLoading: Boolean = true,
        val addresses: WalletManager.WalletAddresses? = null
    )

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val coinGeckoIds = listOf("bitcoin", "ethereum", "binancecoin", "solana", "tron")

    init {
        loadDashboard()
    }

    fun loadDashboard() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val addresses = withContext(Dispatchers.IO) {
                    val mnemonic = secureStorage.getMnemonic() ?: return@withContext null
                    WalletManager.deriveAddresses(mnemonic)
                }
                val prices = withContext(Dispatchers.IO) {
                    priceRepository.getMultiplePrices(coinGeckoIds)
                }
                val tokens = buildTokenList(addresses, prices)
                val total = tokens.sumOf { parseUsd(it.valueUsd) }
                _uiState.value = DashboardUiState(
                    totalBalance = "%.2f".format(total),
                    tokens = tokens,
                    isLoading = false,
                    addresses = addresses
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    private fun buildTokenList(
        addresses: WalletManager.WalletAddresses?,
        prices: Map<String, Double>
    ): List<TokenItem> {
        // Balances hardcodées en attendant l'intégration des RPCs
        val btcBalance = 0.0
        val ethBalance = 0.0
        val bnbBalance = 0.0
        val solBalance = 0.0
        val trxBalance = 0.0

        val btcPrice = prices["bitcoin"] ?: 0.0
        val ethPrice = prices["ethereum"] ?: 0.0
        val bnbPrice = prices["binancecoin"] ?: 0.0
        val solPrice = prices["solana"] ?: 0.0
        val trxPrice = prices["tron"] ?: 0.0

        return listOf(
            TokenItem("BTC", "Bitcoin", Blockchain.BITCOIN,
                "%.6f".format(btcBalance), "$%.2f".format(btcBalance * btcPrice),
                addresses?.btc ?: ""),
            TokenItem("ETH", "Ethereum", Blockchain.ETHEREUM,
                "%.6f".format(ethBalance), "$%.2f".format(ethBalance * ethPrice),
                addresses?.eth ?: ""),
            TokenItem("BNB", "BNB Chain", Blockchain.BNB_CHAIN,
                "%.6f".format(bnbBalance), "$%.2f".format(bnbBalance * bnbPrice),
                addresses?.bnb ?: ""),
            TokenItem("SOL", "Solana", Blockchain.SOLANA,
                "%.6f".format(solBalance), "$%.2f".format(solBalance * solPrice),
                addresses?.sol ?: ""),
            TokenItem("TRX", "Tron", Blockchain.TRON,
                "%.6f".format(trxBalance), "$%.2f".format(trxBalance * trxPrice),
                addresses?.trx ?: "")
        )
    }

    private fun parseUsd(valueUsd: String): Double =
        valueUsd.removePrefix("$").replace(",", "").toDoubleOrNull() ?: 0.0
}
