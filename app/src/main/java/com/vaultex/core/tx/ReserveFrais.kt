package com.vaultex.core.tx

/*
═══════════════════════════════════════════════════════════════════════════
CE QUE « MAX » LAISSE DE CÔTÉ POUR PAYER LE RÉSEAU
═══════════════════════════════════════════════════════════════════════════

Envoyer la totalité d'une monnaie native est impossible : il faut garder de
quoi payer le transport. Le bouton MAX retranche donc une réserve — et cette
règle existait EN DEUX EXEMPLAIRES, un par écran.

Ils avaient déjà divergé. L'écran d'envoi a appris à demander le frais réel
au réseau ; l'écran de swap est resté sur une table écrite en dur, dont la
ligne ETH valait 0,0003. Sur un solde de 0,0006031 ETH, MAX proposait donc la
MOITIÉ du solde, d'après un nombre figé dans le code — exactement le défaut
que la mesure en direct avait supprimé de l'autre côté.

Ce fichier est donc l'unique endroit où cette règle s'écrit. CoinIds raconte
la même histoire une page plus loin : trois copies d'une table, qui ont
divergé, et des alertes de prix qui ne se déclenchaient plus.

───────────────────────────────────────────────────────────────────────────
POURQUOI UNE MARGE, ET POURQUOI PAS LA MÊME PARTOUT
───────────────────────────────────────────────────────────────────────────

Le frais est estimé quand l'écran s'ouvre ; la transaction part plus tard —
le temps de saisir une adresse, de relire, de confirmer. Entre les deux, le
prix du gaz peut monter. La marge couvre cet écart.

Elle n'a donc de sens que là où le prix FLUCTUE, et seulement pour ce qui
n'est pas déjà provisionné :

· ETHEREUM — marge faible. Le plafond rendu par l'estimateur vaut déjà
  « 2 × frais de base + pourboire », la formule canonique EIP-1559, qui
  absorbe à elle seule un doublement du prix. Le multiplier encore par 1,6
  réservait plus du triple du coût réel. 25 % suffisent : le frais de base ne
  peut monter que de 12,5 % PAR BLOC, donc cette marge couvre deux blocs,
  quand l'écart entre estimation et diffusion se compte en secondes.

· BNB CHAIN, BITCOIN — marge pleine. Leur plafond ne contient aucun
  doublement : prix courant × limite de gaz pour l'un, tarif du mempool pour
  l'autre. Il n'y a rien à en retrancher, la marge y est le seul coussin.

· TRON — aucune marge. Le plafond contient déjà le pire cas, et ce pire cas
  est un montant FIXE : 1 TRX d'activation si le compte destinataire n'existe
  pas encore. Rien n'y fluctue, il n'y a donc rien à provisionner en plus.
  Avec une marge, un solde de 1,08 TRX se voyait réserver 1,6 TRX et aucun
  envoi n'était possible.

· SOLANA — réserve EXACTE, sans marge d'aucune sorte. Le frais y est fixe
  (5 000 lamports par signature), et surtout Solana REFUSE de laisser un
  compte entre 1 lamport et le minimum « rent-exempt ». MAX doit vider le
  compte à zéro pile : une marge y laisserait un résidu qui ferait rejeter la
  transaction.

───────────────────────────────────────────────────────────────────────────
LE REPLI N'EST PAS LA RÈGLE
───────────────────────────────────────────────────────────────────────────

Les valeurs écrites ci-dessous ne servent QUE tant que l'estimation n'est pas
revenue — écran tout juste ouvert, réseau coupé. Elles sont volontairement
petites : une réserve de repli trop grosse bloque MAX sur un petit solde,
alors que le vrai frais est souvent minime.

Un frais mesuré à ZÉRO est une réponse, pas une absence de réponse. Sur Tron,
c'est même le cas normal : la bande passante offerte couvre un transfert
simple. Seul `null` justifie le repli.
*/
object ReserveFrais {

    /**
     * Monnaie dans laquelle se paie le transport sur [chaine].
     *
     * Vaut la chaîne elle-même pour une monnaie native, et la monnaie de la
     * chaîne pour un jeton : de l'USDT-TRC20 se transporte en TRX, un ERC-20
     * en ETH. C'est la seule règle qui décide si MAX doit retrancher quelque
     * chose — un jeton ne retranche RIEN de son propre solde, puisque son gaz
     * sort d'ailleurs.
     */
    fun natifDe(chaine: String): String = when {
        chaine.startsWith("ERC20:") ->
            if (chaine.split(":").getOrNull(1) == "BNB") "BNB" else "ETH"
        chaine == "ETH" || chaine == "USDT-ETH" -> "ETH"
        chaine == "BNB" || chaine == "USDT-BNB" -> "BNB"
        chaine == "BTC" -> "BTC"
        chaine == "SOL" -> "SOL"
        chaine == "TRX" || chaine == "USDT" -> "TRX"
        else -> chaine
    }

    /** Repli employé tant que le frais réel n'est pas connu. */
    private val REPLI = mapOf(
        "BTC" to 0.00002,
        "ETH" to 0.0003,
        "BNB" to 0.00005,
        "SOL" to 0.00001,
        "TRX" to 1.1
    )

    private const val FRAIS_FIXE_SOLANA = 0.000005

    /**
     * Ce qu'il faut garder pour payer le dépôt, dans la monnaie [natif].
     *
     * [fraisLive] est le PLAFOND rendu par l'estimateur, ou null s'il n'est
     * pas encore connu. Zéro est une valeur valide : voir plus haut.
     */
    fun pour(natif: String, fraisLive: Double?): Double {
        val n = natif.uppercase()
        if (n == "SOL") return fraisLive?.takeIf { it > 0.0 } ?: FRAIS_FIXE_SOLANA
        if (fraisLive == null) return REPLI[n] ?: 0.0
        val marge = when (n) {
            "TRX" -> 1.0
            "ETH" -> 1.25
            else  -> 1.6
        }
        return fraisLive * marge
    }
}
