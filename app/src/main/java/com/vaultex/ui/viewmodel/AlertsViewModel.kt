package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.data.local.entity.PriceAlertEntity
import com.vaultex.core.session.PriceMoveSettings
import com.vaultex.data.remote.api.CoinGeckoApi
import com.vaultex.domain.usecase.PriceAlertUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val priceAlertUseCase: PriceAlertUseCase,
    private val coinGeckoApi: CoinGeckoApi,
    private val priceFallback: com.vaultex.data.repository.PriceFallbackSource,
    private val moveSettings: PriceMoveSettings
) : ViewModel() {

    /* ─── Alertes automatiques de variation (actives par défaut) ───────────
       Réglages exposés à l'écran. Le worker relit les mêmes préférences à
       chaque exécution : aucune resynchronisation n'est nécessaire. */
    private val _movesEnabled = MutableStateFlow(moveSettings.enabled)
    val movesEnabled: StateFlow<Boolean> = _movesEnabled

    private val _moveThreshold = MutableStateFlow(moveSettings.thresholdPercent)
    val moveThreshold: StateFlow<Int> = _moveThreshold

    fun setMovesEnabled(enabled: Boolean) {
        moveSettings.enabled = enabled
        _movesEnabled.value = enabled
    }

    fun setMoveThreshold(percent: Int) {
        moveSettings.thresholdPercent = percent
        _moveThreshold.value = percent
    }

    val alerts: StateFlow<List<PriceAlertEntity>> = priceAlertUseCase.observeAlerts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Prix courants en FCFA, indexés par symbole token. */
    private val _currentPricesXof = MutableStateFlow<Map<String, Double>>(emptyMap())
    val currentPricesXof: StateFlow<Map<String, Double>> = _currentPricesXof

    init {
        refreshPrices()
    }

    fun refreshPrices() {
        viewModelScope.launch {
            val coursPrincipaux: Map<String, com.vaultex.data.remote.dto.CoinGeckoPriceDto> =
                withContext(Dispatchers.IO) {
                    try {
                        coinGeckoApi.getPrices(
                            ids = SYMBOL_TO_COINGECKO_ID.values.joinToString(","),
                            vsCurrencies = "xof",
                            include24hChange = false,
                            includeMarketCap = false
                        )
                    } catch (_: Exception) {
                        emptyMap()
                    }
                }
            /*
             * Même source de secours que l'accueil.
             *
             * Sans elle, cet écran restait le dernier à afficher des prix
             * vides quand le quota de CoinGecko était épuisé — pendant que
             * l'accueil, lui, affichait des montants justes. Deux écrans de
             * la même application en désaccord sur le prix du Bitcoin, c'est
             * une raison de douter des DEUX.
             *
             * Le prix montré ici n'est pas décoratif : c'est celui que
             * l'utilisateur regarde pour choisir le seuil de son alerte.
             */
            val nonCotees = SYMBOL_TO_COINGECKO_ID.values.filter {
                (coursPrincipaux[it]?.xof ?: 0.0) <= 0.0
            }
            val prices = if (nonCotees.isEmpty()) coursPrincipaux else {
                coursPrincipaux + withContext(Dispatchers.IO) {
                    try { priceFallback.pricesByCoinGeckoId(nonCotees) }
                    catch (_: Exception) { emptyMap() }
                }
            }
            _currentPricesXof.value = SYMBOL_TO_COINGECKO_ID.mapNotNull { (symbol, id) ->
                prices[id]?.xof?.takeIf { it > 0 }?.let { symbol to it }
            }.toMap()
        }
    }

    fun createAlert(symbol: String, condition: String, targetPrice: String) {
        viewModelScope.launch { priceAlertUseCase.createAlert(symbol, condition, targetPrice) }
    }

    fun toggleAlert(id: String, active: Boolean) {
        viewModelScope.launch { priceAlertUseCase.toggleAlert(id, active) }
    }

    fun deleteAlert(id: String) {
        viewModelScope.launch { priceAlertUseCase.deleteAlert(id) }
    }

    companion object {
        val SYMBOL_TO_COINGECKO_ID = mapOf(
            "BTC" to "bitcoin",
            "ETH" to "ethereum",
            "BNB" to "binancecoin",
            "SOL" to "solana",
            "TRX" to "tron",
            "USDT" to "tether"
        )
    }
}
