package com.vaultex.core.util

import java.text.NumberFormat
import java.util.Locale

/**
 * Formatage des montants selon la devise d'affichage (USD / EUR / XOF).
 * USD : "$1 234.56"  ·  EUR : "1 234,56 €"  ·  XOF : "1 234 FCFA".
 */
object CurrencyFormat {

    /** Symbole court de la devise. */
    fun symbol(code: String): String = when (code) {
        "EUR" -> "€"
        "XOF" -> "FCFA"
        else -> "$"
    }

    /** Montant formaté avec le symbole, selon la devise. */
    fun format(amount: Double, code: String): String {
        val nf = NumberFormat.getNumberInstance(Locale.FRANCE)
        return when (code) {
            "XOF" -> {
                nf.maximumFractionDigits = 0
                "${nf.format(amount)} FCFA"
            }
            "EUR" -> {
                nf.minimumFractionDigits = 2; nf.maximumFractionDigits = 2
                "${nf.format(amount)} €"
            }
            else -> {
                nf.minimumFractionDigits = 2; nf.maximumFractionDigits = 2
                "$${nf.format(amount)}"
            }
        }
    }

    /** Prix unitaire (plus de décimales sous 1). */
    fun formatPrice(amount: Double, code: String): String {
        val nf = NumberFormat.getNumberInstance(Locale.FRANCE)
        nf.maximumFractionDigits = if (amount < 1.0) 4 else 2
        val n = nf.format(amount)
        return when (code) {
            "XOF" -> "$n FCFA"
            "EUR" -> "$n €"
            else -> "$$n"
        }
    }
}
