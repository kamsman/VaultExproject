package com.vaultex.core.tx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ═══════════════════════════════════════════════════════════════════════
 * LE CHOIX DES PIÈCES, ET L'ÉQUILIBRE QUI NE DOIT JAMAIS SE ROMPRE
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Aucun champ d'une transaction ne porte les frais : ce sont les unités
 * qui entrent moins celles qui sortent. Un rendu de monnaie oublié n'est
 * donc pas une erreur détectable — il offre le reste au mineur, en
 * silence et sans retour.
 *
 * CHAQUE CAS VÉRIFIE DONC L'ÉQUILIBRE : la somme des pièces dépensées
 * doit valoir exactement destinataire + rendu + frais. C'est l'assertion
 * qui compte ; les autres décrivent le comportement, celle-là protège
 * l'argent.
 *
 * ─── LE TARIF EMPLOYÉ ICI ──────────────────────────────────────────────
 *
 * 1 004 unités par octet : le plus bas tarif MESURÉ dans un bloc
 * confirmé, le 16 novembre 2026, par `tools/doge-frais.sh`. Il vaut
 * 0,0100 DOGE par kilo-octet — le minimum historique — et la transaction
 * qui l'a payé était dans le bloc, donc acceptée.
 *
 * ─── LA TAILLE EST CALIBRÉE SUR UNE TRANSACTION RÉELLE ─────────────────
 *
 * L'estimateur majore d'un ou deux octets, et c'est voulu : majorer fait
 * payer quelques millièmes de centime de trop, minorer produit une
 * transaction que le réseau garde en attente sans rien dire.
 * ═══════════════════════════════════════════════════════════════════════
 */
class DogeSelectionTest {

    private companion object {
        const val DOGE = 100_000_000L

        /** Le plus bas tarif mesuré dans un bloc confirmé. */
        const val TAUX = 1_004L

        /**
         * 0,01 DOGE. CE SEUIL N'EST PAS VÉRIFIÉ — il est ici pour que les
         * tests aient une valeur, et seul un envoi réel dira s'il est bon.
         */
        const val POUSSIERE = DOGE / 100

        const val A = "aa"
        const val B = "bb"
        const val C = "cc"
    }

    private fun piece(prefixe: String, index: Int, unites: Long, confirmations: Int = 6) =
        DogeSelection.Piece(prefixe.repeat(32), index, unites, confirmations)

    private fun ok(r: DogeSelection.Resultat): DogeSelection.Choix {
        assertTrue("attendu un succès, reçu $r", r is DogeSelection.Resultat.Ok)
        val choix = (r as DogeSelection.Resultat.Ok).choix
        assertTrue(
            "L'EQUILIBRE EST ROMPU : ${choix.pieces.sumOf { it.unites }} entrent, " +
                "${choix.versDestinataire} + ${choix.rendu} + ${choix.frais} sortent",
            choix.equilibre
        )
        return choix
    }

    /*
    LA TAILLE CALIBREE SUR LA TRANSACTION REELLE DU BLOC 6460312 : une
    entree, deux sorties, 225 octets portant une signature DER de 70. En
    comptant 72 octets de signature, l'estimateur rend 227 — deux de
    trop, dans le bon sens.
    */
    @Test
    fun `la taille estimee majore la transaction reelle de deux octets`() {
        assertEquals(227, DogeSelection.tailleEstimee(1, 2))
        assertTrue(
            "l'estimation doit MAJORER les 225 octets reels, jamais les minorer",
            DogeSelection.tailleEstimee(1, 2) >= 225
        )
    }

    @Test
    fun `la taille suit le nombre d entrees et de sorties`() {
        assertEquals(193, DogeSelection.tailleEstimee(1, 1))
        assertEquals(227, DogeSelection.tailleEstimee(1, 2))
        assertEquals(342, DogeSelection.tailleEstimee(2, 1))
        assertEquals(376, DogeSelection.tailleEstimee(2, 2))
        assertEquals(525, DogeSelection.tailleEstimee(3, 2))
    }

    @Test
    fun `une seule piece suffisante donne une seule entree`() {
        val choix = ok(
            DogeSelection.choisir(listOf(piece(A, 0, 10 * DOGE)), 3 * DOGE, TAUX, POUSSIERE)
        )
        assertEquals(1, choix.pieces.size)
        assertEquals(227, choix.octetsEstimes)
        assertEquals(227_908L, choix.frais)
        assertEquals(3 * DOGE, choix.versDestinataire)
        assertEquals(699_772_092L, choix.rendu)
    }

    /*
    ═══════════════════════════════════════════════════════════════════
    LA BOUCLE DES FRAIS, ET C'EST POUR ELLE QUE CE CAS EXISTE
    ═══════════════════════════════════════════════════════════════════

    Deux pieces de 2 DOGE pour en envoyer 3. Les frais calcules sur UNE
    entree donneraient un compte juste... pour une transaction qui en
    aura deux. L'ecart est de 149 octets, soit 149 596 unites.

    Ce cas n'arrive jamais pendant les essais a une piece, et toujours
    chez quelqu'un dont le portefeuille s'est rempli en plusieurs fois.
    ═══════════════════════════════════════════════════════════════════
    */
    @Test
    fun `les frais sont recalcules quand il faut une seconde piece`() {
        val choix = ok(
            DogeSelection.choisir(
                listOf(piece(A, 0, 2 * DOGE), piece(B, 1, 2 * DOGE)),
                3 * DOGE, TAUX, POUSSIERE
            )
        )
        assertEquals(2, choix.pieces.size)
        assertEquals(376, choix.octetsEstimes)
        assertEquals(377_504L, choix.frais)
        assertEquals(99_622_496L, choix.rendu)
    }

    @Test
    fun `la plus grosse piece est prise en premier`() {
        val choix = ok(
            DogeSelection.choisir(
                listOf(piece(A, 0, 1 * DOGE), piece(B, 1, 9 * DOGE)),
                3 * DOGE, TAUX, POUSSIERE
            )
        )
        assertEquals(1, choix.pieces.size)
        assertEquals(9 * DOGE, choix.pieces[0].unites)
    }

    /*
    L'ORDRE DOIT ETRE LE MEME D'UN APPEL A L'AUTRE. Sans departage a
    montants egaux, deux ecrans afficheraient deux frais differents pour
    le meme envoi — et l'un des deux aurait l'air d'un defaut.
    */
    @Test
    fun `a montants egaux le choix est toujours le meme`() {
        val pieces = listOf(piece(C, 0, 5 * DOGE), piece(A, 1, 5 * DOGE), piece(B, 2, 5 * DOGE))
        val premier = ok(DogeSelection.choisir(pieces, 3 * DOGE, TAUX, POUSSIERE))
        val second = ok(DogeSelection.choisir(pieces.reversed(), 3 * DOGE, TAUX, POUSSIERE))
        assertEquals(premier.pieces.map { it.txidAffiche }, second.pieces.map { it.txidAffiche })
        assertEquals(A.repeat(32), premier.pieces[0].txidAffiche)
    }

    /*
    DEPENSER UNE PIECE NON CONFIRMEE produit une transaction qui depend
    d'une autre : si la premiere est remplacee, la notre devient invalide
    et personne ne sait l'expliquer a qui la subit.
    */
    @Test
    fun `une piece non confirmee est ecartee`() {
        val choix = ok(
            DogeSelection.choisir(
                listOf(
                    piece(A, 0, 10 * DOGE, confirmations = 0),
                    piece(B, 1, 5 * DOGE, confirmations = 3)
                ),
                3 * DOGE, TAUX, POUSSIERE
            )
        )
        assertEquals(1, choix.pieces.size)
        assertEquals(B.repeat(32), choix.pieces[0].txidAffiche)
    }

    /*
    ═══════════════════════════════════════════════════════════════════
    UN RENDU TROP PETIT PART AUX FRAIS
    ═══════════════════════════════════════════════════════════════════

    Le creer produirait une piece que personne ne pourra jamais depenser,
    puisqu'elle couterait plus cher a depenser qu'elle ne vaut — et une
    transaction que le reseau refuse.

    La sortie de rendu disparait donc, ce qui RAMENE la taille de 227 a
    193 octets, et tout l'excedent va au mineur. L'utilisateur paie un
    peu plus que le tarif ; la seule autre issue serait de lui rendre une
    piece inutilisable.
    */
    @Test
    fun `un rendu sous le seuil de poussiere part aux frais`() {
        val choix = ok(
            DogeSelection.choisir(
                listOf(piece(A, 0, 3 * DOGE + 300_000L)), 3 * DOGE, TAUX, POUSSIERE
            )
        )
        assertEquals(0L, choix.rendu)
        assertEquals(193, choix.octetsEstimes)
        assertEquals(300_000L, choix.frais)
        assertTrue(
            "les frais doivent couvrir au moins le tarif de la transaction sans rendu",
            choix.frais >= 193 * TAUX
        )
    }

    @Test
    fun `un solde trop faible est nomme comme tel`() {
        val r = DogeSelection.choisir(listOf(piece(A, 0, 1 * DOGE)), 3 * DOGE, TAUX, POUSSIERE)
        assertTrue(r is DogeSelection.Resultat.SoldeInsuffisant)
        val d = r as DogeSelection.Resultat.SoldeInsuffisant
        assertEquals(1 * DOGE, d.disponible)
        assertEquals(3 * DOGE, d.demande)
    }

    /*
    DEUX REFUS DIFFERENTS, ET LA DIFFERENCE EST CE QU'ON PROPOSE ENSUITE.
    Ici le montant passe et les frais ne passent pas : l'ecran peut
    proposer « envoyer le maximum » plutot que de renvoyer quelqu'un
    approvisionner un portefeuille qui a presque assez.
    */
    @Test
    fun `des frais non couverts ne sont pas un solde insuffisant`() {
        val r = DogeSelection.choisir(
            listOf(piece(A, 0, 3 * DOGE + 1_000L)), 3 * DOGE, TAUX, POUSSIERE
        )
        assertTrue("attendu FraisNonCouverts, recu $r", r is DogeSelection.Resultat.FraisNonCouverts)
        val d = r as DogeSelection.Resultat.FraisNonCouverts
        assertEquals(3 * DOGE + 1_000L, d.disponible)
        assertEquals(193 * TAUX, d.frais)
        assertTrue("le necessaire doit depasser le disponible", d.necessaire > d.disponible)
    }

    @Test
    fun `un envoi plus petit que la poussiere est refuse`() {
        val r = DogeSelection.choisir(listOf(piece(A, 0, 10 * DOGE)), 1_000L, TAUX, POUSSIERE)
        assertTrue(r is DogeSelection.Resultat.MontantTropPetit)
    }

    /*
    LE CAS QUI VIDE LE PORTEFEUILLE. Il merite son test parce qu'il
    combine les deux pieges : plusieurs entrees ET un rendu nul.
    */
    @Test
    fun `vider un portefeuille de trois pieces garde l equilibre`() {
        val pieces = listOf(piece(A, 0, 1 * DOGE), piece(B, 1, 1 * DOGE), piece(C, 2, 1 * DOGE))
        val frais = DogeSelection.tailleEstimee(3, 1) * TAUX
        val choix = ok(
            DogeSelection.choisir(pieces, 3 * DOGE - frais, TAUX, POUSSIERE)
        )
        assertEquals(3, choix.pieces.size)
        assertEquals(0L, choix.rendu)
        assertEquals(3 * DOGE, choix.versDestinataire + choix.frais)
    }
}
