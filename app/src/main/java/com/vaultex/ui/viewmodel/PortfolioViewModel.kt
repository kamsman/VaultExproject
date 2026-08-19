package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.core.crypto.Blockchain
import com.vaultex.core.crypto.WalletManager
import com.vaultex.core.security.SecureStorage
import com.vaultex.data.remote.api.BitcoinApi
import com.vaultex.data.remote.api.CoinGeckoApi
import com.vaultex.data.remote.api.EvmRpcApi
import com.vaultex.data.remote.api.SolanaRpcApi
import com.vaultex.data.remote.api.TronApi
import com.vaultex.data.remote.dto.JsonRpcRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Named

data class TokenBalance(
    val symbol: String,
    val name: String,
    val amountFormatted: String,
    val valueXof: Double,
    val changePercent24h: Double,
    val colorHex: String,
    val blockchain: Blockchain,
    val valueUsd: Double = 0.0,
    val valueEur: Double = 0.0,
    // Prix unitaire de marché (indépendant du solde) — évite les « prix 00 ».
    val priceUsd: Double = 0.0,
    val priceEur: Double = 0.0,
    val priceXof: Double = 0.0,
    // Solde EXACT (non arrondi) — utilisé par le bouton Max pour ne pas dépasser.
    val amountRaw: Double = 0.0,
    // Token personnalisé ajouté par l'utilisateur (contrat ERC-20/BEP-20).
    // contractAddress est NULLABLE : un ancien instantané (cache) sérialisé
    // avant l'ajout de ce champ le désérialise à null via Gson ; un champ
    // non-null ferait planter .copy() (« parameter specified as non-null is null »).
    val isCustom: Boolean = false,
    val contractAddress: String? = null,
    val decimals: Int = 0
)

data class PortfolioState(
    val totalBalanceXof: Double = 0.0,
    val totalBalanceUsd: Double = 0.0,
    val totalBalanceEur: Double = 0.0,
    val totalChangePercent: Double = 0.0,
    val tokens: List<TokenBalance> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastUpdated: Long = 0L,   // epoch ms de la dernière synchro réussie (#5)
    val isFromCache: Boolean = false,
    // Vrai si au moins un solde n'a pas pu être lu (réseau/RPC). Permet de ne
    // JAMAIS confondre « lecture échouée » avec « solde nul » (fonds reçus mais
    // invisibles parce que le nœud public a rejeté l'appel).
    val balancesUnavailable: Boolean = false,
    /**
     * true UNIQUEMENT si AUCUN solde n'a pu être lu et qu'aucun cache n'existe.
     * À ne pas confondre avec [balancesUnavailable], qui est vrai dès qu'UNE
     * seule chaîne sur huit a échoué — cas très courant avec des nœuds publics,
     * et qui n'empêche nullement d'afficher un total correct.
     */
    val balancesAllUnknown: Boolean = false,
    /**
     * Monnaies illisibles QUI PORTAIENT DES FONDS — la seule situation où le
     * total affiché est réellement incomplet, et donc la seule qui mérite un
     * avertissement.
     *
     * [balancesUnavailable] ne convient pas pour ça : il devient vrai dès
     * qu'une des huit lectures échoue, y compris sur une monnaie vide. Avec des
     * nœuds publics, ça arrive presque à chaque rafraîchissement — le bandeau
     * s'affichait donc en permanence sur un total pourtant exact, jusqu'à ne
     * plus rien vouloir dire.
     */
    val staleFundedSymbols: List<String> = emptyList()
)

@HiltViewModel
class PortfolioViewModel @Inject constructor(
    private val secureStorage: SecureStorage,
    private val coinGeckoApi: CoinGeckoApi,
    @Named("eth") private val ethRpc: EvmRpcApi,
    @Named("bnb") private val bnbRpc: EvmRpcApi,
    private val bitcoinApi: BitcoinApi,
    private val solanaRpc: SolanaRpcApi,
    private val tronApi: TronApi,
    private val balanceVisibility: com.vaultex.core.session.BalanceVisibilityController,
    private val currencyController: com.vaultex.core.session.CurrencyController,
    private val walletNameController: com.vaultex.core.session.WalletNameController,
    private val assetVisibility: com.vaultex.core.session.AssetVisibilityController,
    private val tokenRepository: com.vaultex.data.repository.TokenRepository,
    private val pendingTxStore: com.vaultex.core.session.PendingTxStore,
    private val pendingTxManager: com.vaultex.core.tx.PendingTxManager,
    private val pushRegistrar: com.vaultex.service.PushRegistrar,
    private val notificationCenter: com.vaultex.core.session.NotificationCenter,
    private val transactionDao: com.vaultex.data.local.dao.TransactionDao,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
) : ViewModel() {

    /** Nombre de notifications non lues (pastille cloche du Dashboard). */
    val unreadNotifs: StateFlow<Int> = notificationCenter.unreadCount

    /** 3 dernières transactions (section « Activité récente » du Dashboard). */
    val recentTxs: StateFlow<List<com.vaultex.data.local.entity.TransactionEntity>> =
        transactionDao.observeAll()
            .map { list -> list.sortedByDescending { it.timestamp }.take(3) }
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    /** Adresse EVM principale (ETH/BNB) pour le bouton « Copier » du Dashboard. */
    fun fetchMainAddress(onReady: (String?) -> Unit) {
        viewModelScope.launch {
            val addr = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val m = secureStorage.getMnemonic() ?: return@withContext null
                    com.vaultex.core.crypto.WalletManager.deriveAddresses(m, secureStorage.getPassphrase()).eth
                } catch (_: Exception) { null }
            }
            onReady(addr)
        }
    }

    /** Fonds ENTRANTS non confirmés (ex. BTC en mempool) — alimente aussi le badge. */
    private val _incomingPending = MutableStateFlow<Set<String>>(emptySet())

    /** Symboles avec une transaction NON confirmée — sortante (suivi d'envoi)
     *  OU entrante (dépôt en mempool) : badge « En attente » du dashboard. */
    val pendingSymbols: StateFlow<Set<String>> = kotlinx.coroutines.flow.combine(
        pendingTxStore.items, _incomingPending
    ) { list, incoming -> list.filter { !it.confirmed }.map { it.symbol }.toSet() + incoming }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptySet())

    /** Devise d'affichage choisie (USD/EUR/XOF). */
    val currency: StateFlow<String> = currencyController.currency

    /** Nom du wallet (affichage seul ici ; édition dans Paramètres). */
    val walletName: StateFlow<String> = walletNameController.name

    /** Monnaies activées dans « Mes actifs » (en plus de celles ayant un solde). */
    val visibleAssets: StateFlow<Set<String>> = assetVisibility.visible
    fun toggleAssetVisible(symbol: String) = assetVisibility.toggle(symbol)

    /** true si la phrase du wallet ACTIF a déjà été révélée sur l'écran
     *  Sauvegarde (clé PAR WALLET, alignée sur BackupViewModel) — pilote le
     *  bandeau « As-tu sauvegardé ta phrase ? » du Dashboard. */
    fun isPhraseBackedUp(): Boolean {
        val id = secureStorage.activeWalletId() ?: "legacy"
        return appContext.getSharedPreferences("vaultex_backup", android.content.Context.MODE_PRIVATE)
            .getBoolean("phrase_backed_up_$id", false)
    }

    /**
     * Retire un token PERSONNALISÉ (ajouté par contrat) de CE wallet — même
     * s'il a un solde. Contrairement aux monnaies natives, ce n'est pas une
     * perte : le token reste sur la blockchain à ton adresse, il suffit de
     * ré-ajouter le même contrat pour le revoir. Ne supprime la définition
     * globale que si plus AUCUN wallet ne le possède (TokenRepository.delete).
     */
    fun removeCustomToken(contractAddress: String, blockchain: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { tokenRepository.delete(contractAddress, blockchain) }
                loadPortfolio(silent = true)
            } catch (_: Exception) { }
        }
    }

    private val _state = MutableStateFlow(PortfolioState(isLoading = true))
    val state: StateFlow<PortfolioState> = _state.asStateFlow()

    /** État partagé Show / Hide Balance. */
    val balanceHidden: StateFlow<Boolean> = balanceVisibility.hidden
    fun toggleBalanceVisibility() = balanceVisibility.toggle()

    companion object {
        /**
         * Intervalle minimal entre deux balayages du registre pour une MÊME
         * adresse. Un changement de wallet le contourne : l'adresse diffère,
         * le balayage est immédiat.
         *
         * Trois minutes : assez court pour qu'un jeton reçu apparaisse pendant
         * que l'utilisateur regarde son écran, assez long pour ne pas sonder
         * une douzaine de contrats à chaque retour sur l'accueil.
         */
        private const val DELAI_RESCAN = 3 * 60 * 1000L

        private val COIN_IDS = listOf("bitcoin", "ethereum", "binancecoin", "solana", "tron", "tether")
        private const val USDT_TRC20_CONTRACT = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"
        private const val USDT_ETH_CONTRACT  = "0xdAC17F958D2ee523a2206206994597C13D831ec7"
        private const val USDT_BNB_CONTRACT  = "0x55d398326f99059fF775485246999027B3197955"
    }

    private val gson = com.google.gson.Gson()

    private data class Snapshot(
        val totalBalanceXof: Double,
        val totalBalanceUsd: Double,
        val totalChangePercent: Double,
        val tokens: List<TokenBalance>,
        val lastUpdated: Long
    )

    init {
        loadCachedSnapshot()
        loadPortfolio()
        // Reprend le suivi des transactions en attente persistées (badge « ! »).
        pendingTxManager.kick()
        // Enregistre le jeton FCM + adresses pour les push « Fonds reçus » (sans
        // effet si la Cloud Function n'est pas déployée).
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { pushRegistrar.registerBlocking() }
    }

    /** Affiche immédiatement le dernier portefeuille connu (offline-first). */
    private fun loadCachedSnapshot() {
        val json = secureStorage.getPortfolioSnapshot() ?: return
        try {
            val snap = gson.fromJson(json, Snapshot::class.java) ?: return
            _state.update {
                it.copy(
                    totalBalanceXof = snap.totalBalanceXof,
                    totalBalanceUsd = snap.totalBalanceUsd,
                    totalChangePercent = snap.totalChangePercent,
                    tokens = snap.tokens,
                    lastUpdated = snap.lastUpdated,
                    isFromCache = true
                )
            }
        } catch (_: Exception) { /* cache illisible : ignoré */ }
    }

    private fun persistSnapshot(state: PortfolioState) {
        try {
            secureStorage.savePortfolioSnapshot(
                gson.toJson(
                    Snapshot(
                        state.totalBalanceXof, state.totalBalanceUsd,
                        state.totalChangePercent, state.tokens, state.lastUpdated
                    )
                )
            )
        } catch (_: Exception) { }
    }

    fun loadPortfolio(silent: Boolean = false) {
        viewModelScope.launch {
            /*
            Un rafraîchissement « silencieux » n'a de sens que s'il y a DÉJÀ
            quelque chose à l'écran : on évite alors de faire clignoter un
            chargement par-dessus des montants corrects.

            Quand l'écran est vide, se taire produit l'effet inverse. C'est le
            cas juste après un changement de wallet : les caches du wallet
            précédent viennent d'être purgés, le nouveau n'a encore rien, et
            l'accueil affichait un « 0 » parfaitement affirmé — sur un
            portefeuille, cela se lit « ton argent a disparu ». Le temps que le
            réseau réponde, l'utilisateur a eu une frayeur pour rien.

            Donc : rien à montrer → on montre le chargement, pas un zéro.
             */
            val quiet = silent && _state.value.tokens.isNotEmpty()
            if (!quiet) _state.update { it.copy(isLoading = true, error = null) }
            try {
                val mnemonic = secureStorage.getMnemonic() ?: run {
                    _state.update { it.copy(isLoading = false, error = "Wallet non initialisé") }
                    return@launch
                }
                val addresses = withContext(Dispatchers.IO) { WalletManager.deriveAddresses(mnemonic, secureStorage.getPassphrase()) }

                val prices = withContext(Dispatchers.IO) {
                    try {
                        coinGeckoApi.getPrices(
                            ids = COIN_IDS.joinToString(","),
                            vsCurrencies = "xof,usd,eur",
                            include24hChange = true,
                            includeMarketCap = false
                        )
                    } catch (_: Exception) { emptyMap() }
                }

                // Dernier état connu : si une lecture échoue, on RÉUTILISE la
                // valeur en cache au lieu de la remettre à 0 (fonds jamais perdus).
                val prevBySymbol = _state.value.tokens.associateBy { it.symbol }
                var anyStale = false
                // Au moins une lecture a abouti : sans ce suivi, on ne peut pas
                // distinguer « une chaîne sur huit a échoué » de « rien n'a pu
                // être lu du tout » — deux situations très différentes.
                var anyFresh = false
                /*
                Monnaies dont la lecture a échoué ALORS QU'ELLES PORTENT DES FONDS.

                `anyStale` seul ne peut pas servir d'alerte utilisateur : il
                devient vrai dès qu'une des huit lectures échoue, y compris sur
                une monnaie à zéro. Or les nœuds publics échouent souvent (TRX
                et USDT-TRC20 en particulier, tant que trongrid.key est vide),
                si bien que le bandeau « Total incomplet » s'affichait presque
                en permanence — sur un total pourtant parfaitement exact.

                Un avertissement qui s'affiche tout le temps n'avertit plus de
                rien : l'utilisateur apprend à l'ignorer, et le jour où le total
                est réellement faux, il ne le voit pas. On ne signale donc que
                ce qui change vraiment le total : une monnaie illisible dont on
                sait qu'elle contenait quelque chose. Et on la NOMME, au lieu de
                dire « une monnaie ».
                 */
                val staleFunded = mutableListOf<String>()
                val mainTokens = coroutineScope {
                    val btcD     = async(Dispatchers.IO) { fetchBtcBalance(addresses.btc) }
                    val ethD     = async(Dispatchers.IO) { fetchEvmBalance(ethRpc, addresses.eth) }
                    val bnbD     = async(Dispatchers.IO) { fetchEvmBalance(bnbRpc, addresses.bnb) }
                    val solD     = async(Dispatchers.IO) { fetchSolBalance(addresses.sol) }
                    val trxD     = async(Dispatchers.IO) { fetchTrxBalance(addresses.trx) }
                    val usdtTrcD = async(Dispatchers.IO) { fetchUsdtTrc20Balance(addresses.trx) }
                    val usdtEthD = async(Dispatchers.IO) { fetchErc20Balance(ethRpc, USDT_ETH_CONTRACT, addresses.eth, 6) }
                    val usdtBnbD = async(Dispatchers.IO) { fetchErc20Balance(bnbRpc, USDT_BNB_CONTRACT, addresses.bnb, 18) }
                    val btc = btcD.await(); val eth = ethD.await()
                    val bnb = bnbD.await(); val sol = solD.await()
                    val trx = trxD.await(); val usdtTrc = usdtTrcD.await()
                    val usdtEth = usdtEthD.await(); val usdtBnb = usdtBnbD.await()

                    fun usd(id: String) = prices[id]?.usd ?: 0.0
                    fun eur(id: String) = prices[id]?.eur ?: 0.0
                    // XOF est pegué à l'EUR (655,957 XOF = 1 €). Si CoinGecko ne
                    // renvoie pas xof, on le dérive de l'EUR (jamais 0 si l'EUR existe).
                    fun xof(id: String): Double {
                        val direct = prices[id]?.xof ?: 0.0
                        return if (direct > 0.0) direct else eur(id) * 655.957
                    }
                    fun c(id: String) = prices[id]?.change24h ?: 0.0
                    fun amt(bal: Double?, decimals: Int, unit: String) =
                        if (bal == null) "—" else "%.${decimals}f $unit".format(bal)
                    fun value(bal: Double?, price: Double) = (bal ?: 0.0) * price
                    // Prix COLLANT : si l'appel CoinGecko a échoué (rate-limit) le
                    // prix live vaut 0 ; on réutilise alors le dernier prix connu
                    // (cache) au lieu d'afficher « 00 ». Une fois récupéré, le prix
                    // reste affiché même si un rafraîchissement ultérieur échoue.
                    fun stick(live: Double, prev: Double?) = if (live > 0.0) live else (prev ?: 0.0)
                    fun build(symbol: String, name: String, bal: Double?, decimals: Int, unit: String,
                              id: String, color: String, chain: Blockchain): TokenBalance {
                        val prev = prevBySymbol[symbol]
                        // Prix collants (jamais ramenés à 0 par un appel raté).
                        val pUsd = stick(usd(id), prev?.priceUsd)
                        val pEur = stick(eur(id), prev?.priceEur)
                        val pXof = stick(xof(id), prev?.priceXof)
                        val pChange = if (id in prices) c(id) else (prev?.changePercent24h ?: 0.0)
                        if (bal == null) {
                            /*
                            LECTURE ÉCHOUÉE. Le montant est INCONNU — surtout pas
                            zéro. Sans valeur précédente à réutiliser (cas d'un
                            changement de wallet, où les caches viennent d'être
                            purgés), le code retombait plus bas sur
                            `value(null, prix)` = 0.0 et présentait ce zéro comme
                            un fait. Un simple hoquet réseau annonçait donc un
                            portefeuille vide à quelqu'un qui a des fonds.
                             */
                            anyStale = true
                            // Illisible ET connu comme non vide : le total affiché
                            // est réellement incomplet, ça vaut un avertissement.
                            // Illisible mais à zéro (ou jamais vue) : le total
                            // reste juste, on se tait.
                            if ((prev?.amountRaw ?: 0.0) > 0.0) staleFunded += symbol
                            // Solde indisponible : on garde le dernier solde connu MAIS
                            // on rafraîchit le PRIX de marché (indépendant du solde),
                            // sinon ETH/USDT-ETH affichaient « Prix : $0 ».
                            prev?.let {
                                return it.copy(
                                    priceUsd = pUsd, priceEur = pEur, priceXof = pXof,
                                    changePercent24h = pChange
                                )
                            }
                        }
                        // `amt()` renvoie déjà « — » si bal est nul : la LIGNE de
                        // l'actif dit donc « inconnu », et c'est le total qui
                        // porte l'avertissement plutôt que d'afficher un zéro
                        // rassurant mais faux.
                        if (bal != null) anyFresh = true
                        else {
                            /*
                            DIAGNOSTIC. Jusqu'ici, une chaîne qui ne répondait
                            pas disparaissait en silence : impossible de savoir
                            laquelle, ni pourquoi le total semblait faux. On
                            remonte donc l'incident au bot d'administration —
                            c'est la seule facon de diagnostiquer a distance un
                            « mon solde affiche 0 » sans avoir le telephone en
                            main.
                             */
                            com.vaultex.core.monitoring.AdminBot.balanceReadFailed(symbol)
                        }
                        return TokenBalance(symbol, name, amt(bal, decimals, unit),
                            value(bal, pXof), pChange, color, chain,
                            valueUsd = value(bal, pUsd), valueEur = value(bal, pEur),
                            priceUsd = pUsd, priceEur = pEur, priceXof = pXof,
                            amountRaw = bal ?: 0.0)
                    }
                    listOf(
                        build("BTC",      "Bitcoin",      btc,     6, "BTC",  "bitcoin",     "#F7931A", Blockchain.BITCOIN),
                        build("ETH",      "Ethereum",     eth,     6, "ETH",  "ethereum",    "#627EEA", Blockchain.ETHEREUM),
                        build("BNB",      "BNB",          bnb,     6, "BNB",  "binancecoin", "#F0B90B", Blockchain.BNB_CHAIN),
                        build("SOL",      "Solana",       sol,     4, "SOL",  "solana",      "#9945FF", Blockchain.SOLANA),
                        build("TRX",      "Tron",         trx,     2, "TRX",  "tron",        "#FF060A", Blockchain.TRON),
                        build("USDT",     "Tether TRC20", usdtTrc, 2, "USDT", "tether",      "#26A17B", Blockchain.TRON),
                        build("USDT-ETH", "Tether ERC20", usdtEth, 2, "USDT", "tether",      "#26A17B", Blockchain.ETHEREUM),
                        build("USDT-BNB", "Tether BEP20", usdtBnb, 2, "USDT", "tether",      "#26A17B", Blockchain.BNB_CHAIN),
                    )
                }

                // Tokens personnalisés (ajoutés par contrat). Encapsulés dans un
                // try/catch dédié : leur lecture ne doit JAMAIS casser les 8 actifs
                // principaux. En cas d'échec, on réutilise le dernier état connu.
                val customTokens = try {
                    loadCustomTokens(addresses.eth, addresses.bnb, prevBySymbol)
                } catch (_: Exception) {
                    _state.value.tokens.filter { it.isCustom }
                }
                val tokens = mainTokens + customTokens

                // Au moins une lecture a échoué (réseau/RPC) → affichage depuis le cache.
                val balancesUnavailable = anyStale
                /*
                « On ne sait RIEN » : aucune lecture n'a abouti ET il n'y avait
                rien en mémoire. C'est le seul cas où le total ne doit pas être
                affiché — sinon on annonce un zéro inventé.

                Le premier correctif s'appuyait sur `lastUpdated == 0`, ce qui
                etait faux pour deux raisons : `lastUpdated` n'avançait QUE si
                les huit chaînes réussissaient ensemble, or il suffit qu'une
                seule échoue (fréquent avec des nœuds publics) pour qu'il reste
                bloqué à 0 indéfiniment. Le message s'affichait donc en
                permanence, sur tous les wallets, y compris avec un solde
                parfaitement lu.
                 */
                val allUnknown = !anyFresh && _state.value.tokens.isEmpty()
                val total = tokens.sumOf { it.valueXof }
                val totalUsd = tokens.sumOf { it.valueUsd }
                val totalEur = tokens.sumOf { it.valueEur }
                // Value-weighted 24h change: each token weighted by its XOF value
                val avgChange = if (total == 0.0) 0.0
                    else tokens.sumOf { it.changePercent24h * it.valueXof } / total
                val newState = _state.value.copy(
                    tokens = tokens,
                    totalBalanceXof = total,
                    totalBalanceUsd = totalUsd,
                    totalBalanceEur = totalEur,
                    totalChangePercent = avgChange,
                    isLoading = false,
                    // Plus de message d'erreur quand on retombe sur le cache : les
                    // soldes restent affichés (fusion), pas besoin d'alarmer l'utilisateur.
                    error = null,
                    // Si rien n'a pu être rafraîchi, on reste « en cache » et on
                    // conserve l'horodatage précédent (pas de fausse fraîcheur).
                    // Horodatage : avance dès qu'au moins une chaîne a répondu.
                    // Le lier à la réussite des HUIT le figeait à jamais.
                    lastUpdated = if (anyFresh) System.currentTimeMillis() else _state.value.lastUpdated,
                    isFromCache = balancesUnavailable,
                    balancesUnavailable = balancesUnavailable,
                    balancesAllUnknown = allUnknown,
                    staleFundedSymbols = staleFunded.distinct()
                )
                _state.value = newState
                // On persiste TOUJOURS : la fusion ci-dessus a déjà réinjecté le
                // dernier solde connu pour les chaînes en échec, donc l'instantané
                // n'est jamais « pire » que le précédent. Indispensable pour que
                // l'écran Envoi (bouton Max) retrouve le solde même si une autre
                // chaîne a échoué.
                persistSnapshot(newState)
            } catch (e: Exception) {
                // Offline-first : on conserve les données en cache, on signale juste l'erreur.
                _state.update { it.copy(isLoading = false, error = e.message, isFromCache = it.lastUpdated > 0L) }
            }
        }
    }

    fun refresh() = loadPortfolio()

    /** Rafraîchissement sans spinner (retour sur l'écran, polling). */
    fun refreshSilently() = loadPortfolio(silent = true)

    // Les fetch renvoient Double? : null = ÉCHEC de lecture (réseau/RPC),
    // une valeur (y compris 0.0) = solde réellement déterminé. Si la réponse
    // RPC contient une erreur explicite, on considère aussi que c'est un échec.
    /**
     * Charge les tokens personnalisés (ERC-20 / BEP-20 ajoutés par contrat) :
     * solde réel via eth_call(balanceOf) + prix de marché via CoinGecko
     * (par adresse de contrat). En cas d'échec de prix, le token reste affiché
     * avec son solde (valeur inconnue = 0), jamais d'écran cassé.
     */
    private suspend fun loadCustomTokens(
        ethAddress: String,
        bnbAddress: String,
        prevBySymbol: Map<String, TokenBalance>
    ): List<TokenBalance> = coroutineScope {
        val initial = withContext(Dispatchers.IO) { tokenRepository.getCustom() }
        // Découverte AUTO des tokens du registre reçus mais jamais activés :
        // sans elle, des SHIB envoyés vers ce wallet restaient INVISIBLES
        // (le solde n'était jamais interrogé) alors qu'ils sont sur la chaîne.
        val added = withContext(Dispatchers.IO) { discoverRegistryTokens(ethAddress, bnbAddress, initial) }
        val customs = if (added) withContext(Dispatchers.IO) { tokenRepository.getCustom() } else initial
        if (customs.isEmpty()) return@coroutineScope emptyList()
        // Index du dernier état connu par contrat (pour ne jamais remettre à 0).
        val prevByContract = prevBySymbol.values
            .filter { it.isCustom && it.contractAddress != null }
            .associateBy { it.contractAddress!!.lowercase() }
        customs.map { entity ->
            async(Dispatchers.IO) {
                val isBnb = entity.blockchain == "BNB"
                val rpc = if (isBnb) bnbRpc else ethRpc
                val address = if (isBnb) bnbAddress else ethAddress
                val chain = if (isBnb) Blockchain.BNB_CHAIN else Blockchain.ETHEREUM
                val platform = if (isBnb) "binance-smart-chain" else "ethereum"

                val bal = fetchErc20Balance(rpc, entity.contractAddress, address, entity.decimals)

                // Prix par contrat (clé = adresse en minuscules dans la réponse).
                val price = try {
                    coinGeckoApi.getTokenPrice(platform, entity.contractAddress.lowercase())
                        .entries.firstOrNull()?.value
                } catch (_: Exception) { null }
                // Prix COLLANT : si CoinGecko échoue, on réutilise le dernier prix
                // connu pour ce contrat (jamais « 00 » une fois récupéré).
                val prev = prevByContract[entity.contractAddress.lowercase()]
                val usd = (price?.usd ?: 0.0).let { if (it > 0.0) it else (prev?.priceUsd ?: 0.0) }
                val eur = (price?.eur ?: 0.0).let { if (it > 0.0) it else (prev?.priceEur ?: 0.0) }
                val xof = (price?.xof ?: 0.0).let {
                    when {
                        it > 0.0 -> it
                        eur > 0.0 -> eur * 655.957
                        else -> prev?.priceXof ?: 0.0
                    }
                }
                val change = price?.change24h ?: (prev?.changePercent24h ?: 0.0)

                if (bal == null) {
                    // Lecture du solde échouée → on garde le dernier solde connu
                    // mais on rafraîchit le prix de marché.
                    prev?.let {
                        return@async it.copy(
                            priceUsd = usd, priceEur = eur, priceXof = xof,
                            changePercent24h = change
                        )
                    }
                }
                val amount = bal ?: 0.0
                TokenBalance(
                    symbol = entity.symbol,
                    name = entity.name.ifBlank { entity.symbol },
                    amountFormatted = "%.4f %s".format(amount, entity.symbol),
                    valueXof = amount * xof,
                    changePercent24h = change,
                    colorHex = "#3B82F6",
                    blockchain = chain,
                    valueUsd = amount * usd,
                    valueEur = amount * eur,
                    priceUsd = usd, priceEur = eur, priceXof = xof,
                    amountRaw = amount,
                    isCustom = true,
                    contractAddress = entity.contractAddress,
                    decimals = entity.decimals
                )
            }
        }.map { it.await() }
    }

    /** Une seule sonde par session : ~9 eth_call, pas à chaque refresh. */
    /*
    ═══════════════════════════════════════════════════════════════════════
    MÉMOIRE DU DERNIER BALAYAGE — adresse + horodatage, PAS un simple oui/non
    ═══════════════════════════════════════════════════════════════════════

    Ce champ était un booléen `registryScanned`, mis à true au premier
    balayage et JAMAIS remis à zéro. Conséquence, constatée en usage réel :

      · l'utilisateur ouvre le wallet B, aucun token reçu — balayage effectué,
        rien trouvé, drapeau posé ;
      · il reçoit ensuite des SHIB sur ce wallet ;
      · tous les rafraîchissements suivants sautent la détection.

    Les jetons étaient bien sur la chaîne, mais l'application ne regardait
    plus. Le drapeau bloquait aussi le balayage après un CHANGEMENT de wallet :
    posé pour le wallet A, il empêchait toute détection sur le wallet B.

    On mémorise donc l'adresse balayée et l'instant du balayage. Un nouveau
    balayage a lieu si l'adresse change, ou après [DELAI_RESCAN].
    ═══════════════════════════════════════════════════════════════════════
     */
    @Volatile private var dernierBalayage: Pair<String, Long>? = null

    /**
     * Sonde les contrats CONNUS du registre d'échange (SHIB, USDC, DAI, CAKE…)
     * absents de la liste du wallet, et active automatiquement ceux dont le
     * solde on-chain est > 0. Répond au cas réel : « mes SHIB sont partis du
     * wallet A mais ne sont jamais arrivés sur B » — ils étaient bien sur la
     * chaîne, mais B n'affichait pas le token. @return true si ajout(s).
     */
    private suspend fun discoverRegistryTokens(
        ethAddress: String,
        bnbAddress: String,
        customs: List<com.vaultex.data.local.entity.TokenEntity>
    ): Boolean {
        val maintenant = System.currentTimeMillis()
        val (adrPrec, tPrec) = dernierBalayage ?: ("" to 0L)
        val memeAdresse = adrPrec == ethAddress
        if (memeAdresse && maintenant - tPrec < DELAI_RESCAN) return false
        dernierBalayage = ethAddress to maintenant
        val known = customs.map { it.contractAddress.lowercase() }.toSet()
        var added = false
        com.vaultex.ui.viewmodel.SwapViewModel.SWAP_ASSETS
            .mapNotNull { com.vaultex.ui.viewmodel.SwapViewModel.tokenEntityFor(it) }
            .filter { it.contractAddress.lowercase() !in known }
            .forEach { entity ->
                val isBnb = entity.blockchain == "BNB"
                val bal = fetchErc20Balance(
                    if (isBnb) bnbRpc else ethRpc,
                    entity.contractAddress,
                    if (isBnb) bnbAddress else ethAddress,
                    entity.decimals
                )
                if ((bal ?: 0.0) > 0.0) {
                    try { tokenRepository.addToken(entity); added = true } catch (_: Exception) { }
                }
            }
        return added
    }

    private suspend fun fetchErc20Balance(rpc: EvmRpcApi, contract: String, address: String, decimals: Int): Double? = try {
        val paddedAddr = address.removePrefix("0x").padStart(64, '0')
        val data = "0x70a08231$paddedAddr"
        val res = rpc.rpcCall(JsonRpcRequest("eth_call",
            mutableListOf(mapOf("to" to contract, "data" to data) as Any, "latest" as Any)))
        val hex = res.result as? String
        if (res.error != null || hex == null) null
        else BigInteger(hex.removePrefix("0x").ifEmpty { "0" }, 16)
            .toBigDecimal().divide(java.math.BigDecimal.TEN.pow(decimals)).toDouble()
    } catch (_: Exception) { null }

    private suspend fun fetchEvmBalance(rpc: EvmRpcApi, address: String): Double? = try {
        val res = rpc.rpcCall(JsonRpcRequest("eth_getBalance", mutableListOf(address as Any, "latest" as Any)))
        val hex = res.result as? String
        if (res.error != null || hex == null) null
        else BigInteger(hex.removePrefix("0x").ifEmpty { "0" }, 16)
            .toBigDecimal().divide(java.math.BigDecimal("1000000000000000000")).toDouble()
    } catch (_: Exception) { null }

    private suspend fun fetchBtcBalance(address: String): Double? = try {
        val info = bitcoinApi.getAddressInfo(address)
        // Fonds ENTRANTS encore en mempool (0 confirmation) : le solde confirmé
        // ne bouge pas avant le premier bloc (10-30 min) — sans signal, c'est la
        // panique « il a envoyé mais je n'ai rien reçu ». On allume le badge
        // « En attente » sur la ligne BTC dès que des fonds arrivent.
        val incomingSat = info.mempoolStats.fundedSum - info.mempoolStats.spentSum
        _incomingPending.value =
            if (incomingSat > 0) _incomingPending.value + "BTC"
            else _incomingPending.value - "BTC"
        (info.chainStats.fundedSum - info.chainStats.spentSum) / 1e8
    } catch (_: Exception) { null }

    private suspend fun fetchSolBalance(address: String): Double? = try {
        val res = solanaRpc.rpcCall(JsonRpcRequest("getBalance", mutableListOf(address as Any)))
        if (res.error != null) null
        else {
            @Suppress("UNCHECKED_CAST")
            val lamports = (res.result as? Map<String, Any>)?.get("value") as? Double ?: 0.0
            lamports / 1e9
        }
    } catch (_: Exception) { null }

    private suspend fun fetchTrxBalance(address: String): Double? = try {
        val account = tronApi.getAccount(address)
        (account.data.firstOrNull()?.balance ?: 0L) / 1_000_000.0
    } catch (_: Exception) { null }

    private suspend fun fetchUsdtTrc20Balance(address: String): Double? {
        return try {
            val account = tronApi.getAccount(address)
            val trc20List = account.data.firstOrNull()?.trc20 ?: return 0.0
            val rawBalance = trc20List
                .firstOrNull { it.containsKey(USDT_TRC20_CONTRACT) }
                ?.get(USDT_TRC20_CONTRACT) ?: return 0.0
            rawBalance.toLongOrNull()?.let { it / 1_000_000.0 } ?: 0.0
        } catch (_: Exception) { null }
    }
}
