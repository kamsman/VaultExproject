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

    ─── DEUX TENTATIVES DE CORRECTION, ET CE QU'ELLES ONT APPRIS ───────

    D'ABORD UNE IMAGE EMBARQUÉE : la lettre grecque pi sur le violet de la
    charte Pi. Elle s'affichait parfaitement, et elle était mauvaise — elle
    ne ressemblait à aucun des autres logos, tous de vraies marques. Au
    milieu d'eux, une pastille dessinée à la main se lit « cette monnaie-là
    est bricolée ».

    PUIS UNE ADRESSE COINGECKO ÉCRITE EN DUR, relevée dans une réponse de
    leur moteur de recherche. Elle ne répond pas depuis l'application.

    Le point commun des deux : ELLES DEVINENT. Et c'est tout ce que cette
    fonction sait faire — fabriquer un chemin à partir d'un symbole. Ça
    marche pour les monnaies majeures du dépôt spothq, et pour rien d'autre.

    ─── D'OÙ VIENNENT LES LOGOS MAINTENANT ─────────────────────────────

    L'écran Marché, lui, n'a jamais rien deviné : CoinGecko lui DONNE
    l'adresse de l'image avec les données de marché. C'est pourquoi il
    affiche le bon logo du Pi depuis le premier jour, pendant que l'accueil
    montrait des initiales.

    PortfolioViewModel.logos lit désormais la même donnée, dans l'appel de
    marché qui existait déjà pour les courbes, et les lignes d'actif s'en
    servent en priorité. Cette fonction reste le repli pour tout ce qui n'a
    pas d'identifiant de cotation — un jeton importé par contrat, par
    exemple. Elle ne devine plus que là où il n'y a rien d'autre.

    NE PAS Y REMETTRE D'ADRESSE EN DUR. Deux essais, deux échecs, et la
    source qui marche est ailleurs.
    ═══════════════════════════════════════════════════════════════════════
    */
    fun url(symbol: String): String {
        val ticker = symbol.substringBefore("-").trim().lowercase()
        return "https://raw.githubusercontent.com/spothq/cryptocurrency-icons/" +
            "master/128/color/$ticker.png"
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
