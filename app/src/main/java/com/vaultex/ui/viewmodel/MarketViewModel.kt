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
    private val coinGeckoApi: com.vaultex.data.remote.api.CoinGeckoApi,
    private val tokenInfoService: com.vaultex.core.tx.TokenInfoService,
    private val tokenRepository: com.vaultex.data.repository.TokenRepository,
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

    /** true = le chargement a échoué ET aucune donnée (cache vide) → écran « réessayer ». */
    private val _loadError = MutableStateFlow(false)
    val loadError: StateFlow<Boolean> = _loadError

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

    // ─── Monnaie du marché NON échangeable mais RECEVABLE via Ethereum/BSC ───
    // Beaucoup de monnaies listées existent en réalité en tant que token
    // ERC-20/BEP-20 sans être dans le registre swap. On le détecte via les
    // « platforms » CoinGecko, validées EN VRAI sur la chaîne (comme l'ajout
    // manuel d'un token) avant d'activer Envoyer/Recevoir — jamais de contrat
    // non vérifié.
    data class ReceivableToken(
        val symbol: String,
        val contractAddress: String,
        val chainTicker: String,
        val decimals: Int
    )

    private val _receivableToken = MutableStateFlow<ReceivableToken?>(null)
    val receivableToken: StateFlow<ReceivableToken?> = _receivableToken

    private val _receivableChecking = MutableStateFlow(false)
    val receivableChecking: StateFlow<Boolean> = _receivableChecking

    private var receivableCheckedForId: String? = null

    /**
     * À appeler UNIQUEMENT si la monnaie n'est pas déjà dans le registre swap
     * (vérification faite côté écran). Résout un éventuel contrat ETH/BSC et
     * le valide sur la chaîne — jamais de contrat non vérifié.
     *
     * N'ÉCRIT RIEN dans le portefeuille : cette fonction sert à savoir si la
     * monnaie est recevable, pas à décider qu'on la détient.
     */
    fun checkReceivable(coinId: String) {
        if (receivableCheckedForId == coinId) return
        receivableCheckedForId = coinId
        _receivableToken.value = null
        viewModelScope.launch {
            _receivableChecking.value = true
            try {
                val detail = withContext(Dispatchers.IO) { coinGeckoApi.getCoinPlatforms(coinId) }
                val platforms = detail.platforms ?: emptyMap()
                // Ethereum d'abord (marché le plus liquide), puis BSC.
                val candidates = listOfNotNull(
                    platforms["ethereum"]?.takeIf { it.isNotBlank() }?.let { "ETH" to it },
                    platforms["binance-smart-chain"]?.takeIf { it.isNotBlank() }?.let { "BNB" to it }
                )
                for ((chainTicker, contract) in candidates) {
                    val info = withContext(Dispatchers.IO) { tokenInfoService.fetch(chainTicker, contract) }
                    if (info != null) {
                        /*
                        ON RÉSOUT, ON N'AJOUTE PAS.

                        Cette boucle enregistrait le jeton dans le portefeuille
                        ici même. Conséquence : ouvrir la fiche d'une monnaie
                        depuis le Marché — juste pour en regarder le cours —
                        la faisait apparaître sur l'accueil. Quelques minutes
                        de curiosité suffisaient à peupler le tableau de bord
                        de jetons que l'on ne possède pas.

                        Regarder n'est pas posséder. L'ajout n'a lieu que si
                        l'on touche Envoyer ou Recevoir, c'est-à-dire quand il
                        devient réellement nécessaire — voir enregistrerPourUsage().
                        */
                        _receivableToken.value =
                            ReceivableToken(info.symbol, contract, chainTicker, info.decimals)
                        break
                    }
                }
            } catch (_: Exception) {
                // API indisponible : on reste sur « consultation seule », sans bloquer l'écran.
            } finally {
                _receivableChecking.value = false
            }
        }
    }

    /**
     * Enregistre le jeton résolu — appelé au moment d'ENVOYER ou de RECEVOIR.
     *
     * Envoyer et recevoir supposent que le jeton existe dans le portefeuille :
     * c'est ce circuit-là qui sait lire son solde et bâtir la transaction. On
     * l'y met donc, mais seulement à cet instant — le seul où l'utilisateur a
     * manifesté qu'il veut s'en servir.
     */
    fun enregistrerPourUsage() {
        val jeton = _receivableToken.value ?: return
        viewModelScope.launch {
            try {
                tokenRepository.addToken(
                    com.vaultex.data.local.entity.TokenEntity(
                        contractAddress = jeton.contractAddress,
                        blockchain = jeton.chainTicker,
                        symbol = jeton.symbol,
                        name = jeton.symbol,
                        decimals = jeton.decimals,
                        iconUrl = null,
                        isCustom = true,
                        isHidden = false
                    )
                )
            } catch (_: Exception) { /* déjà présent : sans impact */ }
        }
    }

    // ─── Courbe de prix (détail token) ─────────────────────────
    private val _chart = MutableStateFlow<List<Float>>(emptyList())
    val chart: StateFlow<List<Float>> = _chart

    private val _chartLoading = MutableStateFlow(false)
    val chartLoading: StateFlow<Boolean> = _chartLoading

    /*
    ═══════════════════════════════════════════════════════════════════════
    RECHERCHE — sur les ~19 000 monnaies, plus sur les 100 affichées
    ═══════════════════════════════════════════════════════════════════════

    L'écran filtrait la liste déjà téléchargée. Passé le rang 100, la
    recherche ne rendait donc rien : pour l'utilisateur, une barre de
    recherche qui ne trouve pas une monnaie qui existe est un défaut, pas
    une limite comprise.

    La saisie est maintenant envoyée au relais, mais pas à chaque caractère :
    on attend DELAI_FRAPPE après la dernière touche. Taper « pepe » produit
    ainsi UN appel, pas quatre. La recherche précédente est annulée dès
    qu'une nouvelle frappe arrive, sinon deux réponses en vol pourraient
    s'afficher dans le désordre et montrer le résultat d'une requête
    abandonnée.
    */
    private val _searchResults = MutableStateFlow<List<CoinGeckoMarketDto>>(emptyList())
    val searchResults: StateFlow<List<CoinGeckoMarketDto>> = _searchResults

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching

    /**
     * La recherche réseau a ÉCHOUÉ — à distinguer de « rien ne correspond ».
     *
     * L'écran affichait « Aucune cryptomonnaie ne correspond » dans les deux
     * cas. Il accusait donc le catalogue d'une panne venue du réseau, et
     * personne ne pouvait faire la différence depuis l'écran : c'est ce qui
     * a rendu l'absence de Celo impossible à diagnostiquer.
     */
    private val _searchError = MutableStateFlow(false)
    val searchError: StateFlow<Boolean> = _searchError

    private var rechercheEnCours: kotlinx.coroutines.Job? = null
    private var derniereRequete: String = ""

    fun onSearchQueryChanged(query: String) {
        rechercheEnCours?.cancel()
        val q = query.trim()
        derniereRequete = q
        if (q.length < 2) {
            _searching.value = false
            _searchError.value = false
            _searchResults.value = emptyList()
            return
        }
        rechercheEnCours = viewModelScope.launch {
            kotlinx.coroutines.delay(DELAI_FRAPPE)
            _searching.value = true
            _searchError.value = false
            try {
                val trouve = withContext(Dispatchers.IO) { repository.search(q) }
                _searchResults.value = trouve.monnaies
                _searchError.value = trouve.echecReseau
            } catch (e: Exception) {
                // Une annulation n'est pas un échec : elle signifie simplement
                // que l'utilisateur a continué à taper. La relancer serait
                // rompre la structure des coroutines.
                if (e is kotlinx.coroutines.CancellationException) throw e
                _searchResults.value = emptyList()
                _searchError.value = true
            } finally {
                _searching.value = false
            }
        }
    }

    /** Rejoue la dernière recherche, à la demande de l'utilisateur. */
    fun retrySearch() {
        if (derniereRequete.length >= 2) onSearchQueryChanged(derniereRequete)
    }

    /*
    ═══════════════════════════════════════════════════════════════════════
    DÉFILEMENT — pages suivantes du classement
    ═══════════════════════════════════════════════════════════════════════

    La première page en compte désormais 250 (le maximum d'un seul appel).
    Les suivantes ne partent que si l'on atteint vraiment le bas de la
    liste, et l'on s'arrête à PAGES_MAX : au rang 1 000, on est très loin de
    ce qu'un portefeuille a vocation à parcourir, et la recherche prend le
    relais pour tout le reste.
    */
    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore

    /**
     * Le chargement d'une page suivante a ÉCHOUÉ (réseau, quota, réponse
     * inattendue) — par opposition à « il n'y a plus rien à charger ».
     *
     * La première version confondait les deux : tout échec posait
     * simplement « fin de liste » et la liste s'arrêtait sans un mot. On ne
     * pouvait donc pas distinguer un appel jamais parti d'un appel refusé,
     * ni depuis l'écran ni depuis un rapport d'utilisateur. Un échec doit se
     * voir et pouvoir être réessayé.
     */
    private val _moreError = MutableStateFlow(false)
    val moreError: StateFlow<Boolean> = _moreError

    /** true quand tout le classement accessible a été chargé. */
    private val _endReached = MutableStateFlow(false)
    val endReached: StateFlow<Boolean> = _endReached

    private var pageChargee = 1

    fun loadMoreMarkets() {
        if (_loadingMore.value || _endReached.value || _moreError.value || _isLoading.value) return
        if (pageChargee >= PAGES_MAX) { _endReached.value = true; return }
        viewModelScope.launch {
            _loadingMore.value = true
            _moreError.value = false
            try {
                val suivante = pageChargee + 1
                val list = withContext(Dispatchers.IO) { repository.getMarketsPage(suivante) }
                if (list.isEmpty()) {
                    // Liste réellement épuisée : CoinGecko n'a plus de rangs.
                    _endReached.value = true
                } else {
                    pageChargee = suivante
                    _markets.value = (_markets.value + list).distinctBy { it.id }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                com.vaultex.core.monitoring.reportUnlessCancelled("CoinGecko/page", e)
                _moreError.value = true
            } finally {
                _loadingMore.value = false
            }
        }
    }

    /** Nouvelle tentative après un échec, déclenchée par l'utilisateur. */
    fun retryLoadMore() {
        _moreError.value = false
        loadMoreMarkets()
    }

    fun loadMarkets() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val list = withContext(Dispatchers.IO) { repository.getMarkets() }
                _markets.value = list
                _isStale.value = repository.lastFromCache
                _loadError.value = list.isEmpty()
                // Un rechargement repart du haut : sans cette remise à zéro,
                // le défilement croirait avoir déjà descendu les pages.
                pageChargee = 1
                _endReached.value = false
                _moreError.value = false
            } catch (e: Exception) {
                e.printStackTrace()
                _loadError.value = _markets.value.isEmpty()
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

    private companion object {
        /** Silence après la dernière touche avant d'interroger le réseau. */
        const val DELAI_FRAPPE = 350L

        /** 4 × 250 = les 1 000 premières capitalisations ; au-delà, la recherche. */
        const val PAGES_MAX = 4
    }
}
