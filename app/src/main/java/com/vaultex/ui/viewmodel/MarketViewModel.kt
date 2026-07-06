package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.data.remote.dto.CoinGeckoMarketDto
import com.vaultex.data.repository.MarketRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MarketViewModel @Inject constructor(
    private val repository: MarketRepository,
    private val secureStorage: com.vaultex.core.security.SecureStorage,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
) : ViewModel() {

    // ─── Solde détenu d'une monnaie (carte « Votre solde » du détail) ───
    data class Holding(val amount: Double, val valueUsd: Double)
    private data class SnapLite(val tokens: List<TokLite>?)
    private data class TokLite(val symbol: String = "", val amountRaw: Double = 0.0, val priceUsd: Double = 0.0)

    fun holdingOf(symbol: String): Holding? {
        return try {
            val json = secureStorage.getPortfolioSnapshot() ?: return null
            val t = com.google.gson.Gson().fromJson(json, SnapLite::class.java)
                ?.tokens?.firstOrNull { it.symbol.equals(symbol, ignoreCase = true) } ?: return null
            if (t.amountRaw > 0.0) Holding(t.amountRaw, t.amountRaw * t.priceUsd) else null
        } catch (_: Exception) { null }
    }

    private val _markets = MutableStateFlow<List<CoinGeckoMarketDto>>(emptyList())
    val markets: StateFlow<List<CoinGeckoMarketDto>> = _markets

    // ─── Bandeau global : cap. totale + dominance BTC ───────────
    private val _global = MutableStateFlow<MarketRepository.GlobalStats?>(null)
    val global: StateFlow<MarketRepository.GlobalStats?> = _global

    // ─── Favoris (persistés localement, étoile de la liste) ─────
    private val favPrefs = appContext.getSharedPreferences("vaultex_market_favs", android.content.Context.MODE_PRIVATE)
    private val _favorites = MutableStateFlow(favPrefs.getStringSet("ids", emptySet()) ?: emptySet())
    val favorites: StateFlow<Set<String>> = _favorites

    fun toggleFavorite(coinId: String) {
        val next = _favorites.value.toMutableSet().apply { if (!add(coinId)) remove(coinId) }
        _favorites.value = next
        favPrefs.edit().putStringSet("ids", next).apply()
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    /** true = la liste affichée vient du cache (hors ligne). */
    private val _isStale = MutableStateFlow(false)
    val isStale: StateFlow<Boolean> = _isStale

    // ─── Détail d'UNE pièce (écran CoinDetail) ─────────────────
    private val _coin = MutableStateFlow<CoinGeckoMarketDto?>(null)
    val coin: StateFlow<CoinGeckoMarketDto?> = _coin

    private val _coinLoading = MutableStateFlow(false)
    val coinLoading: StateFlow<Boolean> = _coinLoading

    private val _coinError = MutableStateFlow(false)
    val coinError: StateFlow<Boolean> = _coinError

    /**
     * Charge UNIQUEMENT la pièce demandée (appel léger via ids=), au lieu de
     * re-télécharger toute la liste marché. Échec/réseau ⇒ coinError = true
     * (l'écran montre « réessayer » au lieu d'un spinner infini).
     */
    fun loadCoin(coinId: String) {
        viewModelScope.launch {
            _coinLoading.value = true
            _coinError.value = false
            try {
                val result = withContext(Dispatchers.IO) { repository.getMarket(coinId) }
                _coin.value = result.firstOrNull()
                _coinError.value = (_coin.value == null)
            } catch (e: Exception) {
                _coinError.value = true
            } finally {
                _coinLoading.value = false
            }
        }
    }

    // ─── Courbe de prix (détail token) ─────────────────────────
    private val _chart = MutableStateFlow<List<Float>>(emptyList())
    val chart: StateFlow<List<Float>> = _chart

    private val _chartLoading = MutableStateFlow(false)
    val chartLoading: StateFlow<Boolean> = _chartLoading

    fun loadMarkets() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _markets.value = withContext(Dispatchers.IO) { repository.getMarkets() }
                _isStale.value = repository.lastFromCache
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
        // Bandeau global (best-effort : le Marché reste utilisable sans).
        viewModelScope.launch {
            try {
                val g = withContext(Dispatchers.IO) { repository.getGlobal() }
                if (g != null) _global.value = g
            } catch (_: Exception) { /* bandeau simplement absent */ }
        }
    }

    /** Charge la courbe de prix d'un token pour une période donnée. */
    fun loadChart(coinId: String, days: Int) {
        viewModelScope.launch {
            _chartLoading.value = true
            try {
                val dto = withContext(Dispatchers.IO) { repository.getMarketChart(coinId, days) }
                _chart.value = dto.prices.mapNotNull { it.getOrNull(1)?.toFloat() }
            } catch (e: Exception) {
                _chart.value = emptyList()
            } finally {
                _chartLoading.value = false
            }
        }
    }
}
