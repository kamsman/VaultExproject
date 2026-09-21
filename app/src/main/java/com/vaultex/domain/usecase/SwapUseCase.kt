package com.vaultex.domain.usecase

import com.vaultex.data.local.dao.TransactionDao
import com.vaultex.data.local.entity.TransactionEntity
import javax.inject.Inject

/**
 * Logique métier du swap cross-chain via ChangeNOW :
 * validation, frais VaultEx, suivi de statut et persistance dans
 * l'historique local (table transactions, type "swap").
 */
class SwapUseCase @Inject constructor(
    /*
    L'ÉCHANGEUR PASSE DERRIÈRE UNE INTERFACE.

    Ce cas d'usage appelait ChangeNOW directement, et son type de réponse
    remontait jusqu'au ViewModel : changer de fournisseur revenait à toucher
    l'affichage. Le nom de l'échangeur n'a rien à y faire.

    FournisseurSwap expose quatre opérations — coter, minimum, créer, suivre —
    et le module Hilt choisit l'implémentation selon `swap.provider`.
    */
    private val fournisseur: com.vaultex.domain.swap.FournisseurSwap,
    private val transactionDao: TransactionDao
) {

    /** Nom de l'échangeur en service, pour l'affichage. */
    val nomFournisseur: String get() = fournisseur.nom

    /**
     * Commission VaultEx réellement appliquée, en pourcentage.
     *
     * Elle est portée par la CLÉ d'API du fournisseur, pas par ce code :
     * l'application ne prélève rien elle-même. Cette valeur existe pour que
     * l'écran annonce ce qui est vraiment pris — il affichait 1,5 % alors
     * que rien ne l'était.
     */
    val commissionPourcent: Double get() = fournisseur.commissionPourcent

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
        // On échange le montant COMPLET → on compare le minimum au montant réel.
        val min = getMinAmount(fromToken, toToken)
        if (min != null && amount < min) {
            return ValidationResult.Invalid(ValidationResult.Reason.BELOW_MINIMUM)
        }
        return ValidationResult.Valid
    }

    /** Montant minimum de la paire chez le fournisseur, ou null s'il ne le dit pas. */
    suspend fun getMinAmount(fromToken: String, toToken: String): Double? =
        fournisseur.minimum(fromToken, toToken)

    /*
    ═══════════════════════════════════════════════════════════════════════
    LE DÉPÔT N'EST JAMAIS PARTI : L'ÉCHANGE N'AURA PAS LIEU
    ═══════════════════════════════════════════════════════════════════════

    recordSwap écrit la ligne dès que le FOURNISSEUR a créé l'échange —
    donc AVANT l'envoi des fonds. C'est voulu : si l'application meurt entre
    les deux, l'identifiant de l'échange n'est pas perdu.

    Mais quand le dépôt échoue ensuite — gaz insuffisant, réseau coupé,
    solde entamé entre-temps — rien ne repassait sur la ligne. Elle restait
    « pending » à vie. Constaté sur appareil : trois échanges « en cours »
    depuis sept et seize jours, pour lesquels pas un centime n'avait bougé.

    Ici, et seulement ici, on peut conclure sans risque. Le worker de suivi
    s'interdit de déclarer un échec après 24 h, et il a raison : les fonds
    sont partis, ils peuvent encore arriver. Dans le cas présent, c'est
    l'inverse — on SAIT que rien n'a quitté le portefeuille. L'échange
    expirera chez le fournisseur sans que personne n'ait rien perdu.
    */
    suspend fun markDepositFailed(swapId: String) {
        transactionDao.updateStatus(swapId, "failed", 0)
    }

    /** Statut courant d'un échange (waiting/confirming/exchanging/sending/finished/failed). */
    suspend fun getStatus(swapId: String): com.vaultex.domain.swap.StatutSwap? =
        fournisseur.statut(swapId)

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

    /** Met à jour le statut local d'un swap. Retourne le statut distant complet. */
    suspend fun refreshSwapStatus(swapId: String): com.vaultex.domain.swap.StatutSwap? {
        val status = getStatus(swapId) ?: return null
        val localStatus = when (status.statut) {
            "finished" -> "confirmed"
            "failed", "refunded", "expired" -> "failed"
            else -> "pending"
        }
        transactionDao.updateStatus(swapId, localStatus, if (localStatus == "confirmed") 1 else 0)
        return status
    }

    companion object {
        const val VAULTEX_FEE_PERCENT = 1.5

        /**
         * Ticker ChangeNOW pour un symbole de l'app. ChangeNOW distingue les
         * réseaux : notre « USDT » est du TRC20 → « usdttrc20 ». Sans ça la
         * paire est invalide et l'API échoue.
         */
        fun cnTicker(token: String): String = when (token.uppercase()) {
            "USDT" -> "usdttrc20"       // notre USDT = TRC20
            "USDT-ETH" -> "usdterc20"   // USDT sur Ethereum
            "USDT-BNB" -> "usdtbsc"     // USDT sur BNB Chain
            "BNB"  -> "bnbbsc"          // BNB de BNB Chain (BSC) — « bnb » = ancienne Beacon Chain
            else   -> token.lowercase()
        }

        /** Retourne (frais, montant net après frais). */
        fun applyFee(amount: Double): Pair<Double, Double> {
            val fee = amount * (VAULTEX_FEE_PERCENT / 100.0)
            return Pair(fee, amount - fee)
        }
    }
}
