package com.vaultex.core.session

import com.vaultex.core.validation.AddressValidator

/**
 * Tampon de deep link (P5). Une adresse reçue via un lien
 * bitcoin:/ethereum:/vaultex: n'est mémorisée QUE si elle passe
 * AddressValidator pour la chaîne correspondante. Elle est ensuite
 * consommée une seule fois par l'écran d'envoi.
 */
object DeepLinkBuffer {

    data class Target(val chain: String, val address: String)

    @Volatile
    private var pending: Target? = null

    /**
     * Parse + valide une URI de paiement. Renseigne le tampon seulement
     * si l'adresse est valide. Retourne true si une cible valide a été posée.
     */
    fun offer(uri: String?): Boolean {
        val target = parse(uri) ?: return false
        if (!AddressValidator.isValid(target.address, target.chain)) return false
        pending = target
        return true
    }

    fun hasPending(): Boolean = pending != null

    /** Récupère et efface la cible en attente. */
    fun consume(): Target? {
        val t = pending
        pending = null
        return t
    }

    private fun parse(uri: String?): Target? {
        if (uri.isNullOrBlank()) return null
        val scheme = uri.substringBefore(":", "").lowercase()
        // partie après le scheme, sans les // éventuels, sans query (?amount=…)
        val rest = uri.substringAfter(":", "").removePrefix("//").substringBefore("?").trim()
        if (rest.isEmpty()) return null
        return when (scheme) {
            "bitcoin" -> Target("BTC", rest)
            "ethereum" -> Target("ETH", rest.substringAfter("@").removePrefix("pay-"))
            "vaultex" -> {
                // vaultex://send?chain=ETH&to=0x… ou vaultex:ETH:0x…
                val chain = Regex("chain=([A-Za-z]+)").find(uri)?.groupValues?.get(1)?.uppercase()
                val to = Regex("to=([^&]+)").find(uri)?.groupValues?.get(1)
                if (chain != null && to != null) Target(chain, to) else null
            }
            else -> null
        }
    }
}
