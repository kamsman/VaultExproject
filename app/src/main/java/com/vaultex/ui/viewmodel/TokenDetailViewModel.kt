package com.vaultex.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.core.crypto.WalletManager
import com.vaultex.core.security.SecureStorage
import com.vaultex.data.remote.api.CoinGeckoApi
import com.vaultex.data.repository.MarketRepository
import com.vaultex.data.repository.TokenRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class TokenDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val secureStorage: SecureStorage,
    private val marketRepository: MarketRepository,
    private val coinGeckoApi: CoinGeckoApi,
    private val priceFallback: com.vaultex.data.repository.PriceFallbackSource,
    private val tokenRepository: TokenRepository
) : ViewModel() {

    data class State(
        val symbol: String = "",
        val name: String = "",
        val priceUsd: Double = 0.0,
        val change24h: Double = 0.0,
        val marketCapUsd: Double = 0.0,
        val chartPrices: List<Double> = emptyList(),
        val address: String = "",
        // Solde détenu (depuis l'instantané portefeuille).
        val amountFormatted: String = "",
        val valueUsd: Double = 0.0,
        val isLoading: Boolean = true,
        val error: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val gson = com.google.gson.Gson()

    private val symbol: String = savedStateHandle["symbol"] ?: "ETH"

    /*
    IDENTIFIANT DE COTATION — SANS VALEUR DE REPLI. Le type est `String?`, et
    c'est délibéré : la ligne se terminait autrefois par `?: "ethereum"`, si
    bien que tout jeton absent de la table empruntait le cours d'Ethereum.
    Constaté sur appareil : les fiches SHIB et DAI affichaient toutes deux
    « 2 340,09 $ · +18,10 % · 282 milliards de capitalisation » — les chiffres
    d'ETH. Quelqu'un pouvait croire ses SHIB valorisés à 2 340 $ l'unité.

    Sur un portefeuille, un prix FAUX est plus grave qu'un prix absent : on
    prend des décisions dessus, et rien ne signale qu'il est faux. Un jeton
    inconnu n'affiche donc aucun cours. Ne jamais remettre de repli ici.

    La table elle-même vit dans CoinIds — elle est partagée avec l'écran
    Alertes et le worker de prix.
     */
    private val coinGeckoId: String? = com.vaultex.core.market.CoinIds.BY_SYMBOL[symbol]

    private val tokenNames = mapOf(
        "BTC" to "Bitcoin", "ETH" to "Ethereum", "BNB" to "BNB",
        "SOL" to "Solana", "TRX" to "Tron",
        "USDT" to "Tether TRC20", "USDT-ETH" to "Tether ERC20", "USDT-BNB" to "Tether BEP20",
        "USDC" to "USD Coin", "DAI" to "Dai", "LINK" to "Chainlink",
        "SHIB" to "Shiba Inu", "PEPE" to "Pepe", "UNI" to "Uniswap",
        "AAVE" to "Aave", "WBTC" to "Wrapped Bitcoin", "CAKE" to "PancakeSwap"
    )

    init {
        _state.update { it.copy(symbol = symbol, name = tokenNames[symbol] ?: symbol) }
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val address = withContext(Dispatchers.IO) {
                    val mnemonic = secureStorage.getMnemonic() ?: return@withContext ""
                    val a = WalletManager.deriveAddresses(mnemonic, secureStorage.getPassphrase())
                    when (symbol) {
                        "BTC" -> a.btc
                        "ETH", "USDT-ETH" -> a.eth
                        "BNB", "USDT-BNB" -> a.bnb
                        "SOL" -> a.sol
                        "TRX", "USDT" -> a.trx
                        else -> a.eth
                    }
                }

                // Solde détenu : lu dans l'instantané portefeuille (aucun appel réseau).
                val (amountFormatted, valueUsd) = balanceFor(symbol)

                // Prix / variation / market cap / sparkline 7j : cache marché
                // (cache-first → évite le rate-limit CoinGecko qui cassait l'écran).
                val dto = coinGeckoId?.let { id ->
                    withContext(Dispatchers.IO) {
                        try { marketRepository.getMarket(id).firstOrNull() } catch (_: Exception) { null }
                    }
                }

                /*
                 * JETON IMPORTÉ PAR ADRESSE DE CONTRAT.
                 *
                 * Il n'a pas d'identifiant de cotation — il n'est dans aucune
                 * table de symboles, et il ne peut pas y être : l'utilisateur
                 * peut ajouter n'importe quel contrat. C'est le cas le plus
                 * courant après les jetons du registre.
                 *
                 * CoinGecko sait pourtant le coter, par son ADRESSE. C'est déjà
                 * ce que fait l'écran d'accueil — d'où l'incohérence observée :
                 * la conversion du solde était juste, l'en-tête était faux.
                 *
                 * On emprunte donc le même chemin. Ce point de terminaison
                 * renvoie le prix et la variation sur 24 h, mais NI la
                 * capitalisation NI l'historique 7 jours : ces deux blocs
                 * restent masqués, ce qui est honnête plutôt que remplis de
                 * valeurs empruntées.
                 */
                val jetonImporte = if (dto != null) null else withContext(Dispatchers.IO) {
                    try {
                        tokenRepository.getCustom()
                            .firstOrNull { it.symbol.equals(symbol, ignoreCase = true) }
                    } catch (_: Exception) { null }
                }
                val prixContrat = jetonImporte?.let { jeton ->
                    withContext(Dispatchers.IO) {
                        try {
                            val plateforme =
                                if (jeton.blockchain == "BNB") "binance-smart-chain" else "ethereum"
                            coinGeckoApi.getTokenPrice(plateforme, jeton.contractAddress.lowercase())
                                .entries.firstOrNull()?.value
                        } catch (_: Exception) { null }
                    }
                }
                /*
                 * DERNIER RECOURS : la source de prix sans quota.
                 *
                 * Les deux chemins ci-dessus passent par CoinGecko. Quand son
                 * quota mensuel est épuisé, ils rendent tous les deux zéro et
                 * la fiche s'ouvre sur « $0,00 » — y compris pour Bitcoin.
                 *
                 * Le repli n'est tenté que sur les monnaies dont
                 * l'application connaît elle-même l'identifiant : jamais sur
                 * un contrat importé, dont le symbole est choisi par l'auteur
                 * du contrat et ne prouve rien. Voir PriceFallbackSource.
                 *
                 * La capitalisation n'est pas récupérable par cette voie :
                 * son bloc reste masqué plutôt que rempli d'une valeur
                 * empruntée à une autre monnaie.
                 */
                val prixDejaConnu = dto?.currentPrice?.takeIf { it > 0.0 }
                    ?: prixContrat?.usd?.takeIf { it > 0.0 }
                val prixSecours = if (prixDejaConnu != null) null else coinGeckoId?.let { id ->
                    withContext(Dispatchers.IO) {
                        try { priceFallback.pricesByCoinGeckoId(listOf(id))[id] }
                        catch (_: Exception) { null }
                    }
                }
                // Dernier recours, si tout le réseau a échoué : ce que l'accueil
                // sait déjà. Voir coursInstantane.
                val instantane = if (prixDejaConnu != null || (prixSecours?.usd ?: 0.0) > 0.0) null
                    else coursInstantane(symbol)

                // Graphique : d'abord le sparkline du dto ; sinon repli market_chart.
                val chartData = dto?.sparkline_in_7d?.price
                    ?: coinGeckoId?.let { id ->
                        withContext(Dispatchers.IO) {
                            try { coinGeckoApi.getMarketChart(id, "usd", 7).prices.map { it[1] } }
                            catch (_: Exception) { emptyList() }
                        }
                    } ?: emptyList()

                _state.update {
                    it.copy(
                        // Nom réel du jeton importé : la table des symboles ne
                        // peut pas le connaître, mais la fiche enregistrée à
                        // l'ajout du contrat le porte. Sans cela l'écran
                        // affichait « SHIB / SHIB » au lieu de « SHIB / Shiba Inu ».
                        name = tokenNames[symbol] ?: jetonImporte?.name?.takeIf { it.isNotBlank() } ?: symbol,
                        priceUsd = prixDejaConnu ?: prixSecours?.usd?.takeIf { it > 0.0 }
                            ?: instantane?.first ?: 0.0,
                        change24h = dto?.change24h?.takeIf { it != 0.0 }
                            ?: prixContrat?.change24h?.takeIf { it != 0.0 }
                            ?: prixSecours?.change24h?.takeIf { it != 0.0 }
                            ?: instantane?.second ?: 0.0,
                        marketCapUsd = dto?.marketCap ?: prixContrat?.marketCap ?: 0.0,
                        chartPrices = chartData,
                        address = address,
                        amountFormatted = amountFormatted,
                        valueUsd = valueUsd,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    /** Solde + valeur USD de [sym] depuis l'instantané portefeuille persté. */
    private fun balanceFor(sym: String): Pair<String, Double> {
        val json = secureStorage.getPortfolioSnapshot() ?: return "" to 0.0
        return try {
            val snap = gson.fromJson(json, SnapshotLite::class.java)
            val t = snap?.tokens?.firstOrNull { it.symbol == sym }
            (t?.amountFormatted ?: "") to (t?.valueUsd ?: 0.0)
        } catch (_: Exception) { "" to 0.0 }
    }

    /**
     * Cours DÉJÀ CONNU du portefeuille, pour ce symbole.
     *
     * ═══════════════════════════════════════════════════════════════════
     * LA FICHE NE DOIT JAMAIS EN SAVOIR MOINS QUE L'ACCUEIL
     * ═══════════════════════════════════════════════════════════════════
     *
     * L'instantané du portefeuille contient déjà le prix unitaire et la
     * variation de chaque monnaie, y compris des jetons importés. La fiche
     * n'y lisait pourtant que le solde, et refaisait tout le reste par le
     * réseau.
     *
     * Conséquence observée sur appareil : l'accueil affichait un cours pour
     * un jeton importé, et sa fiche annonçait « Cours indisponible ». Deux
     * écrans de la même application, en désaccord sur le même jeton, à la
     * même seconde. L'utilisateur ne peut se fier ni à l'un ni à l'autre.
     *
     * Ce recours est le DERNIER de la chaîne : les sources réseau restent
     * prioritaires, car elles apportent en plus la capitalisation et la
     * courbe. Mais quand toutes échouent, la fiche reprend ce que l'accueil
     * sait déjà — au lieu de prétendre ne rien savoir.
     * ═══════════════════════════════════════════════════════════════════
     */
    private fun coursInstantane(sym: String): Pair<Double, Double>? {
        val json = secureStorage.getPortfolioSnapshot() ?: return null
        return try {
            val snap = gson.fromJson(json, SnapshotLite::class.java)
            val t = snap?.tokens?.firstOrNull { it.symbol == sym } ?: return null
            if (t.priceUsd > 0.0) t.priceUsd to t.changePercent24h else null
        } catch (_: Exception) { null }
    }

    private data class SnapshotLite(val tokens: List<TokenLite>?)
    private data class TokenLite(
        val symbol: String = "",
        val amountFormatted: String = "",
        val valueUsd: Double = 0.0,
        val priceUsd: Double = 0.0,
        val changePercent24h: Double = 0.0
    )
}
