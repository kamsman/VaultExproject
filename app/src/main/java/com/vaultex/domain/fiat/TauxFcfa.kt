package com.vaultex.domain.fiat

/*
═══════════════════════════════════════════════════════════════════════════
ACHAT ET VENTE EN FRANCS — LE CALCUL, ET RIEN D'AUTRE
═══════════════════════════════════════════════════════════════════════════

Ce fichier ne parle à personne, ne signe rien, ne garde rien. Il transforme
un cours et une marge en deux prix : celui auquel l'utilisateur achète, et
celui auquel il vend.

C'est délibérément séparé du reste. Le prix affiché avant un bouton
« Confirmer » est ce sur quoi quelqu'un s'engage ; il doit pouvoir se
relire et se tester sans réseau, sans écran et sans changeur.

─── LE TAUX DOLLAR → FRANC N'EST PAS ÉCRIT EN DUR ───────────────────────

Il se DÉDUIT des cours que l'application connaît déjà. Chaque monnaie est
cotée en dollars ET en francs : leur rapport donne le taux du jour, sans
appel supplémentaire et sans constante à maintenir.

Écrire « 600 FCFA le dollar » quelque part serait un chiffre juste le jour
où on l'écrit, et faux ensuite — le franc est arrimé à l'euro, pas au
dollar, et l'euro bouge contre le dollar tous les jours. Un taux périmé sur
un écran d'achat, c'est une marge qu'on croit prendre et qu'on ne prend
pas, ou pire : qu'on prend en trop.

─── LA MARGE SE COMPTE EN FRANCS PAR DOLLAR ─────────────────────────────

Parce que c'est ainsi que le marché local en parle. FasoChange prend
50 FCFA par dollar ; un utilisateur de Ouagadougou comprend ce chiffre
immédiatement, là où « 8,3 % » demande un calcul.

Les deux sont donnés — [margeEnPourcent] traduit — parce qu'un pourcentage
reste la seule façon de comparer à un échangeur en ligne.

─── CE QUE CE FICHIER NE DÉCIDE PAS ─────────────────────────────────────

Qui envoie en premier, ce qui vaut preuve, et si l'opération est permise.
Rien de tout cela n'est un calcul.
═══════════════════════════════════════════════════════════════════════════
*/

/**
 * Les deux prix d'une monnaie, en francs, marge comprise.
 *
 * @param baseFcfa cours du marché, sans marge — ce qu'on paierait ailleurs.
 * @param achatFcfa ce que l'utilisateur PAIE pour en acheter une unité.
 * @param venteFcfa ce qu'il REÇOIT pour en vendre une unité.
 */
data class PrixFcfa(
    val baseFcfa: Double,
    val achatFcfa: Double,
    val venteFcfa: Double
)

object TauxFcfa {

    /**
     * Combien de francs vaut un dollar, déduit d'une monnaie cotée dans les
     * deux devises.
     *
     * N'importe laquelle convient — le rapport est le même pour toutes. On
     * préfère une monnaie stable en pratique : son cours en dollars vaut un,
     * donc son cours en francs EST le taux, et un écart viendrait de la
     * source, pas de la volatilité.
     *
     * Rend null plutôt qu'un chiffre bancal : sans taux, un écran d'achat ne
     * doit RIEN afficher. Mieux vaut « indisponible » qu'un montant inventé
     * sur lequel quelqu'un s'engagerait.
     */
    fun fcfaParDollar(prixXof: Double, prixUsd: Double): Double? {
        if (prixUsd <= 0.0 || prixXof <= 0.0) return null
        val taux = prixXof / prixUsd
        // Garde de vraisemblance. Le franc est arrimé à l'euro ; le dollar a
        // oscillé entre 500 et 700 francs sur les vingt dernières années. Une
        // valeur hors de ces bornes signale une source corrompue ou mal lue,
        // pas un mouvement de marché.
        return if (taux in 300.0..1200.0) taux else null
    }

    /**
     * Les deux prix d'une unité de monnaie, à partir de son cours en francs.
     *
     * @param coursFcfa cours de marché d'UNE unité, en francs.
     * @param margeFcfaParDollar marge appliquée, en francs par dollar.
     * @param fcfaParDollar taux du jour, pour convertir la marge.
     *
     * LA MARGE S'APPLIQUE DANS LES DEUX SENS, et en sens opposé : qui achète
     * paie plus cher, qui vend reçoit moins. C'est la même marge, pas deux
     * réglages — un seul nombre à régler, aucune façon de se tromper de
     * signe.
     */
    fun prix(
        coursFcfa: Double,
        margeFcfaParDollar: Double,
        fcfaParDollar: Double
    ): PrixFcfa? {
        if (coursFcfa <= 0.0 || fcfaParDollar <= 0.0) return null
        if (margeFcfaParDollar < 0.0) return null

        // La marge est donnée par DOLLAR ; on la ramène à la monnaie traitée.
        // Un bitcoin ne se vend pas avec la marge d'un dollar : c'est une
        // proportion, exprimée dans l'unité que le marché local emploie.
        val proportion = margeFcfaParDollar / fcfaParDollar
        /*
        PLAFOND DE MARGE. Au-delà, ce n'est plus une marge, c'est une erreur
        de saisie — un 2500 tapé pour 25. Une valeur venue d'un réglage
        distant ne doit pas pouvoir faire payer le double du marché à
        quelqu'un qui a lu un écran et cliqué.
        */
        if (proportion > MARGE_MAX_PROPORTION) return null

        /*
        ═══════════════════════════════════════════════════════════════════
        LE PRIX AFFICHÉ DOIT ÊTRE CELUI QU'ON APPLIQUE
        ═══════════════════════════════════════════════════════════════════

        Sans cet arrondi, l'écran annonçait « Prix d'un USDT : 560 FCFA »
        puis « Tu reçois : 5 599 FCFA » pour dix unités. Les deux chiffres
        étaient justes — 559,93 arrondi à 560, et dix fois 559,93 font
        5 599 — et leur rapprochement était faux.

        Quelqu'un qui vérifie de tête fait 560 × 10 = 5 600, trouve 5 599,
        et ne conclut pas « arrondi » : il conclut qu'on lui prend un franc
        quelque part. Sur un écran de change, ce soupçon coûte plus cher
        que le franc.

        On arrondit donc le prix unitaire AU FRANC, et tout se calcule à
        partir de ce prix-là. Le total redevient vérifiable à la main, ce
        qui est exactement ce qu'un utilisateur fait quand il hésite.

        Le franc n'a pas de subdivision en circulation : arrondir à l'unité
        n'est pas une approximation, c'est la précision réelle de la
        monnaie.
        ═══════════════════════════════════════════════════════════════════
        */
        return PrixFcfa(
            baseFcfa = coursFcfa,
            achatFcfa = kotlin.math.round(coursFcfa * (1.0 + proportion)),
            venteFcfa = kotlin.math.round(coursFcfa * (1.0 - proportion))
        )
    }

    /**
     * La marge, en pourcentage — la seule unité comparable à un échangeur.
     *
     * 50 FCFA par dollar parle à Ouagadougou ; 8,3 % se compare à ChangeNOW.
     * Les deux sont vrais, et l'écran gagne à montrer les deux.
     */
    fun margeEnPourcent(margeFcfaParDollar: Double, fcfaParDollar: Double): Double? {
        if (fcfaParDollar <= 0.0 || margeFcfaParDollar < 0.0) return null
        return margeFcfaParDollar / fcfaParDollar * 100.0
    }

    /**
     * Montant de crypto obtenu pour [fcfa] francs, au prix d'achat.
     *
     * Rend null sur un prix nul : diviser par zéro rendrait l'infini, qui
     * s'afficherait comme un montant.
     */
    fun cryptoPourFcfa(fcfa: Double, prix: PrixFcfa): Double? {
        if (fcfa <= 0.0 || prix.achatFcfa <= 0.0) return null
        return fcfa / prix.achatFcfa
    }

    /** Francs obtenus pour [crypto] unités, au prix de vente. */
    fun fcfaPourCrypto(crypto: Double, prix: PrixFcfa): Double? {
        if (crypto <= 0.0 || prix.venteFcfa <= 0.0) return null
        return crypto * prix.venteFcfa
    }

    /**
     * Vingt pour cent. Très au-dessus de ce que pratique le marché local —
     * FasoChange est à 8,3 % — et très en dessous d'une faute de frappe.
     */
    const val MARGE_MAX_PROPORTION = 0.20

    /**
     * Combien de décimales afficher pour cette monnaie.
     *
     * ═══════════════════════════════════════════════════════════════════
     * NE PAS PROMETTRE UNE PRÉCISION QUI N'EXISTE PAS
     * ═══════════════════════════════════════════════════════════════════
     *
     * L'écran affichait « 8,19672131 USDT » pour un achat de 5 000 francs.
     * Le calcul est juste ; le nombre, non.
     *
     * L'USDT a SIX décimales sur TRON comme sur Ethereum. Les deux
     * derniers chiffres n'existent pas : ils ne peuvent pas être envoyés,
     * et le changeur enverra 8,196721. On annonçait donc un montant que
     * personne ne peut recevoir.
     *
     * Ça n'a jamais fait perdre un franc — l'écart est d'un millionième —
     * mais c'est exactement le genre de petite fausseté qui use la
     * confiance : quelqu'un qui compare le montant promis à celui reçu
     * trouve une différence, et il a raison.
     *
     * Et c'est illisible. « 8,196721 » se lit déjà mal ; dix chiffres
     * après la virgule sur la ligne la plus importante de l'écran ne
     * servent personne.
     *
     * SIX PAR DÉFAUT, ce qui couvre l'USDT, le TRX et tout ce qui se
     * change en francs ici. Le bitcoin garde ses huit : à 50 000 francs
     * l'opération, on y parle de dix-millièmes.
     * ═══════════════════════════════════════════════════════════════════
     */
    fun decimalesAffichage(monnaie: String): Int = when (monnaie.uppercase()) {
        "BTC" -> 8
        // Pi en a sept, comme Stellar dont il est issu.
        "PI" -> 7
        else -> 6
    }
}
