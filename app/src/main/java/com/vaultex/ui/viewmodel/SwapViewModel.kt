package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.core.config.ApiKeys
import com.vaultex.core.crypto.WalletManager
import com.vaultex.core.security.SecureStorage
import com.vaultex.data.remote.api.ChangeNowApi
import com.vaultex.data.remote.dto.ChangeNowTransactionBody
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
    val estimatedFee: String = "",
    val minAmount: Double? = null,
    val vaultexFeePercent: Double = SwapUseCase.VAULTEX_FEE_PERCENT,
    val isCrossChain: Boolean = true,
    val isLoading: Boolean = false,
    val error: String? = null,
    val swapId: String? = null,         // ChangeNOW transaction ID
    val payinAddress: String? = null,   // adresse où envoyer pour déclencher le swap
    val swapStatus: String? = null      // waiting/confirming/exchanging/sending/finished/failed
)

@HiltViewModel
class SwapViewModel @Inject constructor(
    private val changeNowApi: ChangeNowApi,
    private val secureStorage: SecureStorage,
    private val swapUseCase: SwapUseCase
) : ViewModel() {

    private var statusJob: kotlinx.coroutines.Job? = null

    companion object {
        private val CHANGENOW_API_KEY get() = ApiKeys.CHANGENOW
    }

    private val _state = MutableStateFlow(SwapState())
    val state: StateFlow<SwapState> = _state.asStateFlow()

    fun setFromToken(token: String) {
        _state.update { it.copy(fromToken = token) }
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
        it.copy(fromToken = it.toToken, toToken = it.fromToken, fromAmount = it.toAmount, toAmount = it.fromAmount)
    }

    private fun estimateOutput(amount: String) {
        viewModelScope.launch {
            val input = amount.toDoubleOrNull() ?: return@launch
            val (fee, net) = SwapUseCase.applyFee(input)
            try {
                val fromTo = "${_state.value.fromToken.lowercase()}_${_state.value.toToken.lowercase()}"
                val est = withContext(Dispatchers.IO) {
                    changeNowApi.getEstimatedAmount(
                        amount = String.format("%.6f", net),
                        fromTo = fromTo,
                        apiKey = CHANGENOW_API_KEY
                    )
                }
                _state.update { it.copy(toAmount = est.estimatedAmount, estimatedFee = String.format("%.6f", fee)) }
            } catch (_: Exception) {
                // Estimation locale fallback
                _state.update { it.copy(toAmount = String.format("%.4f", net * 0.97), estimatedFee = String.format("%.6f", fee)) }
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
                val (_, net) = SwapUseCase.applyFee(inputAmount)

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

                val fromTo = "${s.fromToken.lowercase()}_${s.toToken.lowercase()}"

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
                            from = s.fromToken.lowercase(),
                            to = s.toToken.lowercase(),
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
                        isLoading = false,
                        swapId = txRes.id,
                        payinAddress = txRes.payinAddress,
                        swapStatus = "waiting"
                    )
                }
                trackSwapStatus(txRes.id)
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Erreur swap") }
            }
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
