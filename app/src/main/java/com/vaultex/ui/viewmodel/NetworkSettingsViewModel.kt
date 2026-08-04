package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.vaultex.core.security.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class RpcEntry(
    val chain: String,
    val label: String,
    val default: String,
    val current: String
)

data class NetworkSettingsState(
    val entries: List<RpcEntry> = emptyList(),
    val saved: Boolean = false
)

@HiltViewModel
class NetworkSettingsViewModel @Inject constructor(
    private val secureStorage: SecureStorage
) : ViewModel() {

    private val defaults = listOf(
        Triple("eth",        "Ethereum (ETH)",           "https://rpc.ankr.com/eth/"),
        Triple("bnb",        "BNB Smart Chain",          "https://bsc-dataseed.binance.org/"),
        Triple("btc",        "Bitcoin (BTC)",            "https://blockstream.info/api/"),
        Triple("sol",        "Solana (SOL)",             "https://api.mainnet-beta.solana.com/"),
        Triple("trx",        "TRON (TRX/USDT)",         "https://api.trongrid.io/"),
        Triple("etherscan",  "Etherscan (ETH history)",  "https://api.etherscan.io/"),
        Triple("bscscan",    "BscScan (BNB history)",    "https://api.bscscan.com/"),
        Triple("changenow",  "ChangeNOW (swap)",         "https://api.changenow.io/v1/")
    )

    private val _state = MutableStateFlow(NetworkSettingsState(
        entries = defaults.map { (chain, label, def) ->
            RpcEntry(chain, label, def, secureStorage.getRpcUrl(chain, def))
        }
    ))
    val state: StateFlow<NetworkSettingsState> = _state.asStateFlow()

    fun setUrl(chain: String, url: String) {
        _state.update { s ->
            s.copy(
                entries = s.entries.map { if (it.chain == chain) it.copy(current = url) else it },
                saved = false
            )
        }
    }

    fun save() {
        _state.value.entries.forEach { secureStorage.setRpcUrl(it.chain, it.current) }
        _state.update { it.copy(saved = true) }
    }

    fun reset() {
        _state.update { s ->
            s.copy(
                entries = s.entries.map { it.copy(current = it.default) },
                saved = false
            )
        }
    }
}
