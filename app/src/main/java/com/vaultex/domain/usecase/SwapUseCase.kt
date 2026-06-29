package com.vaultex.domain.usecase

import com.vaultex.core.config.ApiKeys
import com.vaultex.data.local.dao.TransactionDao
import com.vaultex.data.local.entity.TransactionEntity
import com.vaultex.data.remote.api.ChangeNowApi
import com.vaultex.data.remote.dto.ChangeNowStatusDto
import javax.inject.Inject

/**
 * Logique métier du swap cross-chain via ChangeNOW :
 * validation, frais VaultEx, suivi de statut et persistance dans
 * l'historique local (table transactions, type "swap").
 */
class SwapUseCase @Inject constructor(
    private val changeNowApi: ChangeNowApi,
    private val transactionDao: TransactionDao
) {

    sealed class ValidationResult {
        data object Valid : ValidationResult()
        data class Invalid(val reason: Reason) : ValidationResult()
        enum class Reason { INVALID_AMOUNT, SAME_TOKEN, BELOW_MINIMUM }
    }

    /** Validation complète avant création du swap. */
    suspend fun validate(fromToken: String, toToken: String, amount: Double?): ValidationResult {
        if (amount == null || amount <= 0.0) {
            return ValidationResult.Invalid(ValidationResult.Reason.INVALID_AMOUNT)
        }
        if (fromToken.equals(toToken, ignoreCase = true)) {
            return ValidationResult.Invalid(ValidationResult.Reason.SAME_TOKEN)
        }
        val (_, net) = applyFee(amount)
        val min = getMinAmount(fromToken, toToken)
        if (min != null && net < min) {
            return ValidationResult.Invalid(ValidationResult.Reason.BELOW_MINIMUM)
        }
        return ValidationResult.Valid
    }

    /** Montant minimum ChangeNOW pour la paire, ou null si l'appel échoue. */
    suspend fun getMinAmount(fromToken: String, toToken: String): Double? = try {
        changeNowApi.getMinAmount(pair(fromToken, toToken), ApiKeys.CHANGENOW).minAmount
    } catch (_: Exception) {
        null
    }

    /** Statut courant d'un swap ChangeNOW (waiting/confirming/exchanging/sending/finished/failed). */
    suspend fun getStatus(swapId: String): ChangeNowStatusDto? = try {
        changeNowApi.getTransactionStatus(swapId, ApiKeys.CHANGENOW)
    } catch (_: Exception) {
        null
    }

    /** Enregistre le swap créé dans l'historique local. */
    suspend fun recordSwap(
        swapId: String,
        fromToken: String,
        toToken: String,
        amount: String,
        payinAddress: String,
        payoutAddress: String
    ) {
        transactionDao.insertIgnore(
            TransactionEntity(
                hash = swapId,
                type = "swap",
                blockchain = fromToken.uppercase(),
                fromAddress = payinAddress,
                toAddress = payoutAddress,
                amount = amount,
                tokenSymbol = "${fromToken.uppercase()}→${toToken.uppercase()}",
                fee = "%.2f%%".format(VAULTEX_FEE_PERCENT),
                status = "pending",
                timestamp = System.currentTimeMillis(),
                confirmations = 0,
                blockNumber = null
            )
        )
    }

    /** Met à jour le statut local d'un swap depuis ChangeNOW. Retourne le statut distant. */
    suspend fun refreshSwapStatus(swapId: String): String? {
        val status = getStatus(swapId) ?: return null
        val localStatus = when (status.status) {
            "finished" -> "confirmed"
            "failed", "refunded", "expired" -> "failed"
            else -> "pending"
        }
        transactionDao.updateStatus(swapId, localStatus, if (localStatus == "confirmed") 1 else 0)
        return status.status
    }

    private fun pair(from: String, to: String) = "${cnTicker(from)}_${cnTicker(to)}"

    companion object {
        const val VAULTEX_FEE_PERCENT = 1.5
        const val MOBILE_MONEY_FEE_PERCENT = 1.0

        /**
         * Ticker ChangeNOW pour un symbole de l'app. ChangeNOW distingue les
         * réseaux : notre « USDT » est du TRC20 → « usdttrc20 ». Sans ça la
         * paire est invalide et l'API échoue.
         */
        fun cnTicker(token: String): String = when (token.uppercase()) {
            "USDT" -> "usdttrc20"   // notre USDT = TRC20
            "BNB"  -> "bnbbsc"      // BNB de BNB Chain (BSC) — « bnb » = ancienne Beacon Chain
            else   -> token.lowercase()
        }

        /** Retourne (frais, montant net après frais). */
        fun applyFee(amount: Double): Pair<Double, Double> {
            val fee = amount * (VAULTEX_FEE_PERCENT / 100.0)
            return Pair(fee, amount - fee)
        }
    }
}
