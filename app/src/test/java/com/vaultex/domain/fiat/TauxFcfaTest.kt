package com.vaultex.domain.fiat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ═══════════════════════════════════════════════════════════════════════
 * LE PRIX AFFICHÉ AVANT « CONFIRMER »
 * ═══════════════════════════════════════════════════════════════════════
 *
 * C'est le chiffre sur lequel quelqu'un s'engage, et il n'y a personne
 * derrière pour le rattraper : une fois l'argent parti sur Orange Money,
 * il est parti.
 *
 * Aucune erreur de ce fichier ne lève d'exception. Elles produisent toutes
 * un prix — simplement pas le bon. C'est exactement le genre de défaut que
 * seuls des exemples chiffrés attrapent.
 * ═══════════════════════════════════════════════════════════════════════
 */
class TauxFcfaTest {

    private companion object {
        /** Ordre de grandeur réel : le dollar vaut environ 600 francs. */
        const val TAUX = 600.0

        /** Ce que prend FasoChange, le concurrent local. */
        const val MARGE_FASOCHANGE = 50.0
    }

    /*
    ───────────────────────────────────────────────────────────────────────
    LE TAUX SE DÉDUIT, IL NE S'ÉCRIT PAS
    ───────────────────────────────────────────────────────────────────────
    */

    @Test fun `le taux se deduit d'une monnaie cotee dans les deux devises`() {
        // Une stable : un dollar l'unité, donc son cours en francs EST le taux.
        assertEquals(600.0, TauxFcfa.fcfaParDollar(prixXof = 600.0, prixUsd = 1.0)!!, 0.001)
        // N'importe quelle autre donne le même rapport.
        assertEquals(600.0, TauxFcfa.fcfaParDollar(prixXof = 36_000_000.0, prixUsd = 60_000.0)!!, 0.001)
    }

    /**
     * UN TAUX ABERRANT NE DOIT PAS SORTIR.
     *
     * Il viendrait d'une source corrompue ou d'un champ mal lu, jamais d'un
     * mouvement de marché : le franc est arrimé à l'euro, et le dollar a
     * oscillé entre 500 et 700 francs en vingt ans.
     *
     * Le laisser passer ferait afficher un prix d'achat délirant sur un
     * écran où quelqu'un valide.
     */
    @Test fun `un taux hors des bornes est refuse`() {
        assertNull(TauxFcfa.fcfaParDollar(prixXof = 6.0, prixUsd = 1.0))        // 6 F le dollar
        assertNull(TauxFcfa.fcfaParDollar(prixXof = 60_000.0, prixUsd = 1.0))   // 60 000 F
        assertNull(TauxFcfa.fcfaParDollar(prixXof = 0.0, prixUsd = 1.0))
        assertNull(TauxFcfa.fcfaParDollar(prixXof = 600.0, prixUsd = 0.0))
        assertNull(TauxFcfa.fcfaParDollar(prixXof = -600.0, prixUsd = 1.0))
    }

    /*
    ───────────────────────────────────────────────────────────────────────
    LA MARGE, DANS LES DEUX SENS
    ───────────────────────────────────────────────────────────────────────
    */

    @Test fun `qui achete paie plus, qui vend recoit moins`() {
        // Une stable à 600 F, marge de 25 F par dollar : 600 ± 25.
        val p = TauxFcfa.prix(coursFcfa = 600.0, margeFcfaParDollar = 25.0, fcfaParDollar = TAUX)!!
        assertEquals(600.0, p.baseFcfa, 0.001)
        assertEquals(625.0, p.achatFcfa, 0.001)
        assertEquals(575.0, p.venteFcfa, 0.001)
    }

    /**
     * LA MARGE EST UNE PROPORTION, PAS UN MONTANT FIXE.
     *
     * Elle se DIT en francs par dollar parce que c'est la langue du marché
     * local. Mais l'appliquer telle quelle à un bitcoin retirerait 25 francs
     * sur trente-six millions — une marge de 0,00007 %.
     */
    @Test fun `la marge suit la valeur de la monnaie traitee`() {
        val btc = TauxFcfa.prix(
            coursFcfa = 36_000_000.0, margeFcfaParDollar = 25.0, fcfaParDollar = TAUX
        )!!
        // 25/600 = 4,1667 % de 36 000 000 = 1 500 000.
        assertEquals(37_500_000.0, btc.achatFcfa, 1.0)
        assertEquals(34_500_000.0, btc.venteFcfa, 1.0)
    }

    @Test fun `une marge nulle laisse le cours intact`() {
        val p = TauxFcfa.prix(1000.0, 0.0, TAUX)!!
        assertEquals(1000.0, p.achatFcfa, 0.001)
        assertEquals(1000.0, p.venteFcfa, 0.001)
    }

    /**
     * UNE MARGE ABSURDE EST REFUSÉE, PAS APPLIQUÉE.
     *
     * Elle viendra d'un réglage distant — c'est tout l'intérêt d'un
     * pourcentage dynamique — donc d'un champ qu'on tape à la main. Un 2500
     * pour 25 ferait payer cinq fois le marché à quelqu'un qui a lu l'écran
     * et cliqué.
     */
    @Test fun `une marge delirante est refusee`() {
        // 20 % du taux, soit 120 F par dollar : la limite.
        assertTrue(TauxFcfa.prix(600.0, 120.0, TAUX) != null)
        assertNull(TauxFcfa.prix(600.0, 121.0, TAUX))
        assertNull(TauxFcfa.prix(600.0, 2500.0, TAUX))
        assertNull(TauxFcfa.prix(600.0, -25.0, TAUX))
    }

    @Test fun `un cours ou un taux nul ne donne aucun prix`() {
        assertNull(TauxFcfa.prix(0.0, 25.0, TAUX))
        assertNull(TauxFcfa.prix(600.0, 25.0, 0.0))
    }

    /*
    ───────────────────────────────────────────────────────────────────────
    LA TRADUCTION EN POURCENTAGE
    ───────────────────────────────────────────────────────────────────────

    « 50 FCFA par dollar » parle à Ouagadougou ; « 8,3 % » se compare à un
    échangeur en ligne. L'écran gagne à montrer les deux, et c'est ce
    chiffre-là qui dit à l'utilisateur ce qu'il paie vraiment.
    */

    @Test fun `la marge de FasoChange vaut environ huit pour cent`() {
        val pct = TauxFcfa.margeEnPourcent(MARGE_FASOCHANGE, TAUX)!!
        assertEquals(8.33, pct, 0.01)
    }

    @Test fun `vingt-cinq francs par dollar font environ quatre pour cent`() {
        assertEquals(4.17, TauxFcfa.margeEnPourcent(25.0, TAUX)!!, 0.01)
    }

    /*
    ───────────────────────────────────────────────────────────────────────
    LES DEUX SENS DE CONVERSION
    ───────────────────────────────────────────────────────────────────────
    */

    @Test fun `combien de crypto pour cinquante mille francs`() {
        val p = TauxFcfa.prix(600.0, 25.0, TAUX)!!
        // 50 000 / 625 = 80 unités.
        assertEquals(80.0, TauxFcfa.cryptoPourFcfa(50_000.0, p)!!, 0.001)
    }

    @Test fun `combien de francs pour quatre-vingts unites`() {
        val p = TauxFcfa.prix(600.0, 25.0, TAUX)!!
        // 80 x 575 = 46 000.
        assertEquals(46_000.0, TauxFcfa.fcfaPourCrypto(80.0, p)!!, 0.001)
    }

    /**
     * L'ALLER-RETOUR COÛTE DEUX FOIS LA MARGE, et c'est normal.
     *
     * Acheter puis revendre aussitôt fait perdre deux fois 4,17 %. Le
     * vérifier ici garantit que les deux sens sont bien symétriques — une
     * marge appliquée dans le même sens des deux côtés serait invisible à
     * l'œil et offrirait un aller-retour gagnant à l'utilisateur, ou
     * ruineux.
     */
    @Test fun `un aller-retour coute exactement deux fois la marge`() {
        val p = TauxFcfa.prix(600.0, 25.0, TAUX)!!
        val crypto = TauxFcfa.cryptoPourFcfa(50_000.0, p)!!
        val retour = TauxFcfa.fcfaPourCrypto(crypto, p)!!
        val perte = (50_000.0 - retour) / 50_000.0 * 100.0
        // 1 - (575/625) = 8 %, soit deux fois 4,17 % moins le produit croisé.
        assertEquals(8.0, perte, 0.01)
        assertTrue("l'aller-retour doit coûter, jamais rapporter", retour < 50_000.0)
    }

    @Test fun `aucune conversion sur un montant nul`() {
        val p = TauxFcfa.prix(600.0, 25.0, TAUX)!!
        assertNull(TauxFcfa.cryptoPourFcfa(0.0, p))
        assertNull(TauxFcfa.fcfaPourCrypto(0.0, p))
        assertNull(TauxFcfa.cryptoPourFcfa(-1.0, p))
    }
}
