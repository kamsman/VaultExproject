package com.vaultex.ui.components

/**
 * Source unique des logos crypto, par TICKER → URL prévisible et stable.
 * Le même symbole donne donc le même logo partout dans l'app (et sur tout
 * appareil). Les variantes (ex. USDT-BNB) sont ramenées au ticker de base.
 *
 * Repo communautaire d'icônes par ticker (mis en cache par Coil après le
 * 1er chargement, donc disponible hors-ligne ensuite).
 */
object CryptoIcon {

    fun url(symbol: String): String {
        val ticker = symbol.substringBefore("-").trim().lowercase()
        return "https://raw.githubusercontent.com/spothq/cryptocurrency-icons/master/128/color/$ticker.png"
    }

    /**
     * Logo d'un token ajouté par ADRESSE DE CONTRAT (ERC-20/BEP-20). Le jeu
     * d'icônes par ticker (spothq) ne couvre que les monnaies majeures : un
     * token importé par contrat (ticker exotique, doublon de symbole…) y est
     * quasi toujours absent → logo manquant. Le dépôt communautaire Trust
     * Wallet indexe ses logos PAR CONTRAT et couvre des milliers de tokens
     * ERC-20/BEP-20 — bien mieux adapté ici. Repli sur [url] si pas de contrat
     * (monnaie native) ; si le contrat n'y est pas non plus, l'app affiche
     * déjà les initiales en repli (AsyncImage superposé à un avatar-lettres).
     */
    fun urlFor(symbol: String, contractAddress: String?, chainTicker: String?): String {
        if (contractAddress.isNullOrBlank()) return url(symbol)
        val chainPath = if (chainTicker.equals("BNB", ignoreCase = true)) "smartchain" else "ethereum"
        return "https://raw.githubusercontent.com/trustwallet/assets/master/blockchains/$chainPath/assets/$contractAddress/logo.png"
    }
}
