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
    affichage d'une ligne Pi, et retombait sur les initiales.

    ─── POURQUOI PAS UNE IMAGE DESSINÉE PAR NOUS ───────────────────────

    Un premier correctif embarquait un fichier : la lettre grecque pi, en
    blanc sur le violet de la charte Pi. Il s'affichait correctement, et il
    était quand même mauvais — parce qu'il ne RESSEMBLAIT à rien d'autre.

    Toutes les autres lignes de l'accueil portent la vraie marque de leur
    monnaie. Une pastille dessinée à la main au milieu d'elles ne se lit pas
    « logo de remplacement » : elle se lit « cette monnaie-là est bricolée ».
    Et l'écran Marché, lui, affichait déjà le vrai logo du Pi — fourni par
    CoinGecko avec le reste des données de marché. Deux logos différents
    pour la même monnaie dans la même application.

    ─── ON PREND LA MÊME SOURCE QUE L'ÉCRAN MARCHÉ ─────────────────────

    CoinGecko héberge le logo officiel de chaque monnaie, et l'écran Marché
    s'en sert déjà pour les dix-neuf mille qu'il liste. Pointer ici la même
    image aligne tous les écrans sur une seule et même source : ce n'est pas
    une dépendance de plus, c'est celle qui existait déjà.

    L'adresse est fixe et ne contient aucune clé. Si elle venait à ne plus
    répondre, le comportement est celui d'avant ce correctif — les initiales
    en repli, dessinées par les appelants.

    UN SEUL ENDROIT POUR TRENTE-ET-UN APPELS. C'est la raison d'être de
    cette classe, rappelée par son en-tête : corriger ici corrige l'accueil,
    le Marché, l'envoi, la réception, les notifications et les toasts d'un
    coup. Aller modifier les appelants aurait produit trente-et-une
    occasions d'en oublier un.
    ═══════════════════════════════════════════════════════════════════════
    */
    private val LOGOS_PARTICULIERS = mapOf(
        "PI" to "https://coin-images.coingecko.com/coins/images/54342/large/pi_network.jpg"
    )

    fun url(symbol: String): String {
        val ticker = symbol.substringBefore("-").trim()
        LOGOS_PARTICULIERS[ticker.uppercase()]?.let { return it }
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
