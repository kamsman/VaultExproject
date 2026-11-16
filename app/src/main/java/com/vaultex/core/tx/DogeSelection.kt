package com.vaultex.core.tx

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * QUELLES PIÈCES DÉPENSER, ET COMBIEN LAISSER AU RÉSEAU
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Quatrième brique : [com.vaultex.core.crypto.DogeWallet] dérive,
 * [DogeTransaction] sérialise, [DogeSigner] signe, et celle-ci décide ce
 * qu'on signe.
 *
 * ─── POURQUOI C'EST UN FICHIER À PART ──────────────────────────────────────
 *
 * Sur une chaîne UTXO, on ne « retire pas d'un solde » : on dépense des
 * pièces entières et on se rend la monnaie. Quelqu'un qui détient trois
 * pièces de 400 DOGE et veut en envoyer 500 doit en dépenser deux, et se
 * renvoyer 300 moins les frais.
 *
 * Tout ce qui peut mal tourner ici est silencieux et coûteux.
 *
 * ─── LE PIÈGE DE LA BOUCLE ─────────────────────────────────────────────────
 *
 * Les frais dépendent de la TAILLE, la taille dépend du NOMBRE D'ENTRÉES, et
 * le nombre d'entrées dépend des frais. Choisir les pièces pour couvrir
 * « montant + frais » avec des frais calculés sur une seule entrée donne un
 * compte juste tant qu'une entrée suffit, et faux dès qu'il en faut deux —
 * cas qui n'arrive jamais pendant les essais et toujours chez quelqu'un.
 *
 * On recalcule donc les frais à CHAQUE pièce ajoutée.
 *
 * ─── LE PIÈGE DU RENDU DE MONNAIE ──────────────────────────────────────────
 *
 * AUCUN CHAMP NE PORTE LES FRAIS. Ce sont les unités qui entrent moins
 * celles qui sortent. Une sortie de rendu oubliée n'est donc pas une erreur
 * détectable : elle offre tout le reste au mineur. Sur un envoi de 500 DOGE
 * depuis deux pièces de 400, l'oubli coûte 300 DOGE, en silence, et c'est
 * irréversible.
 *
 * ─── LE TARIF EST UN PARAMÈTRE, PAS UNE CONSTANTE ──────────────────────────
 *
 * MESURÉ le 16 novembre 2026 sur dix transactions d'un bloc : la moins chère
 * a payé 1 004 unités par octet — 0,0100 DOGE par kilo-octet, le minimum
 * historique — et elle était dans le bloc, donc acceptée. Les huit autres
 * payaient des forfaits ronds, jusqu'à UN DOGE pour 225 octets : du
 * surpaiement, pas une nécessité du réseau.
 *
 * Ce fichier ne fige pourtant aucun tarif. Il le reçoit, parce qu'un réseau
 * chargé pourrait demander davantage un autre jour, et qu'une constante
 * écrite ici serait relue par personne le jour où elle deviendrait fausse.
 * `tools/doge-frais.sh` est là pour la remesurer.
 * ═══════════════════════════════════════════════════════════════════════════
 */
object DogeSelection {

    /*
    ═══════════════════════════════════════════════════════════════════════
    LA TAILLE EST ESTIMÉE AVANT D'ÊTRE CONNUE, DONC ELLE EST MAJORÉE
    ═══════════════════════════════════════════════════════════════════════

    On doit payer des frais pour une transaction qui n'existe pas encore :
    ses signatures ne seront produites qu'après le choix des pièces.

    UNE SIGNATURE DER CANONIQUE FAIT AU PLUS 71 OCTETS, et souvent 70 — la
    transaction réelle qui sert de référence en portait une de 70, et
    l'estimateur retombe alors sur ses 225 octets exactement. On compte
    quand même 72.

    L'ÉCART VA TOUJOURS DANS LE BON SENS. Majorer fait payer un ou deux
    octets de trop, soit quelques millièmes de centime. Minorer produit
    une transaction sous-payée que le réseau garde en attente sans rien
    dire — et c'est l'utilisateur qui attend, sans savoir pourquoi.
    ═══════════════════════════════════════════════════════════════════════
    */
    const val OCTETS_SIGNATURE_MAX = 72
    const val OCTETS_CLE_COMPRESSEE = 33

    /** 8 pour la valeur, 1 pour la longueur, 25 pour le script P2PKH. */
    const val OCTETS_SORTIE = 34

    /** Une sortie non dépensée, telle que le fournisseur la rend. */
    data class Piece(
        val txidAffiche: String,
        val index: Int,
        val unites: Long,
        val confirmations: Int = 0,
    )

    /** Ce qu'il faut construire. */
    data class Choix(
        val pieces: List<Piece>,
        val versDestinataire: Long,
        /** Zéro quand il n'y a pas de sortie de rendu. */
        val rendu: Long,
        val frais: Long,
        val octetsEstimes: Int,
    ) {
        /** Toujours vrai par construction — vérifié par les tests. */
        val equilibre: Boolean
            get() = pieces.sumOf { it.unites } == versDestinataire + rendu + frais
    }

    /**
     * Les refus sont NOMMÉS, parce qu'ils ne demandent pas la même chose à
     * l'utilisateur : réapprovisionner, baisser le montant, ou renoncer.
     */
    sealed class Resultat {
        data class Ok(val choix: Choix) : Resultat()

        /** Même sans frais, les pièces ne couvrent pas le montant. */
        data class SoldeInsuffisant(val disponible: Long, val demande: Long) : Resultat()

        /**
         * Le montant passe, les frais non. Distinct du précédent : ici
         * l'écran peut proposer « envoyer le maximum » au lieu de renvoyer
         * quelqu'un approvisionner un portefeuille qui a presque assez.
         */
        data class FraisNonCouverts(
            val disponible: Long,
            val necessaire: Long,
            val frais: Long,
        ) : Resultat()

        /** Un envoi plus petit que le seuil de poussière ne serait pas relayé. */
        data class MontantTropPetit(val montant: Long, val seuil: Long) : Resultat()
    }

    /**
     * Taille d'une transaction à [nEntrees] entrées et [nSorties] sorties,
     * toutes en P2PKH. Majorée : voir le pavé sur les 72 octets.
     */
    fun tailleEstimee(nEntrees: Int, nSorties: Int): Int {
        require(nEntrees >= 0 && nSorties >= 0) { "comptes négatifs" }
        // script de déblocage : longueur + (signature + type) + longueur + clé
        val deblocage = 1 + (OCTETS_SIGNATURE_MAX + 1) + 1 + OCTETS_CLE_COMPRESSEE
        val entree = 32 + 4 + DogeTransaction.varint(deblocage.toLong()).size + deblocage + 4
        return 4 +
            DogeTransaction.varint(nEntrees.toLong()).size + nEntrees * entree +
            DogeTransaction.varint(nSorties.toLong()).size + nSorties * OCTETS_SORTIE +
            4
    }

    /**
     * Choisit les pièces à dépenser pour envoyer [montant] unités.
     *
     * [seuilPoussiere] est le plus petit montant qu'une sortie puisse porter
     * sans être rejetée. CE SEUIL N'EST PAS VÉRIFIÉ : il est passé par
     * l'appelant, et seul un envoi réel dira s'il est bon. Le choisir trop
     * bas produit une transaction refusée ; trop haut, un rendu de monnaie
     * abandonné aux frais.
     */
    fun choisir(
        pieces: List<Piece>,
        montant: Long,
        unitesParOctet: Long,
        seuilPoussiere: Long,
        confirmationsMinimum: Int = 1,
    ): Resultat {
        require(montant > 0) { "montant nul ou négatif" }
        require(unitesParOctet > 0) { "tarif nul ou négatif" }

        if (montant < seuilPoussiere) return Resultat.MontantTropPetit(montant, seuilPoussiere)

        /*
        LES PIÈCES NON CONFIRMÉES SONT ÉCARTÉES PAR DÉFAUT. Dépenser une
        sortie qui n'est pas encore dans un bloc produit une transaction qui
        dépend d'une autre : si la première est remplacée, la nôtre devient
        invalide, et personne ne sait l'expliquer à qui la subit.
        */
        val utilisables = pieces
            .filter { it.unites > 0 && it.confirmations >= confirmationsMinimum }
            /*
            LA PLUS GROSSE D'ABORD. Ce n'est pas la stratégie la plus
            raffinée — elle regroupe les pièces, ce qui en dit un peu plus
            sur le portefeuille qu'une sélection dispersée. C'est en
            revanche celle qui emploie le MOINS d'entrées, donc la moins
            chère, et sur une chaîne où les frais se comptent à l'octet
            c'est ce que l'utilisateur constate.

            Départage par identifiant puis index : à montants égaux, le
            choix doit être le même d'un appel à l'autre, sinon deux écrans
            affichent deux frais différents pour le même envoi.
            */
            .sortedWith(
                compareByDescending<Piece> { it.unites }
                    .thenBy { it.txidAffiche }
                    .thenBy { it.index }
            )

        val total = utilisables.sumOf { it.unites }
        if (total < montant) return Resultat.SoldeInsuffisant(total, montant)

        val retenues = mutableListOf<Piece>()
        var cumul = 0L

        for (piece in utilisables) {
            retenues += piece
            cumul += piece.unites

            // Deux sorties : le destinataire, et le rendu de monnaie.
            val octetsAvecRendu = tailleEstimee(retenues.size, 2)
            val fraisAvecRendu = octetsAvecRendu * unitesParOctet
            val reste = cumul - montant - fraisAvecRendu

            if (reste >= seuilPoussiere) {
                return Resultat.Ok(
                    Choix(retenues.toList(), montant, reste, fraisAvecRendu, octetsAvecRendu)
                )
            }

            /*
            ═══════════════════════════════════════════════════════════════
            UN RENDU TROP PETIT PART AUX FRAIS, IL NE DISPARAÎT PAS
            ═══════════════════════════════════════════════════════════════

            Créer une sortie de poussière produirait une transaction que le
            réseau refuse — et une pièce que personne ne pourra jamais
            dépenser, puisqu'elle coûterait plus cher à dépenser qu'elle ne
            vaut.

            On supprime donc la sortie de rendu, ce qui RÉDUIT la taille de
            34 octets, et tout l'excédent va au mineur. L'utilisateur paie
            un peu plus que le tarif, et c'est la seule issue honnête : la
            seule autre serait de lui rendre une pièce inutilisable.

            CE CAS EST RARE ET IL ARRIVE QUAND MÊME : il se produit chaque
            fois que le solde est presque exactement le montant envoyé,
            c'est-à-dire quand on vide un portefeuille.
            ═══════════════════════════════════════════════════════════════
            */
            val octetsSansRendu = tailleEstimee(retenues.size, 1)
            val fraisMinimum = octetsSansRendu * unitesParOctet
            if (cumul - montant >= fraisMinimum) {
                return Resultat.Ok(
                    Choix(
                        retenues.toList(),
                        montant,
                        rendu = 0L,
                        frais = cumul - montant,
                        octetsEstimes = octetsSansRendu
                    )
                )
            }
        }

        /*
        TOUTES LES PIÈCES SONT PRISES ET ÇA NE SUFFIT PAS. Le montant
        passait — on l'a vérifié plus haut — donc ce sont les frais qui
        manquent, et c'est un refus différent.
        */
        val octets = tailleEstimee(utilisables.size, 1)
        return Resultat.FraisNonCouverts(
            disponible = total,
            necessaire = montant + octets * unitesParOctet,
            frais = octets * unitesParOctet
        )
    }
}
