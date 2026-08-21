package com.vaultex.core.market

/**
 * Correspondance SYMBOLE → identifiant de cotation, en un seul endroit.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * POURQUOI CETTE TABLE EST CENTRALISÉE
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Elle existait en TROIS exemplaires : la fiche d'un jeton, l'écran
 * Alertes, et le worker de prix. Trois copies d'une même liste dérivent
 * toujours, et la façon dont elles dérivent ici est particulièrement
 * mauvaise : l'écran proposait une monnaie que le worker ne surveillait
 * pas. L'utilisateur créait son alerte, elle s'affichait comme active, et
 * elle ne se déclenchait jamais. Aucun message d'erreur — rien qui
 * permette de comprendre.
 *
 * Une seule table supprime cette classe de panne : ajouter une monnaie
 * ici l'ajoute partout à la fois.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * VÉRIFIER UN IDENTIFIANT AVANT DE L'AJOUTER
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Un identifiant inventé ne provoque pas d'erreur : il produit un PRIX,
 * celui d'une autre monnaie. C'est le bug qui a déjà touché cette
 * application — DAI, SHIB et WLFI affichaient tous le cours de
 * l'Ethereum. Une valeur fausse sur un portefeuille est bien pire qu'une
 * valeur absente, parce que rien ne signale qu'elle est fausse.
 *
 *   https://api.coingecko.com/api/v3/simple/price?ids=<id>&vs_currencies=usd
 *
 * Une réponse vide « {} » signifie que l'identifiant n'existe pas.
 *
 * Piège fréquent : ce sont des identifiants CoinGecko, PAS les tickers
 * ChangeNOW du registre d'échange. « shiba-inu » et non « shib »,
 * « chainlink » et non « link », « pancakeswap-token » et non « cake ».
 * Confondre les deux est exactement ce qui produit des cours erronés.
 */
object CoinIds {

    /**
     * Toutes les monnaies que l'application sait nommer, y compris les
     * variantes d'affichage de l'USDT (même jeton, trois chaînes, donc le
     * même cours).
     */
    val BY_SYMBOL: Map<String, String> = mapOf(
        "BTC" to "bitcoin",
        "ETH" to "ethereum",
        "BNB" to "binancecoin",
        "SOL" to "solana",
        "TRX" to "tron",
        "USDT" to "tether",
        "USDT-ETH" to "tether",
        "USDT-BNB" to "tether",
        "USDC" to "usd-coin",
        "DAI" to "dai",
        "LINK" to "chainlink",
        "SHIB" to "shiba-inu",
        "PEPE" to "pepe",
        "UNI" to "uniswap",
        "AAVE" to "aave",
        "WBTC" to "wrapped-bitcoin",
        "CAKE" to "pancakeswap-token"
    )

    /**
     * Monnaies pouvant porter une alerte de prix, dans l'ordre d'affichage.
     *
     * CE QUI EST DEDANS : les monnaies du registre intégré à l'application,
     * dont l'adresse de contrat est écrite en dur et vérifiée.
     *
     * CE QUI N'Y EST PAS, DÉLIBÉRÉMENT : les jetons que l'utilisateur
     * importe par adresse de contrat. Deux raisons, aucune n'étant une
     * limite technique :
     *
     * 1. LE COÛT. Le worker récupère toute cette liste en UN appel réseau —
     *    l'allonger ne coûte donc rien. Coter des contrats arbitraires
     *    exigerait en revanche deux appels de plus à chaque réveil, soit
     *    environ 2 100 par mois et par téléphone contre 720 aujourd'hui.
     *    Cinq téléphones épuiseraient à eux seuls le quota mensuel — la
     *    panne dont cette application vient précisément de sortir.
     *
     * 2. L'ABSENCE DE FILET. La source de prix de secours ne connaît pas
     *    les adresses de contrat. Coter d'après le symbole rouvrirait le
     *    bug d'attribution : n'importe qui peut déployer un contrat et
     *    l'appeler « SHIB ».
     *
     * Les variantes USDT-ETH / USDT-BNB sont exclues : trois entrées pour
     * un seul cours n'ajouteraient que de la confusion à l'écran.
     *
     * Les stablecoins ferment la marche : ils ne déclencheront jamais une
     * alerte de variation, mais restent utiles pour une alerte de cible —
     * surveiller un décrochage est un usage légitime.
     *
     * CONSÉQUENCE À GARDER EN TÊTE : les alertes de variation sont actives
     * par défaut et surveillent TOUTE cette liste. En passant de six à
     * quinze monnaies, on y a fait entrer SHIB et PEPE, qui franchissent le
     * seuil de 5 % presque tous les jours. Le délai de garde de douze heures
     * borne le bruit, mais l'utilisateur pour qui c'est trop doit savoir
     * qu'il peut relever le seuil à 10 ou 20 % depuis l'écran Alertes — ou
     * couper ces alertes entièrement.
     */
    val ALERTABLE: List<String> = listOf(
        "BTC", "ETH", "BNB", "SOL", "TRX",
        "LINK", "UNI", "AAVE", "CAKE", "SHIB", "PEPE", "WBTC",
        "USDT", "USDC", "DAI"
    )

    /**
     * [ALERTABLE] avec son identifiant de cotation.
     *
     * Dérivée de la table maîtresse, jamais recopiée : une monnaie ajoutée
     * à [ALERTABLE] sans identifiant ferait échouer la construction de
     * cette classe au démarrage, plutôt que de créer une alerte muette que
     * personne ne saurait diagnostiquer.
     */
    val ALERT_IDS: Map<String, String> =
        ALERTABLE.associateWith { symbole ->
            BY_SYMBOL[symbole]
                ?: error("CoinIds : identifiant de cotation manquant pour $symbole")
        }
}
