package com.vaultex.ui.screens.change

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ═══════════════════════════════════════════════════════════════════════
 * LE NOMBRE QU'ON AFFICHE DOIT ÊTRE UN NOMBRE QU'ON ACCEPTE
 * ═══════════════════════════════════════════════════════════════════════
 *
 * VU SUR L'APPAREIL, sur l'achat en USDT-ERC20 : l'écran annonce
 * « Minimum : 6 958 FCFA ». On tape 6 958 — refusé, avec le même message
 * et le même nombre. Il fallait deviner qu'il en manquait un.
 *
 * Le minimum réel vaut 6 958,38 : dix dollars à 660 francs, plus 0,60 $
 * de gaz converti au cours. `fcfa()` arrondissait au PLUS PROCHE, donc
 * vers le bas, tandis que le contrôle compare au réel.
 *
 * ─── CE QUE CE FICHIER TIENT ───────────────────────────────────────────
 *
 * Une seule propriété, et c'est la bonne : **le nombre affiché passe le
 * contrôle**. Pas « l'arrondi est correct » — on peut arrondir
 * correctement et refuser quand même. Ce qui compte est qu'un utilisateur
 * qui recopie ce qu'il lit soit accepté.
 *
 * Les deux sens sont testés. Le défaut était symétrique et personne ne
 * l'avait vu du côté du plafond : un maximum de 49 999,6 affiché
 * « 50 000 » se ferait refuser tout pareil, pour dépassement.
 * ═══════════════════════════════════════════════════════════════════════
 */
class SeuilAfficheTest {

    /*
    LE CAS EXACT RENCONTRÉ, avec ses vrais chiffres : dix dollars au prix
    client de 660, plus 0,60 $ de gaz au cours de 597,30.
    */
    private val minimumErc20 = 10 * 660.0 + 0.60 * 597.30   // 6 958,38

    @Test
    fun `le minimum affiche est accepte par le controle`() {
        val affiche = seuilAffiche(minimumErc20, versLeHaut = true)
        assertTrue(
            "L'ecran annoncerait $affiche alors que le controle exige $minimumErc20 : " +
                "celui qui recopie le nombre affiche se fait refuser.",
            affiche >= minimumErc20
        )
        assertEquals(6959.0, affiche, 0.0)
    }

    @Test
    fun `le plafond affiche est accepte par le controle`() {
        val plafond = 49_999.6
        val affiche = seuilAffiche(plafond, versLeHaut = false)
        assertTrue(
            "L'ecran annoncerait $affiche alors que le controle plafonne a $plafond.",
            affiche <= plafond
        )
        assertEquals(49_999.0, affiche, 0.0)
    }

    /*
    UN SEUIL DÉJÀ ENTIER NE DOIT PAS BOUGER. Sans ce contrôle, une
    correction maladroite — « +1 pour être sûr » — ferait afficher 6 601
    là où 6 600 convient, et le minimum grossirait d'un franc à chaque
    relecture du code.
    */
    @Test
    fun `un seuil entier reste identique dans les deux sens`() {
        assertEquals(6600.0, seuilAffiche(6600.0, versLeHaut = true), 0.0)
        assertEquals(6600.0, seuilAffiche(6600.0, versLeHaut = false), 0.0)
    }

    /*
    LA PROPRIÉTÉ, SUR UNE PLAGE ENTIÈRE plutôt que sur trois exemples.
    Cent centimes couvrent toutes les parties décimales possibles d'un
    seuil en francs.
    */
    @Test
    fun `sur cent decimales, l affiche passe toujours le controle`() {
        for (centimes in 0..99) {
            val seuil = 6958.0 + centimes / 100.0
            val plancher = seuilAffiche(seuil, versLeHaut = true)
            val plafond = seuilAffiche(seuil, versLeHaut = false)
            assertTrue("plancher $plancher < seuil $seuil", plancher >= seuil)
            assertTrue("plafond $plafond > seuil $seuil", plafond <= seuil)
        }
    }
}
