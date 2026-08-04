package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.core.tx.TokenInfoService
import com.vaultex.data.local.entity.TokenEntity
import com.vaultex.data.remote.api.CoinGeckoApi
import com.vaultex.data.repository.TokenRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Ajout d'un token personnalisé ERC-20 / BEP-20 par adresse de contrat.
 * On lit symbole + décimales directement sur la chaîne (source de vérité),
 * puis on enregistre le token. Le solde et le prix sont chargés ensuite par
 * le portefeuille (PortfolioViewModel.loadCustomTokens).
 */
@HiltViewModel
class AddTokenViewModel @Inject constructor(
    private val tokenInfoService: TokenInfoService,
    private val tokenRepository: TokenRepository,
    private val coinGeckoApi: CoinGeckoApi
) : ViewModel() {

    data class State(
        val network: String = "ETH",          // "ETH" ou "BNB"
        val contract: String = "",
        val detecting: Boolean = false,
        val detectedSymbol: String? = null,
        val detectedDecimals: Int? = null,
        // Prix de marché trouvé pour vérification (null = non coté sur CoinGecko).
        val detectedPriceUsd: Double? = null,
        val priceUnknown: Boolean = false,
        val error: String? = null,
        val saved: Boolean = false
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun setNetwork(network: String) = _state.update {
        // Changer de réseau invalide la détection précédente.
        it.copy(network = network, detectedSymbol = null, detectedDecimals = null, error = null)
    }

    fun setContract(value: String) = _state.update {
        it.copy(contract = value.trim(), detectedSymbol = null, detectedDecimals = null, error = null, saved = false)
    }

    private fun isValidContract(addr: String): Boolean =
        addr.startsWith("0x") && addr.length == 42 &&
            addr.drop(2).all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }

    fun detect() {
        val s = _state.value
        if (s.detecting) return
        if (!isValidContract(s.contract)) {
            _state.update { it.copy(error = ERR_INVALID) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(detecting = true, error = null) }
            val info = withContext(Dispatchers.IO) {
                tokenInfoService.fetch(s.network, s.contract)
            }
            if (info == null) {
                _state.update { it.copy(detecting = false, error = ERR_NOT_FOUND) }
            } else {
                // Vérification supplémentaire : prix de marché (CoinGecko par
                // contrat). Un token réel mais non coté reste ajoutable (prix
                // inconnu signalé), mais l'utilisateur voit que le contrat est lu.
                val platform = if (s.network == "BNB") "binance-smart-chain" else "ethereum"
                val priceUsd = withContext(Dispatchers.IO) {
                    try {
                        coinGeckoApi.getTokenPrice(platform, s.contract.lowercase())
                            .entries.firstOrNull()?.value?.usd?.takeIf { it > 0.0 }
                    } catch (_: Exception) { null }
                }
                _state.update {
                    it.copy(
                        detecting = false,
                        detectedSymbol = info.symbol,
                        detectedDecimals = info.decimals,
                        detectedPriceUsd = priceUsd,
                        priceUnknown = priceUsd == null,
                        error = null
                    )
                }
            }
        }
    }

    fun save() {
        val s = _state.value
        val symbol = s.detectedSymbol ?: return
        val decimals = s.detectedDecimals ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    tokenRepository.addToken(
                        TokenEntity(
                            contractAddress = s.contract,
                            blockchain = s.network,
                            symbol = symbol,
                            name = symbol,
                            decimals = decimals,
                            iconUrl = null,
                            isCustom = true,
                            isHidden = false
                        )
                    )
                }
                _state.update { it.copy(saved = true) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: ERR_NOT_FOUND) }
            }
        }
    }

    companion object {
        const val ERR_INVALID = "INVALID"
        const val ERR_NOT_FOUND = "NOT_FOUND"
    }
}
