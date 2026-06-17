package com.vaultex.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.R
import com.vaultex.core.security.SecureStorage
import com.vaultex.core.session.NetworkMonitor
import com.vaultex.core.validation.AddressValidator
import com.vaultex.data.local.dao.PendingSendDao
import com.vaultex.data.local.entity.PendingSendEntity
import com.vaultex.domain.usecase.SendCryptoUseCase
import com.vaultex.service.PendingSendWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SendState(
    val selectedChain: String = "USDT",
    val toAddress: String = "",
    val amount: String = "",
    val estimatedFee: String = "",
    val isAddressValid: Boolean = false,
    val isLoading: Boolean = false,
    val txHash: String? = null,
    val error: String? = null,
    val queued: Boolean = false,
    val dustWarning: String? = null,
    // Solde disponible de la chaîne sélectionnée (lu depuis le cache portefeuille).
    val availableBalance: String? = null
)

@HiltViewModel
class SendViewModel @Inject constructor(
    private val sendCryptoUseCase: SendCryptoUseCase,
    private val pendingSendDao: PendingSendDao,
    private val secureStorage: SecureStorage,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(SendState())
    val state: StateFlow<SendState> = _state.asStateFlow()

    private val gson = com.google.gson.Gson()

    init {
        _state.update { it.copy(availableBalance = availableFor(it.selectedChain)) }
    }

    fun setChain(chain: String) {
        val addr = _state.value.toAddress
        val valid = if (addr.isEmpty()) false else AddressValidator.isValid(addr, chain)
        val warning = dustWarning(chain, _state.value.amount)
        _state.update {
            it.copy(
                selectedChain = chain,
                isAddressValid = valid,
                error = null,
                dustWarning = warning,
                availableBalance = availableFor(chain)
            )
        }
    }

    fun setToAddress(address: String) {
        val chain = _state.value.selectedChain
        val valid = AddressValidator.isValid(address, chain)
        _state.update { it.copy(toAddress = address, isAddressValid = valid) }
    }

    fun setAmount(amount: String) {
        val warning = dustWarning(_state.value.selectedChain, amount)
        _state.update { it.copy(amount = amount, dustWarning = warning, error = null) }
    }

    /**
     * Bouton MAX : remplit le montant avec le solde disponible de la chaîne.
     * Pour une monnaie NATIVE (BNB/ETH/BTC/SOL/TRX), on retranche une petite
     * réserve pour couvrir les frais de réseau (sinon l'envoi du solde entier
     * est refusé faute de gas). Pour un token (USDT*), gas payé en natif → on
     * peut envoyer tout le solde. Si aucun solde connu, message explicite.
     */
    fun onMaxClicked() {
        val chain = _state.value.selectedChain
        val balance = availableFor(chain)?.toBigDecimalOrNull()
        if (balance == null || balance.signum() <= 0) {
            _state.update { it.copy(error = appContext.getString(R.string.send_no_balance)) }
            return
        }
        val reserve = NATIVE_FEE_RESERVE[chain]?.let { java.math.BigDecimal.valueOf(it) }
            ?: java.math.BigDecimal.ZERO
        val spendable = balance.subtract(reserve)
        if (spendable.signum() <= 0) {
            _state.update { it.copy(error = appContext.getString(R.string.send_no_balance)) }
            return
        }
        setAmount(
            spendable.setScale(8, java.math.RoundingMode.DOWN)
                .stripTrailingZeros()
                .toPlainString()
        )
    }

    private fun dustWarning(chain: String, amount: String): String? {
        val value = amount.replace(",", ".").toDoubleOrNull() ?: return null
        val minimum = MINIMUM_AMOUNTS[chain] ?: return null
        return if (value < minimum) "$minimum $chain" else null
    }

    /** Lit le solde de [chain] dans l'instantané portefeuille (aucun appel réseau). */
    private fun availableFor(chain: String): String? {
        val json = secureStorage.getPortfolioSnapshot() ?: return null
        return try {
            val snap = gson.fromJson(json, SnapshotLite::class.java) ?: return null
            val raw = snap.tokens?.firstOrNull { it.symbol == chain }?.amountFormatted ?: return null
            val numeric = raw.substringBefore(" ").replace(" ", "").replace(",", ".")
            if (numeric.toDoubleOrNull() != null) numeric else null
        } catch (_: Exception) { null }
    }

    /** Traduit un message d'erreur technique en message utilisateur clair. */
    private fun friendlyError(raw: String?): String {
        val m = (raw ?: "").lowercase()
        return when {
            m.contains("insufficient funds") || m.contains("overshot") || m.contains("tx cost") ->
                appContext.getString(R.string.send_err_insufficient)
            m.contains("nonce") ->
                appContext.getString(R.string.send_err_nonce)
            m.contains("underpriced") || m.contains("fee too low") || m.contains("gas price") ->
                appContext.getString(R.string.send_err_fee)
            m.contains("timeout") || m.contains("timed out") ||
                m.contains("unable to resolve host") || m.contains("failed to connect") ->
                appContext.getString(R.string.send_err_network)
            else -> appContext.getString(R.string.send_err_generic)
        }
    }

    private data class SnapshotLite(val tokens: List<TokenLite>?)
    private data class TokenLite(val symbol: String = "", val amountFormatted: String = "")

    companion object {
        // Réserve de frais retranchée par MAX sur les monnaies NATIVES, pour que
        // l'envoi « tout le solde » couvre le gas. Valeurs volontairement
        // prudentes ; les tokens (USDT*) ne sont pas listés (gas payé en natif).
        private val NATIVE_FEE_RESERVE = mapOf(
            "BTC" to 0.0003,
            "ETH" to 0.0008,
            "BNB" to 0.0002,
            "SOL" to 0.0001,
            "TRX" to 2.0
        )

        private val MINIMUM_AMOUNTS = mapOf(
            "BTC"      to 0.00000546,
            "ETH"      to 0.0001,
            "BNB"      to 0.0001,
            "SOL"      to 0.000001,
            "TRX"      to 0.000001,
            "USDT"     to 1.0,
            "USDT-ETH" to 1.0,
            "USDT-BNB" to 1.0
        )
    }

    fun send() {
        val s = _state.value
        if (s.isLoading) return
        if (!s.isAddressValid || s.amount.isEmpty()) return

        // Hors-ligne : on met l'INTENTION en file. Elle sera signée (avec un
        // nonce/blockhash frais) et diffusée automatiquement, une seule fois,
        // au retour du réseau via PendingSendWorker.
        if (!NetworkMonitor.isOnline(appContext)) {
            viewModelScope.launch {
                try {
                    pendingSendDao.insert(
                        PendingSendEntity(
                            chain = s.selectedChain,
                            toAddress = s.toAddress,
                            amount = s.amount,
                            status = PendingSendWorker.STATUS_PENDING,
                            txHash = null,
                            lastError = null,
                            attempts = 0,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    PendingSendWorker.enqueue(appContext)
                    _state.update { it.copy(queued = true, error = null) }
                } catch (e: Exception) {
                    _state.update { it.copy(error = friendlyError(e.message)) }
                }
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = sendCryptoUseCase.sendByChain(s.selectedChain, s.toAddress, s.amount)
            when (result) {
                is SendCryptoUseCase.Result.Success -> _state.update { it.copy(isLoading = false, txHash = result.txHash) }
                is SendCryptoUseCase.Result.Error   -> _state.update { it.copy(isLoading = false, error = friendlyError(result.message)) }
            }
        }
    }

    fun reset() = _state.update { SendState() }
}
