package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.core.config.ApiKeys
import com.vaultex.core.crypto.WalletManager
import com.vaultex.core.security.SecureStorage
import com.vaultex.data.remote.api.ChangeNowApi
import com.vaultex.data.remote.dto.ChangeNowTransactionBody
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
    val vaultexFeePercent: Double = SwapUseCase.VAULTEX_FEE_PERCENT,
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
    private val changeNowApi: ChangeNowApi,
    private val secureStorage: SecureStorage,
    private val swapUseCase: SwapUseCase,
    private val sendCryptoUseCase: com.vaultex.domain.usecase.SendCryptoUseCase,
    private val tokenRepository: com.vaultex.data.repository.TokenRepository,
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
        private val CHANGENOW_API_KEY get() = ApiKeys.CHANGENOW

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
    }

    private val _state = MutableStateFlow(SwapState())
    val state: StateFlow<SwapState> = _state.asStateFlow()

    private val gson = com.google.gson.Gson()
    private data class SnapLite(val tokens: List<TokLite>?)
    private data class TokLite(val symbol: String = "", val amountRaw: Double = 0.0, val priceUsd: Double = 0.0, val priceXof: Double = 0.0)

    init {
        _state.update { it.copy(
            fromBalance = balanceOf(it.fromToken),
            fromPriceUsd = priceUsdOf(it.fromToken),
            toPriceUsd = priceUsdOf(it.toToken)
        ) }
    }

    private fun snapTok(token: String): TokLite? {
        val json = secureStorage.getPortfolioSnapshot() ?: return null
        return try {
            gson.fromJson(json, SnapLite::class.java)?.tokens?.firstOrNull { it.symbol == token }
        } catch (_: Exception) { null }
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
    fun onMaxClicked() {
        val tok = _state.value.fromToken
        val bal = _state.value.fromBalance
        if (bal <= 0.0) {
            _state.update { it.copy(error = str(com.vaultex.R.string.swap_msg_no_funds, tok)) }
            return
        }
        val reserve = when (tok.uppercase()) {
            "BTC" -> 0.00002
            "ETH" -> 0.0003
            "BNB" -> 0.00005
            "SOL" -> 0.00001
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
        val amt = _state.value.fromAmount
        if (amt.isNotEmpty()) estimateOutput(amt)
    }

    fun setToToken(token: String) {
        _state.update { it.copy(toToken = token, toPriceUsd = priceUsdOf(token)) }
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

    fun swapTokens() = _state.update {
        it.copy(
            fromToken = it.toToken, toToken = it.fromToken,
            fromAmount = it.toAmount, toAmount = it.fromAmount,
            fromBalance = balanceOf(it.toToken),
            fromPriceUsd = priceUsdOf(it.toToken), toPriceUsd = priceUsdOf(it.fromToken)
        )
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
                val fromTo = "${assetOf(_state.value.fromToken).cn}_${assetOf(_state.value.toToken).cn}"
                // Réseau lent : on retente UNE fois automatiquement sur timeout /
                // coupure avant d'afficher une erreur à l'utilisateur.
                val est = withContext(Dispatchers.IO) {
                    try {
                        changeNowApi.getEstimatedAmount(amount = apiAmount(input), fromTo = fromTo, apiKey = CHANGENOW_API_KEY)
                    } catch (e: Exception) {
                        if (e is java.net.SocketTimeoutException || e is java.net.UnknownHostException) {
                            kotlinx.coroutines.delay(1200)
                            changeNowApi.getEstimatedAmount(amount = apiAmount(input), fromTo = fromTo, apiKey = CHANGENOW_API_KEY)
                        } else throw e
                    }
                }
                // Ignorer les devis obsolètes (l'utilisateur a déjà changé le montant).
                if (_state.value.fromAmount != amount) return@launch
                _state.update { it.copy(toAmount = est.estimatedAmount, error = null) }
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
                val toAddress = if (mnemonic != null) {
                    val addresses = withContext(Dispatchers.IO) { WalletManager.deriveAddresses(mnemonic, secureStorage.getPassphrase()) }
                    when (assetOf(s.toToken).chain) {
                        "ETH" -> addresses.eth
                        "BNB" -> addresses.bnb
                        "BTC" -> addresses.btc
                        "SOL" -> addresses.sol
                        "TRX" -> addresses.trx
                        else  -> addresses.eth
                    }
                } else {
                    _state.update { it.copy(isLoading = false, error = str(com.vaultex.R.string.swap_msg_wallet)) }
                    return@launch
                }

                val fromTo = "${assetOf(s.fromToken).cn}_${assetOf(s.toToken).cn}"

                // Vérifier le montant minimum
                val minRes = withContext(Dispatchers.IO) {
                    changeNowApi.getMinAmount(fromTo, CHANGENOW_API_KEY)
                }
                if (net < minRes.minAmount) {
                    _state.update { it.copy(isLoading = false, error = str(com.vaultex.R.string.swap_msg_below_min, trimNum(minRes.minAmount), s.fromToken)) }
                    return@launch
                }

                // Créer la transaction ChangeNOW
                val txRes = withContext(Dispatchers.IO) {
                    changeNowApi.createTransaction(
                        apiKey = CHANGENOW_API_KEY,
                        body = ChangeNowTransactionBody(
                            from = assetOf(s.fromToken).cn,
                            to = assetOf(s.toToken).cn,
                            address = toAddress,
                            amount = apiAmount(net)
                        )
                    )
                }
                swapUseCase.recordSwap(
                    swapId = txRes.id,
                    fromToken = s.fromToken,
                    toToken = s.toToken,
                    amount = apiAmount(net),
                    payinAddress = txRes.payinAddress,
                    payoutAddress = toAddress
                )
                // Token ERC-20/BEP-20 reçu : on l'ajoute au portefeuille pour que
                // le solde reçu soit VISIBLE (sinon le user croit ne rien recevoir).
                val toAsset = assetOf(s.toToken)
                if (toAsset.contract != null && toAsset.key != "USDT-ETH" && toAsset.key != "USDT-BNB") {
                    try {
                        tokenRepository.addToken(
                            com.vaultex.data.local.entity.TokenEntity(
                                contractAddress = toAsset.contract,
                                blockchain = if (toAsset.chain == "BNB") "BNB" else "ETH",
                                symbol = toAsset.base,
                                name = toAsset.base,
                                decimals = toAsset.decimals,
                                iconUrl = com.vaultex.ui.components.CryptoIcon.url(toAsset.base),
                                isCustom = true
                            )
                        )
                    } catch (_: Exception) { /* déjà présent : sans effet */ }
                }
                _state.update {
                    it.copy(
                        swapId = txRes.id,
                        payinAddress = txRes.payinAddress,
                        depositAmount = apiAmount(net),
                        swapInProgress = true,
                        swapStatus = "depositing"
                    )
                }

                // DÉPÔT AUTOMATIQUE (comme Trust Wallet) : on envoie nous-mêmes les
                // fonds vers l'adresse payin via le moteur d'envoi déjà testé.
                val depChain = swapSendChainOf(s.fromToken)
                val dep = withContext(Dispatchers.IO) {
                    sendCryptoUseCase.sendByChain(depChain, txRes.payinAddress, apiAmount(net))
                }
                when (dep) {
                    is SendCryptoUseCase.Result.Success -> {
                        _state.update { it.copy(isLoading = false, depositTxHash = dep.txHash, swapStatus = "waiting") }
                        com.vaultex.core.session.BalanceRefreshSignal.signalTxSent()
                        trackSwapStatus(txRes.id)
                    }
                    is SendCryptoUseCase.Result.Error -> {
                        _state.update { it.copy(isLoading = false, swapInProgress = false, swapStatus = null,
                            error = str(com.vaultex.R.string.swap_msg_deposit_failed, dep.message)) }
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, swapInProgress = false, error = changeNowError(e)) }
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
            repeat(90) { // ~30 min max
                kotlinx.coroutines.delay(20_000)
                val remote = withContext(Dispatchers.IO) { swapUseCase.refreshSwapStatus(swapId) }
                if (remote != null) {
                    _state.update { it.copy(swapStatus = remote) }
                    if (remote in listOf("finished", "failed", "refunded", "expired")) return@launch
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
