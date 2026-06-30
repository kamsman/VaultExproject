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
    private val sendCryptoUseCase: com.vaultex.domain.usecase.SendCryptoUseCase
) : ViewModel() {

    private var statusJob: kotlinx.coroutines.Job? = null

    companion object {
        private val CHANGENOW_API_KEY get() = ApiKeys.CHANGENOW
    }

    private val _state = MutableStateFlow(SwapState())
    val state: StateFlow<SwapState> = _state.asStateFlow()

    private val gson = com.google.gson.Gson()
    private data class SnapLite(val tokens: List<TokLite>?)
    private data class TokLite(val symbol: String = "", val amountRaw: Double = 0.0)

    init { _state.update { it.copy(fromBalance = balanceOf(it.fromToken)) } }

    /** Solde de [token] lu dans l'instantané portefeuille (aucun appel réseau). */
    private fun balanceOf(token: String): Double {
        val json = secureStorage.getPortfolioSnapshot() ?: return 0.0
        return try {
            gson.fromJson(json, SnapLite::class.java)?.tokens
                ?.firstOrNull { it.symbol == token }?.amountRaw ?: 0.0
        } catch (_: Exception) { 0.0 }
    }

    /**
     * Bouton MAX : remplit avec le solde, en RÉSERVANT de quoi payer le gas du
     * DÉPÔT pour une monnaie native (sinon « MAX » prend tout le BNB/ETH et il
     * ne reste rien pour les frais → l'envoi du dépôt échoue). Pour un token
     * (USDT), le gas est payé en natif séparément → pas de réserve.
     */
    fun onMaxClicked() {
        val bal = _state.value.fromBalance
        if (bal <= 0.0) return
        val reserve = when (_state.value.fromToken.uppercase()) {
            "BTC" -> 0.00002
            "ETH" -> 0.0003
            "BNB" -> 0.00005
            "SOL" -> 0.00001
            "TRX" -> 0.5
            else  -> 0.0   // USDT & tokens : gas en natif séparé
        }
        val spendable = bal - reserve
        if (spendable <= 0.0) {
            // Solde trop faible pour couvrir les frais du dépôt → message clair
            // (au lieu d'un MAX silencieux qui « ne fait rien »).
            _state.update { it.copy(error = "Solde trop faible pour couvrir les frais réseau.") }
            return
        }
        val txt = java.math.BigDecimal.valueOf(spendable)
            .setScale(8, java.math.RoundingMode.DOWN).stripTrailingZeros().toPlainString()
        setFromAmount(txt)
    }

    fun setFromToken(token: String) {
        _state.update { it.copy(fromToken = token, fromBalance = balanceOf(token)) }
        val amt = _state.value.fromAmount
        if (amt.isNotEmpty()) estimateOutput(amt)
    }

    fun setToToken(token: String) {
        _state.update { it.copy(toToken = token) }
        val amt = _state.value.fromAmount
        if (amt.isNotEmpty()) estimateOutput(amt)
    }

    fun setFromAmount(amount: String) {
        _state.update { it.copy(fromAmount = amount, error = null) }
        if (amount.isNotEmpty()) estimateOutput(amount)
    }

    fun swapTokens() = _state.update {
        it.copy(
            fromToken = it.toToken, toToken = it.fromToken,
            fromAmount = it.toAmount, toAmount = it.fromAmount,
            fromBalance = balanceOf(it.toToken)
        )
    }

    private fun estimateOutput(amount: String) {
        viewModelScope.launch {
            val input = amount.toDoubleOrNull() ?: return@launch
            // On échange le montant COMPLET. La commission VaultEx vient de
            // ChangeNOW (programme partenaire), pas en rognant le montant.
            try {
                val fromTo = "${SwapUseCase.cnTicker(_state.value.fromToken)}_${SwapUseCase.cnTicker(_state.value.toToken)}"
                val est = withContext(Dispatchers.IO) {
                    changeNowApi.getEstimatedAmount(
                        amount = String.format("%.6f", input),
                        fromTo = fromTo,
                        apiKey = CHANGENOW_API_KEY
                    )
                }
                _state.update { it.copy(toAmount = est.estimatedAmount, error = null) }
            } catch (e: Exception) {
                // Pas de devis : on n'affiche PAS un faux montant (ça donnait
                // l'impression que « le montant ne change pas »). On vide + on
                // garde la raison pour l'écran.
                _state.update { it.copy(toAmount = "") }
            }
        }
    }

    fun executeSwap() {
        val s = _state.value
        if (s.isLoading) return
        val inputAmount = s.fromAmount.toDoubleOrNull() ?: run {
            _state.update { it.copy(error = "Montant invalide") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                when (val validation = swapUseCase.validate(s.fromToken, s.toToken, inputAmount)) {
                    is SwapUseCase.ValidationResult.Invalid -> {
                        val msg = when (validation.reason) {
                            SwapUseCase.ValidationResult.Reason.INVALID_AMOUNT -> "Montant invalide"
                            SwapUseCase.ValidationResult.Reason.SAME_TOKEN -> "Choisissez deux tokens différents"
                            SwapUseCase.ValidationResult.Reason.BELOW_MINIMUM -> "Montant inférieur au minimum requis"
                        }
                        _state.update { it.copy(isLoading = false, error = msg) }
                        return@launch
                    }
                    SwapUseCase.ValidationResult.Valid -> Unit
                }
                // Montant COMPLET déposé/échangé (commission via ChangeNOW).
                val net = inputAmount

                // Dériver l'adresse de réception pour le toToken
                val mnemonic = secureStorage.getMnemonic()
                val toAddress = if (mnemonic != null) {
                    val addresses = withContext(Dispatchers.IO) { WalletManager.deriveAddresses(mnemonic, secureStorage.getPassphrase()) }
                    when (s.toToken.uppercase()) {
                        "ETH", "USDC" -> addresses.eth
                        "BNB"         -> addresses.bnb
                        "BTC"         -> addresses.btc
                        "SOL"         -> addresses.sol
                        "TRX", "USDT" -> addresses.trx
                        else          -> addresses.eth
                    }
                } else {
                    _state.update { it.copy(isLoading = false, error = "Wallet non initialisé") }
                    return@launch
                }

                val fromTo = "${SwapUseCase.cnTicker(s.fromToken)}_${SwapUseCase.cnTicker(s.toToken)}"

                // Vérifier le montant minimum
                val minRes = withContext(Dispatchers.IO) {
                    changeNowApi.getMinAmount(fromTo, CHANGENOW_API_KEY)
                }
                if (net < minRes.minAmount) {
                    _state.update { it.copy(isLoading = false, error = "Montant minimum : ${minRes.minAmount} ${s.fromToken}") }
                    return@launch
                }

                // Créer la transaction ChangeNOW
                val txRes = withContext(Dispatchers.IO) {
                    changeNowApi.createTransaction(
                        apiKey = CHANGENOW_API_KEY,
                        body = ChangeNowTransactionBody(
                            from = SwapUseCase.cnTicker(s.fromToken),
                            to = SwapUseCase.cnTicker(s.toToken),
                            address = toAddress,
                            amount = String.format("%.6f", net)
                        )
                    )
                }
                swapUseCase.recordSwap(
                    swapId = txRes.id,
                    fromToken = s.fromToken,
                    toToken = s.toToken,
                    amount = String.format("%.6f", net),
                    payinAddress = txRes.payinAddress,
                    payoutAddress = toAddress
                )
                _state.update {
                    it.copy(
                        swapId = txRes.id,
                        payinAddress = txRes.payinAddress,
                        depositAmount = String.format("%.6f", net),
                        swapInProgress = true,
                        swapStatus = "depositing"
                    )
                }

                // DÉPÔT AUTOMATIQUE (comme Trust Wallet) : on envoie nous-mêmes les
                // fonds vers l'adresse payin via le moteur d'envoi déjà testé.
                val depChain = swapSendChainOf(s.fromToken)
                val dep = withContext(Dispatchers.IO) {
                    sendCryptoUseCase.sendByChain(depChain, txRes.payinAddress, String.format("%.6f", net))
                }
                when (dep) {
                    is SendCryptoUseCase.Result.Success -> {
                        _state.update { it.copy(isLoading = false, depositTxHash = dep.txHash, swapStatus = "waiting") }
                        com.vaultex.core.session.BalanceRefreshSignal.signalTxSent()
                        trackSwapStatus(txRes.id)
                    }
                    is SendCryptoUseCase.Result.Error -> {
                        _state.update { it.copy(isLoading = false, swapInProgress = false, swapStatus = null,
                            error = "Dépôt échoué : ${dep.message}") }
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, swapInProgress = false, error = changeNowError(e)) }
            }
        }
    }

    /** Chaîne d'envoi pour déposer la monnaie source (notre USDT = TRC20). */
    private fun swapSendChainOf(fromToken: String): String =
        if (fromToken.uppercase() == "USDT") "USDT" else fromToken.uppercase()

    /** Extrait la VRAIE raison d'un échec ChangeNOW (corps de la réponse HTTP),
     *  au lieu d'un « HTTP 400 » opaque. */
    private fun changeNowError(e: Throwable): String = when (e) {
        is retrofit2.HttpException -> {
            val body = try { e.response()?.errorBody()?.string()?.trim()?.take(220) } catch (_: Exception) { null }
            if (!body.isNullOrBlank()) body else "Erreur ChangeNOW (HTTP ${e.code()})"
        }
        else -> e.message ?: "Erreur swap"
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
