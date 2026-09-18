package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.core.crypto.WalletManager
import com.vaultex.core.security.SecureStorage
import com.vaultex.domain.usecase.SendCryptoUseCase
import com.vaultex.domain.usecase.SwapUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SwapState(
    val fromToken: String = "USDT",
    val toToken: String = "TRX",
    val fromAmount: String = "",
    val toAmount: String = "",
    val fromBalance: Double = 0.0,      // solde de la monnaie source (pour MAX + affichage)
    val fromPriceUsd: Double = 0.0,     // prix USD de la monnaie source (pour « ≈ $ »)
    val toPriceUsd: Double = 0.0,       // prix USD de la monnaie cible
    val estimatedFee: String = "",
    val minAmount: Double? = null,
    val isCrossChain: Boolean = true,
    val isLoading: Boolean = false,
    val error: String? = null,
    val swapId: String? = null,         // ChangeNOW transaction ID
    val payinAddress: String? = null,   // adresse où envoyer pour déclencher le swap
    val depositAmount: String? = null,  // montant EXACT à déposer (= attendu par ChangeNOW)
    val depositTxHash: String? = null,  // hash du dépôt envoyé automatiquement
    val swapStatus: String? = null,     // creating/depositing/waiting/confirming/exchanging/sending/finished/failed
    val swapInProgress: Boolean = false // un swap est en cours (overlay de suivi)
)

@HiltViewModel
class SwapViewModel @Inject constructor(
    /*
    L'ÉCHANGEUR EST DÉSORMAIS DERRIÈRE UNE INTERFACE.

    ChangeNOW était appelé ici en direct. Changer de fournisseur revenait donc
    à retoucher le ViewModel — alors que le choix de l'échangeur ne regarde ni
    l'état de l'écran ni la façon dont il s'affiche.

    Le module Hilt décide lequel des deux répond, selon `swap.provider`.
    */
    private val fournisseur: com.vaultex.domain.swap.FournisseurSwap,
    private val secureStorage: SecureStorage,
    private val swapUseCase: SwapUseCase,
    private val sendCryptoUseCase: com.vaultex.domain.usecase.SendCryptoUseCase,
    private val tokenRepository: com.vaultex.data.repository.TokenRepository,
    private val hub: com.vaultex.core.session.NotificationHub,
    private val notifPrefs: com.vaultex.core.session.NotifPrefs,
    private val pendingTxManager: com.vaultex.core.tx.PendingTxManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
) : ViewModel() {

    /** Raccourci ressources — via LocaleManager pour respecter la langue CHOISIE
     *  dans l'app (le contexte application, lui, suit la langue du système). */
    private fun str(id: Int, vararg args: Any): String =
        com.vaultex.core.session.LocaleManager.wrap(appContext).getString(id, *args)

    private var statusJob: kotlinx.coroutines.Job? = null

    /** Un actif échangeable : identité app + ticker ChangeNOW + chaînes dépôt/réception. */
    data class SwapAsset(
        val key: String,        // identifiant interne = symbole dans le portefeuille
        val base: String,       // symbole affiché (et logo)
        val badge: String,      // réseau court (TRC20, ERC20…)
        val network: String,    // réseau long (écran de confirmation)
        val cn: String,         // ticker ChangeNOW
        val chain: String,      // chaîne de l'adresse de RÉCEPTION (BTC/ETH/BNB/SOL/TRX)
        val sendChain: String,  // chaîne du moteur d'envoi pour le DÉPÔT
        val contract: String? = null, // contrat (auto-ajout au portefeuille à la réception)
        val decimals: Int = 18
    )

    companion object {

        /**
         * Registre des actifs échangeables. Contrainte non-custodial : uniquement
         * des monnaies déposables ET recevables sur nos 5 chaînes. Les tokens
         * ERC-20 utilisent le moteur d'envoi générique ("ERC20:ETH:contrat:déc").
         */
        val SWAP_ASSETS: List<SwapAsset> = listOf(
            SwapAsset("BTC", "BTC", "Bitcoin", "Bitcoin", "btc", "BTC", "BTC"),
            SwapAsset("ETH", "ETH", "Ethereum", "Ethereum", "eth", "ETH", "ETH"),
            SwapAsset("BNB", "BNB", "BNB Chain", "BNB Chain (BEP20)", "bnbbsc", "BNB", "BNB"),
            SwapAsset("SOL", "SOL", "Solana", "Solana", "sol", "SOL", "SOL"),
            SwapAsset("TRX", "TRX", "Tron", "Tron", "trx", "TRX", "TRX"),
            SwapAsset("USDT", "USDT", "TRC20", "Tron (TRC20)", "usdttrc20", "TRX", "USDT"),
            SwapAsset("USDT-ETH", "USDT", "ERC20", "Ethereum (ERC20)", "usdterc20", "ETH", "USDT-ETH",
                contract = "0xdAC17F958D2ee523a2206206994597C13D831ec7", decimals = 6),
            SwapAsset("USDT-BNB", "USDT", "BEP20", "BNB Chain (BEP20)", "usdtbsc", "BNB", "USDT-BNB",
                contract = "0x55d398326f99059fF775485246999027B3197955", decimals = 18),
            SwapAsset("USDC", "USDC", "ERC20", "Ethereum (ERC20)", "usdc", "ETH",
                "ERC20:ETH:0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48:6",
                contract = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48", decimals = 6),
            SwapAsset("DAI", "DAI", "ERC20", "Ethereum (ERC20)", "dai", "ETH",
                "ERC20:ETH:0x6B175474E89094C44Da98b954EedeAC495271d0F:18",
                contract = "0x6B175474E89094C44Da98b954EedeAC495271d0F", decimals = 18),
            SwapAsset("LINK", "LINK", "ERC20", "Ethereum (ERC20)", "link", "ETH",
                "ERC20:ETH:0x514910771AF9Ca656af840dff83E8264EcF986CA:18",
                contract = "0x514910771AF9Ca656af840dff83E8264EcF986CA", decimals = 18),
            SwapAsset("SHIB", "SHIB", "ERC20", "Ethereum (ERC20)", "shib", "ETH",
                "ERC20:ETH:0x95aD61b0a150d79219dCF64E1E6Cc01f0B64C4cE:18",
                contract = "0x95aD61b0a150d79219dCF64E1E6Cc01f0B64C4cE", decimals = 18),
            SwapAsset("PEPE", "PEPE", "ERC20", "Ethereum (ERC20)", "pepe", "ETH",
                "ERC20:ETH:0x6982508145454Ce325dDbE47a25d4ec3d2311933:18",
                contract = "0x6982508145454Ce325dDbE47a25d4ec3d2311933", decimals = 18),
            SwapAsset("UNI", "UNI", "ERC20", "Ethereum (ERC20)", "uni", "ETH",
                "ERC20:ETH:0x1f9840a85d5aF5bf1D1762F925BDADdC4201F984:18",
                contract = "0x1f9840a85d5aF5bf1D1762F925BDADdC4201F984", decimals = 18),
            SwapAsset("AAVE", "AAVE", "ERC20", "Ethereum (ERC20)", "aave", "ETH",
                "ERC20:ETH:0x7Fc66500c84A76Ad7e9c93437bFc5Ac33E2DDaE9:18",
                contract = "0x7Fc66500c84A76Ad7e9c93437bFc5Ac33E2DDaE9", decimals = 18),
            SwapAsset("WBTC", "WBTC", "ERC20", "Ethereum (ERC20)", "wbtc", "ETH",
                "ERC20:ETH:0x2260FAC5E5542a773Aa44fBCfeDf7C193bc2C599:8",
                contract = "0x2260FAC5E5542a773Aa44fBCfeDf7C193bc2C599", decimals = 8),
            SwapAsset("CAKE", "CAKE", "BEP20", "BNB Chain (BEP20)", "cake", "BNB",
                "ERC20:BNB:0x0E09FaBB73Bd3Ade0a17ECC321fD13a19e81cE82:18",
                contract = "0x0E09FaBB73Bd3Ade0a17ECC321fD13a19e81cE82", decimals = 18)
        )

        fun assetOf(key: String): SwapAsset =
            SWAP_ASSETS.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: SWAP_ASSETS[0]

        /** Actif correspondant à un SYMBOLE d'affichage (Marché) — USDT → variante TRC20. */
        fun assetForSymbol(symbol: String): SwapAsset? =
            SWAP_ASSETS.firstOrNull { it.base.equals(symbol, ignoreCase = true) }

        /** Symboles échangeables (badge « Échangeable » du Marché). */
        val SWAPPABLE_SYMBOLS: Set<String> = SWAP_ASSETS.map { it.base.uppercase() }.toSet()

        /**
         * TokenEntity à enregistrer dans le portefeuille pour un actif ERC-20 /
         * BEP-20 du registre (contrat CONNU). Renvoie null pour les natifs et
         * les variantes USDT, déjà gérés nativement par Envoyer / Recevoir.
         *
         * Grâce à ce contrat connu, l'app AUTO-ajoute la monnaie quand on tape
         * « Envoyer » / « Recevoir » depuis sa page Marché : plus besoin d'aller
         * chercher l'adresse du contrat à la main.
         */
        fun tokenEntityFor(asset: SwapAsset): com.vaultex.data.local.entity.TokenEntity? {
            val contract = asset.contract ?: return null
            if (asset.key == "USDT-ETH" || asset.key == "USDT-BNB") return null
            return com.vaultex.data.local.entity.TokenEntity(
                contractAddress = contract,
                blockchain = if (asset.chain == "BNB") "BNB" else "ETH",
                symbol = asset.base,
                name = asset.base,
                decimals = asset.decimals,
                iconUrl = com.vaultex.ui.components.CryptoIcon.url(asset.base),
                isCustom = true
            )
        }
    }

    private val _state = MutableStateFlow(SwapState())
    val state: StateFlow<SwapState> = _state.asStateFlow()

    private val gson = com.google.gson.Gson()
    private data class SnapLite(val tokens: List<TokLite>?)
    private data class TokLite(val symbol: String = "", val amountRaw: Double = 0.0, val priceUsd: Double = 0.0, val priceXof: Double = 0.0)

    /*
    ═══════════════════════════════════════════════════════════════════════
    S'OUVRIR SUR LA VARIANTE QU'ON DÉTIENT, PAS SUR CELLE QUI EST VIDE
    ═══════════════════════════════════════════════════════════════════════

    L'écran s'ouvrait toujours sur USDT (TRC20), premier de la liste. Pour
    quelqu'un dont les USDT sont sur BNB Chain, il s'ouvrait donc sur un
    solde à zéro, affichait « Solde : 0 USDT » et bloquait le bouton — sans
    que rien n'indique que les fonds étaient une ligne plus bas dans le
    sélecteur.

    On ouvre désormais sur la variante qui porte le solde. Ce n'est pas une
    substitution : au moment de l'ouverture, aucun choix n'a encore été fait,
    et le réseau reste écrit en toutes lettres sur le sélecteur (« BEP20 »,
    « TRC20 ») comme sur l'écran de confirmation.

    CE N'EST QUE LE RÉGLAGE D'OUVERTURE. Dès que l'utilisateur choisit une
    ligne lui-même, setFromToken l'applique telle quelle : on n'a rien à
    redire à quelqu'un qui sélectionne sciemment une variante vide — il peut
    vouloir y recevoir des fonds. Corriger ce choix-là serait le genre de
    substitution silencieuse qu'un écran qui déplace de l'argent ne doit
    jamais se permettre.
    */
    private fun varianteDetenue(cle: String): String {
        if (balanceOf(cle) > 0.0) return cle
        val base = assetOf(cle).base
        val riche = SWAP_ASSETS
            .filter { it.base.equals(base, ignoreCase = true) && !it.key.equals(cle, ignoreCase = true) }
            .map { it.key to balanceOf(it.key) }
            .filter { it.second > 0.0 }
            .maxByOrNull { it.second }
            ?.first
            ?: return cle
        // Une variante ne doit jamais devenir la monnaie d'arrivée : on
        // n'échange pas une monnaie contre elle-même.
        return if (riche.equals(_state.value.toToken, ignoreCase = true)) cle else riche
    }

    init {
        val depart = varianteDetenue(_state.value.fromToken)
        _state.update { it.copy(
            fromToken = depart,
            fromBalance = balanceOf(depart),
            fromPriceUsd = priceUsdOf(depart),
            toPriceUsd = priceUsdOf(it.toToken)
        ) }
        chargerMinimum()
    }

    /**
     * Pré-sélection venue de la fiche d'une crypto (Marché → « Échanger »).
     *
     * La fiche parle de Tether en général ; elle ne sait pas sur quelle
     * chaîne se trouvent les fonds. On applique donc la même règle qu'à
     * l'ouverture : arriver sur la variante détenue plutôt que sur la
     * première de la liste.
     */
    fun preselectFromToken(symbole: String) = setFromToken(varianteDetenue(symbole))

    /*
    ═══════════════════════════════════════════════════════════════════════
    LE MINIMUM SE LIT AVANT DE TAPER, PAS APRÈS S'ÊTRE FAIT REFUSER
    ═══════════════════════════════════════════════════════════════════════

    Le minimum n'était demandé qu'à deux moments : au clic sur MAX, et à la
    création de l'échange. Autrement dit, on le découvrait en se faisant
    refuser — après avoir choisi les monnaies, saisi un montant et touché
    « Continuer ».

    Or il ne s'agit pas d'un détail : mesuré sur le vrai service,
    USDT-TRC20 → BTC exige 17,30 USDT, soit environ 10 400 FCFA. Un
    utilisateur qui détient 2 000 FCFA n'a aucune chance, et rien ne le lui
    disait avant l'échec.

    CE MINIMUM N'EST PAS ARBITRAIRE, ET ON NE LE DEVINE PAS. Il dépend du
    coût de retrait de la monnaie d'ARRIVÉE : sortir du BTC coûte cher,
    sortir du TRX coûte des centimes. Il change donc avec les frais de la
    chaîne, et l'écrire en dur serait faux dès le lendemain.

    On le demande au fournisseur à chaque changement de paire. Un échec
    laisse simplement la ligne vide — jamais un chiffre inventé.

    ET ON NE BLOQUE RIEN. Le fournisseur refusera ce qu'il refuse, avec son
    propre message ; l'avertissement « Valeur perdue » dit déjà ce que
    l'opération coûte en proportion. À l'utilisateur de décider — c'est son
    argent, et un portefeuille non-dépositaire n'a pas à choisir pour lui.
    */
    private var jobMinimum: kotlinx.coroutines.Job? = null

    private fun chargerMinimum() {
        // Une seule requête à la fois : changer de paire deux fois
        // rapidement ne doit pas laisser la réponse la plus lente écraser
        // l'écran — le même piège que les frais de l'écran d'envoi.
        jobMinimum?.cancel()
        val de = _state.value.fromToken
        val vers = _state.value.toToken
        _state.update { it.copy(minAmount = null) }
        jobMinimum = viewModelScope.launch {
            val min = withContext(Dispatchers.IO) { swapUseCase.getMinAmount(de, vers) }
            // La paire a changé pendant l'appel : ce minimum ne la concerne
            // plus.
            if (_state.value.fromToken != de || _state.value.toToken != vers) return@launch
            _state.update { it.copy(minAmount = min) }
        }
    }

    /** Minimum de la paire, prêt à afficher — ou null s'il est inconnu. */
    fun minimumLisible(): String? =
        _state.value.minAmount?.let {
            java.math.BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() +
                " " + assetOf(_state.value.fromToken).base
        }

    /** Commission VaultEx réellement appliquée par le fournisseur en service. */
    val commissionPourcent: Double get() = swapUseCase.commissionPourcent

    /** Nom de l'échangeur en service — ChangeNOW ou SimpleSwap. */
    val nomFournisseur: String get() = swapUseCase.nomFournisseur

    private fun snapTok(token: String): TokLite? {
        val json = secureStorage.getPortfolioSnapshot() ?: return null
        return try {
            gson.fromJson(json, SnapLite::class.java)?.tokens?.firstOrNull { it.symbol == token }
        } catch (_: Exception) {
            // Lecture d'un cache LOCAL : un echec ici n'est pas une panne de
            // service, inutile de le remonter — l'appelant retombe sur le
            // solde reseau.
            null
        }
    }

    /** Solde de [token] lu dans l'instantané portefeuille (aucun appel réseau). */
    private fun balanceOf(token: String): Double = snapTok(token)?.amountRaw ?: 0.0

    /** Prix USD de [token] (instantané portefeuille). */
    private fun priceUsdOf(token: String): Double = snapTok(token)?.priceUsd ?: 0.0

    /** (solde, valeur en XOF) d'un actif — lignes du sélecteur de crypto. */
    fun balanceInfo(key: String): Pair<Double, Double> {
        val t = snapTok(key) ?: return 0.0 to 0.0
        return t.amountRaw to t.amountRaw * t.priceXof
    }

    /**
     * Bouton MAX : remplit avec le solde, en RÉSERVANT de quoi payer le gas du
     * DÉPÔT pour une monnaie native (sinon « MAX » prend tout le BNB/ETH et il
     * ne reste rien pour les frais → l'envoi du dépôt échoue). Pour un token
     * (USDT), le gas est payé en natif séparément → pas de réserve.
     */
    fun onMaxClicked() = onFractionClicked(1.0)

    /**
     * Remplit avec une PART du solde : 0,25, 0,50 ou 1,0 pour le maximum.
     *
     * Une part inférieure à 1 n'a pas besoin de réserve de gas : ce qui reste
     * sur le compte suffit largement à payer les frais. La réserve — et le
     * contrôle du minimum de la paire qui l'accompagne — ne concerne donc que
     * le cas où l'on vide effectivement le solde.
     */
    fun onFractionClicked(fraction: Double) {
        if (fraction < 1.0) {
            val bal = _state.value.fromBalance
            if (bal <= 0.0) {
                _state.update { it.copy(error = messageSoldeVide(_state.value.fromToken)) }
                return
            }
            setFromAmount(
                java.math.BigDecimal.valueOf(bal * fraction)
                    .setScale(8, java.math.RoundingMode.DOWN)
                    .stripTrailingZeros()
                    .toPlainString()
            )
            return
        }
        val tok = _state.value.fromToken
        val bal = _state.value.fromBalance
        if (bal <= 0.0) {
            _state.update { it.copy(error = messageSoldeVide(tok)) }
            return
        }
        val reserve = when (tok.uppercase()) {
            "BTC" -> 0.00002
            "ETH" -> 0.0003
            "BNB" -> 0.00005
            // SOL : frais FIXE (5000 lamports). Réserve EXACTE : le compte doit
            // se vider à 0 pile — un résidu < 0.00089 SOL (rent-exempt) ferait
            // rejeter le dépôt du swap par le réseau.
            "SOL" -> 0.000005
            "TRX" -> 1.1   // bande passante brûlée + activation éventuelle du destinataire
            else  -> 0.0   // USDT & tokens : gas en natif séparé
        }
        val spendable = bal - reserve
        if (spendable <= 0.0) {
            // Solde présent mais trop faible pour couvrir le gas du dépôt → message
            // chiffré et clair (solde réel + réserve nécessaire).
            _state.update { it.copy(error = str(com.vaultex.R.string.swap_msg_low_gas, tok, trimNum(bal), trimNum(reserve))) }
            return
        }
        val txt = java.math.BigDecimal.valueOf(spendable)
            .setScale(8, java.math.RoundingMode.DOWN).stripTrailingZeros().toPlainString()
        // On affiche d'abord le montant MAX (et on lance le devis).
        setFromAmount(txt)
        // Puis on vérifie EN MÊME TEMPS si ce MAX atteint le minimum requis pour
        // la paire : s'il y a des fonds mais qu'ils sont insuffisants pour swap,
        // on garde le montant affiché ET on prévient l'utilisateur.
        val fromTok = _state.value.fromToken
        val toTok = _state.value.toToken
        viewModelScope.launch {
            val min = swapUseCase.getMinAmount(fromTok, toTok) ?: return@launch
            _state.update { st ->
                if (st.fromAmount == txt && spendable < min) {
                    val minTxt = java.math.BigDecimal.valueOf(min).stripTrailingZeros().toPlainString()
                    st.copy(minAmount = min, error = str(com.vaultex.R.string.swap_msg_max_below_min, txt, fromTok, minTxt))
                } else st.copy(minAmount = min)
            }
        }
    }

    fun setFromToken(token: String) {
        _state.update { it.copy(fromToken = token, fromBalance = balanceOf(token), fromPriceUsd = priceUsdOf(token)) }
        chargerMinimum()
        val amt = _state.value.fromAmount
        if (amt.isNotEmpty()) estimateOutput(amt)
    }

    fun setToToken(token: String) {
        _state.update { it.copy(toToken = token, toPriceUsd = priceUsdOf(token)) }
        chargerMinimum()
        val amt = _state.value.fromAmount
        if (amt.isNotEmpty()) estimateOutput(amt)
    }

    fun setFromAmount(amount: String) {
        // Clavier français : la virgule décimale devient un point (sinon
        // toDoubleOrNull échoue et le devis ne part jamais).
        val normalized = amount.replace(',', '.')
        _state.update { it.copy(fromAmount = normalized, error = null) }
        if (normalized.isNotEmpty()) estimateOutput(normalized)
    }

    fun swapTokens() {
        _state.update {
            it.copy(
                fromToken = it.toToken, toToken = it.fromToken,
                fromAmount = it.toAmount, toAmount = it.fromAmount,
                fromBalance = balanceOf(it.toToken),
                fromPriceUsd = priceUsdOf(it.toToken), toPriceUsd = priceUsdOf(it.fromToken)
            )
        }
        // Inverser la paire, c'est en changer : le minimum de BTC→USDT n'a
        // rien à voir avec celui d'USDT→BTC, puisque la monnaie qui sort
        // n'est plus la même.
        chargerMinimum()
    }

    private fun estimateOutput(amount: String) {
        viewModelScope.launch {
            val input = amount.toDoubleOrNull() ?: return@launch
            if (input <= 0.0) {
                // « 0 » ou saisie incomplète : on vide le résultat sans appeler l'API.
                _state.update { it.copy(toAmount = "") }
                return@launch
            }
            // On échange le montant COMPLET. La commission VaultEx vient de
            // ChangeNOW (programme partenaire), pas en rognant le montant.
            try {
                val de = _state.value.fromToken
                val vers = _state.value.toToken
                // Réseau lent : on retente UNE fois automatiquement sur timeout /
                // coupure avant d'afficher une erreur à l'utilisateur.
                val est = withContext(Dispatchers.IO) {
                    try {
                        fournisseur.devis(de, vers, input)
                    } catch (e: Exception) {
                        if (e is java.net.SocketTimeoutException || e is java.net.UnknownHostException) {
                            kotlinx.coroutines.delay(1200)
                            fournisseur.devis(de, vers, input)
                        } else throw e
                    }
                }
                // Ignorer les devis obsolètes (l'utilisateur a déjà changé le montant).
                if (_state.value.fromAmount != amount) return@launch
                _state.update { it.copy(toAmount = est.montantEstime, error = null) }
            } catch (e: Exception) {
                if (_state.value.fromAmount != amount) return@launch
                // Pas de devis : on n'affiche PAS un faux montant, on vide ET on
                // montre la vraie raison (ex. montant sous le minimum de la paire).
                _state.update { it.copy(toAmount = "", error = str(com.vaultex.R.string.swap_msg_quote_failed, changeNowError(e))) }
            }
        }
    }

    fun executeSwap() {
        val s = _state.value
        if (s.isLoading) return
        val inputAmount = s.fromAmount.toDoubleOrNull() ?: run {
            _state.update { it.copy(error = str(com.vaultex.R.string.swap_msg_enter_amount)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                when (val validation = swapUseCase.validate(s.fromToken, s.toToken, inputAmount)) {
                    is SwapUseCase.ValidationResult.Invalid -> {
                        val msg = when (validation.reason) {
                            SwapUseCase.ValidationResult.Reason.INVALID_AMOUNT ->
                                str(com.vaultex.R.string.swap_msg_enter_amount)
                            SwapUseCase.ValidationResult.Reason.SAME_TOKEN ->
                                str(com.vaultex.R.string.swap_msg_same_coin)
                            SwapUseCase.ValidationResult.Reason.BELOW_MINIMUM -> {
                                val min = swapUseCase.getMinAmount(s.fromToken, s.toToken)
                                if (min != null) str(com.vaultex.R.string.swap_msg_below_min, trimNum(min), s.fromToken)
                                else str(com.vaultex.R.string.swap_msg_below_min_generic, s.fromToken, s.toToken)
                            }
                        }
                        _state.update { it.copy(isLoading = false, error = msg) }
                        return@launch
                    }
                    SwapUseCase.ValidationResult.Valid -> Unit
                }
                // Montant COMPLET déposé/échangé (commission via ChangeNOW).
                val net = inputAmount

                // Dériver l'adresse de réception : la chaîne vient du registre.
                val mnemonic = secureStorage.getMnemonic()
                if (mnemonic == null) {
                    _state.update { it.copy(isLoading = false, error = str(com.vaultex.R.string.swap_msg_wallet)) }
                    return@launch
                }
                val addresses = withContext(Dispatchers.IO) { WalletManager.deriveAddresses(mnemonic, secureStorage.getPassphrase()) }
                fun addrFor(chain: String) = when (chain) {
                    "ETH" -> addresses.eth
                    "BNB" -> addresses.bnb
                    "BTC" -> addresses.btc
                    "SOL" -> addresses.sol
                    "TRX" -> addresses.trx
                    else  -> addresses.eth
                }
                val toAddress = addrFor(assetOf(s.toToken).chain)
                // Adresse de REMBOURSEMENT = notre adresse sur la chaîne source.
                // Sans elle, un swap échoué/expiré côté ChangeNOW laisse les
                // fonds chez eux (récupération uniquement via leur support).
                val refundAddress = addrFor(assetOf(s.fromToken).chain)

                /*
                Le minimum vient du fournisseur, et peut être ABSENT.

                Auparavant l'appel n'était pas protégé : un échec réseau ou une
                paire inconnue faisait échouer toute la création. Un minimum
                qu'on ne connaît pas ne doit pas empêcher d'essayer — c'est le
                fournisseur qui tranchera, et son refus portera un message
                précis.
                */
                val minimum = withContext(Dispatchers.IO) { fournisseur.minimum(s.fromToken, s.toToken) }
                if (minimum != null && net < minimum) {
                    _state.update { it.copy(isLoading = false, error = str(com.vaultex.R.string.swap_msg_below_min, trimNum(minimum), s.fromToken)) }
                    return@launch
                }

                // Créer l'échange chez le fournisseur en service.
                //
                // creerEchange ÉCHOUE plutôt que de rendre une adresse de dépôt
                // vide : c'est vers elle que partiront les fonds, et une réponse
                // incomplète ne doit jamais devenir un virement dans le vide.
                val txRes = withContext(Dispatchers.IO) {
                    fournisseur.creerEchange(
                        de = s.fromToken,
                        vers = s.toToken,
                        montant = net,
                        adresseReception = toAddress,
                        adresseRemboursement = refundAddress
                    )
                }
                swapUseCase.recordSwap(
                    swapId = txRes.id,
                    fromToken = s.fromToken,
                    toToken = s.toToken,
                    amount = apiAmount(net),
                    payinAddress = txRes.adresseDepot,
                    payoutAddress = toAddress
                )
                // Token ERC-20/BEP-20 reçu : on l'ajoute au portefeuille pour que
                // le solde reçu soit VISIBLE (sinon le user croit ne rien recevoir).
                tokenEntityFor(assetOf(s.toToken))?.let { entity ->
                    try { tokenRepository.addToken(entity) } catch (_: Exception) { /* déjà présent */ }
                }
                // Cloche : notifie DÈS LA CRÉATION, au même instant que l'entrée
                // « Récent » (swapUseCase.recordSwap ci-dessus) — sinon la cloche
                // ne sonnait qu'à la fin (minutes plus tard) alors que « Récent »
                // affichait déjà le swap : désalignement signalé en test réel.
                if (notifPrefs.txAlerts.value) {
                    hub.post(
                        key = "swap:started:${txRes.id}",
                        title = str(com.vaultex.R.string.notif_swap_started_title),
                        body = str(com.vaultex.R.string.notif_swap_started_body, s.fromAmount, assetOf(s.fromToken).base, assetOf(s.toToken).base),
                        symbol = assetOf(s.fromToken).base
                    )
                }
                _state.update {
                    it.copy(
                        swapId = txRes.id,
                        payinAddress = txRes.adresseDepot,
                        depositAmount = apiAmount(net),
                        swapInProgress = true,
                        swapStatus = "depositing"
                    )
                }
                // Événement admin (Telegram) : swap lancé — 🚨 si ≥ 20 $.
                com.vaultex.core.monitoring.AdminBot.swapCreated(
                    apiAmount(net), assetOf(s.fromToken).base, assetOf(s.toToken).base,
                    net * s.fromPriceUsd
                )

                // DÉPÔT AUTOMATIQUE (comme Trust Wallet) : on envoie nous-mêmes les
                // fonds vers l'adresse payin via le moteur d'envoi déjà testé.
                val depChain = swapSendChainOf(s.fromToken)
                val dep = withContext(Dispatchers.IO) {
                    sendCryptoUseCase.sendByChain(depChain, txRes.adresseDepot, apiAmount(net))
                }
                when (dep) {
                    is SendCryptoUseCase.Result.Success -> {
                        _state.update { it.copy(isLoading = false, depositTxHash = dep.txHash, swapStatus = "waiting") }
                        com.vaultex.core.session.BalanceRefreshSignal.signalTxSent()
                        // Le dépôt alimente le badge « En attente » du dashboard : si le
                        // user quitte l'écran de suivi, la monnaie source reste marquée
                        // comme transaction en cours jusqu'à confirmation on-chain.
                        pendingTxManager.track(assetOf(s.fromToken).base, assetOf(s.fromToken).chain, dep.txHash)
                        trackSwapStatus(txRes.id)
                    }
                    is SendCryptoUseCase.Result.Error -> {
                        _state.update { it.copy(isLoading = false, swapInProgress = false, swapStatus = null,
                            error = str(com.vaultex.R.string.swap_msg_deposit_failed, dep.message)) }
                        com.vaultex.core.monitoring.AdminBot.swapFailed(
                            assetOf(s.fromToken).base, assetOf(s.toToken).base,
                            "dépôt refusé : ${dep.message}")
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, swapInProgress = false, error = changeNowError(e)) }
                // Panne cote ChangeNOW : sans remontee, un swap impossible pour
                // TOUS les utilisateurs (cle revoquee, paire desactivee, service
                // en panne) resterait invisible jusqu'a ce que quelqu'un se
                // plaigne. Message technique, non traduit : c'est un diagnostic.
                com.vaultex.core.monitoring.reportUnlessCancelled("ChangeNOW", e)
            }
        }
    }

    /** Chaîne d'envoi pour déposer la monnaie source (registre des actifs). */
    private fun swapSendChainOf(fromToken: String): String = assetOf(fromToken).sendChain

    /**
     * Format de montant pour les APIs : point décimal garanti (toPlainString),
     * 8 décimales max, arrondi VERS LE BAS. L'ancien format 6 décimales
     * transformait 0.00000001 en « 0.000000 » et pouvait ARRONDIR AU-DESSUS du
     * solde (dépôt refusé) pour les montants à 8 décimales (BTC, MAX…).
     */
    private fun apiAmount(v: Double): String =
        java.math.BigDecimal.valueOf(v).setScale(8, java.math.RoundingMode.DOWN)
            .stripTrailingZeros().toPlainString()

    /** Nombre lisible (jusqu'à 8 décimales, sans zéros inutiles) pour les messages. */
    private fun trimNum(v: Double): String =
        java.math.BigDecimal.valueOf(v).setScale(8, java.math.RoundingMode.DOWN).stripTrailingZeros().toPlainString()

    /*
    ═══════════════════════════════════════════════════════════════════════
    « SOLDE VIDE » QUAND LE SOLDE EXISTE, SUR UNE AUTRE CHAÎNE
    ═══════════════════════════════════════════════════════════════════════

    Constaté sur appareil : l'écran d'échange s'ouvre sur USDT (TRC20),
    annonce « Solde : 0 USDT », et répond « Solde USDT vide. Reçois d'abord
    des USDT. » — alors que le portefeuille contient 3,10 USDT sur BNB Chain.

    La phrase est fausse dans ce qu'elle CONSEILLE. Elle envoie recevoir des
    USDT qu'on détient déjà, et laisse croire à une panne là où il n'y a
    qu'un sélecteur de réseau resté sur la mauvaise ligne. Quelqu'un qui la
    suit peut se faire envoyer des USDT sur Tron pour rien, en payant des
    frais, pendant que ses fonds dorment ailleurs.

    Le portefeuille, lui, SAIT où sont les fonds. Il n'y a aucune raison de
    le taire : on nomme le réseau qui les porte et le montant exact.

    ON NE CHANGE PAS DE RÉSEAU À SA PLACE. Basculer tout seul le ferait
    partir d'une autre chaîne que celle qu'il regardait — sur un écran qui
    déplace de l'argent, une substitution silencieuse est précisément ce
    qu'il ne faut pas faire. On dit où regarder ; il choisit.
    */
    private fun messageSoldeVide(token: String): String {
        val base = assetOf(token).base
        val ailleurs = SWAP_ASSETS
            .filter { it.base.equals(base, ignoreCase = true) && !it.key.equals(token, ignoreCase = true) }
            .map { it to balanceOf(it.key) }
            .filter { it.second > 0.0 }
            .maxByOrNull { it.second }
            ?: return str(com.vaultex.R.string.swap_msg_no_funds, base)
        return str(
            com.vaultex.R.string.swap_msg_funds_other_chain,
            base,
            "${trimNum(ailleurs.second)} $base",
            ailleurs.first.network
        )
    }

    /** Extrait la VRAIE raison d'un échec ChangeNOW (corps de la réponse HTTP),
     *  ou un message clair pour les pannes réseau (hors-ligne, délai dépassé). */
    private fun changeNowError(e: Throwable): String = when {
        e is java.net.UnknownHostException ->
            str(com.vaultex.R.string.msg_offline)
        e is java.net.SocketTimeoutException ->
            str(com.vaultex.R.string.msg_timeout)
        e is java.io.IOException ->
            str(com.vaultex.R.string.msg_offline)
        e is retrofit2.HttpException -> {
            val body = try { e.response()?.errorBody()?.string()?.trim()?.take(400) } catch (_: Exception) { null }
            parseChangeNowBody(body) ?: str(com.vaultex.R.string.swap_msg_generic)
        }
        else -> e.message ?: str(com.vaultex.R.string.swap_msg_generic)
    }

    /**
     * Traduit le JSON d'erreur ChangeNOW ({"error":"deposit_too_small","message":
     * "Out of min amount 0.14…"}) en message lisible, au lieu d'afficher le JSON
     * brut à l'utilisateur.
     */
    private fun parseChangeNowBody(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try {
            val obj = gson.fromJson(body, com.google.gson.JsonObject::class.java)
            val err = obj.get("error")?.takeIf { !it.isJsonNull }?.asString
            val msg = obj.get("message")?.takeIf { !it.isJsonNull }?.asString
            when {
                err == "deposit_too_small" || msg?.contains("min amount", true) == true -> {
                    val min = Regex("""[0-9]+(?:\.[0-9]+)?""").find(msg ?: "")?.value
                    if (min != null) str(com.vaultex.R.string.swap_msg_below_min, min, _state.value.fromToken)
                    else str(com.vaultex.R.string.swap_msg_below_min_generic, _state.value.fromToken, _state.value.toToken)
                }
                err == "pair_is_inactive" || err == "unavailable_pair" || err == "not_valid_pair" ->
                    str(com.vaultex.R.string.swap_msg_pair_unavailable, _state.value.fromToken, _state.value.toToken)
                !msg.isNullOrBlank() -> msg
                !err.isNullOrBlank() -> err
                else -> null
            }
        } catch (_: Exception) {
            // Pas du JSON : texte brut court plutôt que rien.
            body.take(160)
        }
    }

    /** Poll ChangeNOW toutes les 20 s jusqu'à un état terminal, et synchronise l'historique local. */
    fun trackSwapStatus(swapId: String) {
        statusJob?.cancel()
        statusJob = viewModelScope.launch {
            // Un swap DEPUIS BTC attend ses confirmations (souvent > 30 min) :
            // l'ancien plafond de 30 min arrêtait le suivi EN SILENCE et la notif
            // de fin ne partait jamais. On suit jusqu'à 4 h : toutes les 20 s la
            // première demi-heure, puis toutes les 60 s.
            var elapsedMs = 0L
            while (elapsedMs < 4 * 60 * 60_000L) {
                val stepMs = if (elapsedMs < 30 * 60_000L) 20_000L else 60_000L
                kotlinx.coroutines.delay(stepMs)
                elapsedMs += stepMs
                val statusDto = withContext(Dispatchers.IO) { swapUseCase.refreshSwapStatus(swapId) }
                val remote = statusDto?.statut
                if (remote != null) {
                    _state.update { it.copy(swapStatus = remote) }
                    // Dès l'étape « sending », ChangeNOW a DIFFUSÉ le versement et
                    // fournit un payoutHash : la monnaie reçue est en route mais pas
                    // encore confirmée sur sa chaîne. On affiche donc le badge « En
                    // attente » sur le dashboard le plus tôt possible (track() ignore
                    // les doublons, l'appeler à chaque tour est sans effet). Le badge
                    // s'efface tout seul dès la confirmation on-chain du versement.
                    statusDto?.hashSortie?.takeIf { it.isNotBlank() }?.let { payHash ->
                        pendingTxManager.track(
                            assetOf(_state.value.toToken).base,
                            assetOf(_state.value.toToken).chain,
                            payHash
                        )
                    }
                    if (remote in listOf("finished", "failed", "refunded", "expired")) {
                        // Événement admin (Telegram) : issue du swap — indépendant
                        // des préférences de notification de l'utilisateur.
                        run {
                            val st = _state.value
                            if (remote == "finished") {
                                /*
                                Revenu estimé de ce swap, en dollars.

                                Calculé sur la commission RÉELLE du fournisseur
                                en service — et non sur l'ancienne constante de
                                1,5 %, qui donnait un revenu imaginaire : rien
                                n'était prélevé, donc le suivi comptait de
                                l'argent qui n'arrivait jamais.

                                Reste une ESTIMATION : le versement effectif se
                                lit dans le tableau de bord partenaire, et peut
                                différer du montant déposé si la paire a bougé.
                                */
                                val usdFee = (st.fromAmount.toDoubleOrNull() ?: 0.0) *
                                    (commissionPourcent / 100.0) * st.fromPriceUsd
                                com.vaultex.core.monitoring.AdminBot.swapFinished(
                                    st.fromAmount, assetOf(st.fromToken).base, assetOf(st.toToken).base, usdFee)
                                // Jalon de MONÉTISATION : tout premier swap abouti.
                                com.vaultex.core.monitoring.AdminBot.milestoneFirstSwap(
                                    assetOf(st.fromToken).base, assetOf(st.toToken).base, usdFee)
                            }
                            else
                                com.vaultex.core.monitoring.AdminBot.swapFailed(
                                    assetOf(st.fromToken).base, assetOf(st.toToken).base, remote)
                        }
                        // Centre de notifications (cloche) : trace le swap terminé,
                        // au même titre que les envois/réceptions. Respecte
                        // l'interrupteur « Alertes transactions ».
                        if (notifPrefs.txAlerts.value) {
                            val st = _state.value
                            val fromSym = assetOf(st.fromToken).base
                            val toSym = assetOf(st.toToken).base
                            if (remote == "finished") {
                                hub.post(
                                    key = "swap:done:$swapId",
                                    title = str(com.vaultex.R.string.notif_swap_done_title),
                                    body = str(com.vaultex.R.string.notif_swap_done_body, st.fromAmount, fromSym, toSym),
                                    symbol = toSym
                                )
                            } else {
                                hub.post(
                                    key = "swap:failed:$swapId",
                                    title = str(com.vaultex.R.string.notif_swap_failed_title),
                                    body = str(com.vaultex.R.string.notif_swap_failed_body, fromSym, toSym),
                                    symbol = fromSym
                                )
                            }
                        }
                        return@launch
                    }
                }
            }
        }
    }

    override fun onCleared() {
        statusJob?.cancel()
        super.onCleared()
    }

    fun resetSwap() = _state.update { SwapState() }
}
