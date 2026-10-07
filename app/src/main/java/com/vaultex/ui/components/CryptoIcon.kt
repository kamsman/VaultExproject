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

    /*
    ═══════════════════════════════════════════════════════════════════════
    LES LOGOS QUE LE DÉPÔT COMMUNAUTAIRE N'A PAS
    ═══════════════════════════════════════════════════════════════════════

    Le jeu d'icônes spothq couvre les monnaies majeures et s'arrête là. Pour
    le Pi, l'URL répond 404 — vérifié : `btc.png` rend 200, `pi.png` rend
    404. L'application tentait donc une requête vouée à l'échec à CHAQUE
    affichage d'une ligne Pi, et retombait sur les initiales. Le Pi était la
    seule monnaie de l'accueil sans logo, au milieu de huit qui en ont.

    ON SERT DONC UN FICHIER EMBARQUÉ. Coil sait lire une URI
    `file:///android_asset/…`, et cette forme ne dépend pas du nom du
    paquet — ce qui compte ici, car la variante de débogage porte le suffixe
    « .debug » et une URI `android.resource://com.vaultex/…` y serait
    introuvable.

    UN SEUL ENDROIT POUR TRENTE-ET-UN APPELS. C'est la raison d'être de
    cette classe, rappelée par son en-tête : corriger ici corrige l'accueil,
    le Marché, l'envoi, la réception, les notifications et les toasts d'un
    coup. Aller modifier les appelants aurait produit trente-et-une
    occasions d'en oublier un.

    CE N'EST PAS LE LOGO DE PI NETWORK, et il ne faut pas le présenter comme
    tel : c'est la lettre grecque pi sur le violet de leur charte. Un
    symbole mathématique, pas une marque. Généré par tools/pi-logo.py, à
    remplacer si Pi Network publie un jeu d'icônes réutilisable.
    ═══════════════════════════════════════════════════════════════════════
    */
    private val EMBARQUES = mapOf(
        "PI" to "file:///android_asset/crypto/pi.png"
    )

    fun url(symbol: String): String {
        val ticker = symbol.substringBefore("-").trim()
        EMBARQUES[ticker.uppercase()]?.let { return it }
        return "https://raw.githubusercontent.com/spothq/cryptocurrency-icons/" +
            "master/128/color/${ticker.lowercase()}.png"
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
