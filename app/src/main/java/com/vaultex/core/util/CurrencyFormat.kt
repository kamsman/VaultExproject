package com.vaultex.core.util

import com.vaultex.core.session.LocaleManager
import java.text.NumberFormat

/**
 * Formatage des montants selon la devise d'affichage (USD / EUR / XOF).
 *
 * ── LANGUE ────────────────────────────────────────────────────────────────
 * Le format des nombres suit la LANGUE CHOISIE dans l'application, pas une
 * locale figée. Auparavant tout passait par `Locale.FRANCE` : un utilisateur
 * anglophone lisait « 1 872,38 » là où il attend « 1,872.38 », et un
 * arabophone la même chose. Sur des montants d'argent, une virgule prise pour
 * un séparateur de milliers est une erreur de lecture d'un facteur 100.
 *
 * On passe par [LocaleManager.appLocale] et non par `Locale.getDefault()` :
 * ce dernier n'est aligné sur le choix de l'utilisateur qu'après la création
 * d'une Activity, alors que les workers formatent des notifications
 * application fermée.
 */
object CurrencyFormat {

    private fun nf(): NumberFormat = NumberFormat.getNumberInstance(LocaleManager.appLocale())

    /** Symbole court de la devise. */
    fun symbol(code: String): String = when (code) {
        "EUR" -> "€"
        "XOF" -> "FCFA"
        else -> "$"
    }

    /** Montant formaté avec le symbole, selon la devise. */
    fun format(amount: Double, code: String): String {
        val n = nf()
        return when (code) {
            "XOF" -> {
                n.maximumFractionDigits = 0
                "${n.format(amount)} FCFA"
            }
            "EUR" -> {
                n.minimumFractionDigits = 2; n.maximumFractionDigits = 2
                "${n.format(amount)} €"
            }
            else -> {
                n.minimumFractionDigits = 2; n.maximumFractionDigits = 2
                "$${n.format(amount)}"
            }
        }
    }

    /**
     * Prix UNITAIRE d'une monnaie.
     *
     * La précision s'adapte à l'ordre de grandeur. Un nombre fixe de décimales
     * ne peut pas couvrir à la fois le Bitcoin et les mèmes-coins : avec quatre
     * décimales, SHIB (~0,00001 $) et PEPE (~0,000001 $) s'affichaient
     * « $0 » — un prix nul, donc un token qui a l'air cassé ou sans valeur,
     * alors que la donnée était parfaitement correcte. Ces deux monnaies sont
     * dans le registre d'échange : le cas n'est pas théorique.
     *
     * Règle : on garde toujours quatre chiffres SIGNIFICATIFS sous 0,01, ce qui
     * donne « $0,00001234 » pour SHIB sans allonger inutilement « $64 030 ».
     */
    fun formatPrice(amount: Double, code: String): String {
        val s = nombrePrix(amount)
        return when (code) {
            "XOF" -> "$s FCFA"
            "EUR" -> "$s €"
            else -> "$$s"
        }
    }

    /**
     * Le NOMBRE seul, sans symbole monétaire, avec la règle ci-dessus.
     *
     * ═══════════════════════════════════════════════════════════════════
     * POURQUOI CETTE FONCTION EXISTE SÉPARÉMENT
     * ═══════════════════════════════════════════════════════════════════
     *
     * CONSTATÉ SUR UNE CAPTURE de la fiche Dogecoin : l'axe vertical du
     * graphique affichait cinq fois « 0 » pour un cours de 0,0592 $. Il
     * passait par le formateur des CAPITALISATIONS — celui qui rend
     * « 9Md » et « 530M » — dont la dernière branche arrondit à l'entier.
     *
     * Juste, donc, pour une capitalisation. Faux pour un prix, et faux en
     * silence : un axe gradué de zéros ressemble à un graphique en panne,
     * pas à une erreur de format.
     *
     * LA RÈGLE EXISTAIT DÉJÀ ICI, et le commentaire du dessus décrit
     * exactement ce défaut sur SHIB. L'axe ne l'employait simplement pas.
     * En écrire une seconde version aurait garanti qu'elles divergent : un
     * jour quelqu'un corrige l'une, et l'autre continue d'afficher zéro.
     * ═══════════════════════════════════════════════════════════════════
     */
    fun nombrePrix(amount: Double): String {
        val n = nf()
        n.minimumFractionDigits = 0
        n.maximumFractionDigits = when {
            amount >= 1.0 -> 2
            amount >= 0.01 -> 4
            amount > 0.0 -> {
                // Nombre de zéros après la virgule avant le 1er chiffre
                // significatif : 0,00001234 → 4 → 8 décimales affichées.
                val leadingZeros = kotlin.math.floor(-kotlin.math.log10(amount)).toInt()
                (leadingZeros + 4).coerceIn(4, 12)
            }
            else -> 2
        }
        return n.format(amount)
    }
}
