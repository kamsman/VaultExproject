package com.vaultex.core.session

/**
 * Tampon de DÉPÔT de swap : après création d'un ordre ChangeNOW, on doit
 * envoyer les fonds vers l'adresse de dépôt (payin). On passe par l'écran
 * Envoyer (déjà testé + biométrie) en pré-remplissant chaîne + adresse +
 * montant exact attendu par ChangeNOW. Consommé une seule fois.
 */
object SwapDepositBuffer {

    data class Deposit(val chain: String, val address: String, val amount: String)

    @Volatile
    private var pending: Deposit? = null

    fun set(chain: String, address: String, amount: String) {
        pending = Deposit(chain, address, amount)
    }

    fun consume(): Deposit? {
        val d = pending
        pending = null
        return d
    }
}
