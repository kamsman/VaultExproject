package com.vaultex.domain.fiat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ═══════════════════════════════════════════════════════════════════════
 * UN MODÈLE RÉGLÉ A DISTANCE EST DU CODE EXÉCUTÉ SUR LE TÉLÉPHONE
 * ═══════════════════════════════════════════════════════════════════════
 *
 * CHANGE_USSD arrive d'une variable Cloudflare et finit dans le composeur
 * de quelqu'un, sur une opération d'argent irréversible. Ce qui s'y
 * glisserait par erreur — une accolade restée ouverte, une lettre, un
 * emplacement de code secret — ne leverait aucune exception : ça
 * composerait simplement autre chose que ce qu'on croit.
 *
 * Ces tests disent donc surtout ce qui doit être REFUSÉ.
 * ═══════════════════════════════════════════════════════════════════════
 */
class CodeUssdTest {

    private val modele = "*144*2*1*{numero}*{montant}#"

    // ─── Ce qui doit marcher ────────────────────────────────────────

    @Test
    fun `un modele correct compose la chaine attendue`() {
        assertEquals(
            "*144*2*1*70123456*10000#",
            CodeUssd.composer(modele, "70123456", 10_000.0)
        )
    }

    @Test
    fun `le numero est reduit a ses chiffres`() {
        /*
        Le reglage du relais peut porter « 70 12 34 56 », parce que c'est
        ainsi qu'on ecrit un numero. Une espace dans un code USSD le fait
        echouer — silencieusement, avec un « connexion impossible » qui ne
        dit pas pourquoi.
        */
        assertEquals(
            "*144*2*1*70123456*5000#",
            CodeUssd.composer(modele, "70 12 34 56", 5_000.0)
        )
        assertEquals(
            "*144*2*1*22670123456*5000#",
            CodeUssd.composer(modele, "+226 70 12 34 56", 5_000.0)
        )
    }

    @Test
    fun `le montant est arrondi au franc`() {
        // Le franc CFA n'a pas de centimes, et une virgule dans un code
        // USSD le fait echouer.
        assertEquals(
            "*144*2*1*70123456*5001#",
            CodeUssd.composer(modele, "70123456", 5_000.6)
        )
    }

    @Test
    fun `un modele qui commence par diese est accepte`() {
        assertTrue(CodeUssd.modeleValide("#144#{numero}*{montant}#"))
    }

    // ─── CE QUI DOIT ÊTRE REFUSÉ ────────────────────────────────────

    /**
     * LE TEST LE PLUS IMPORTANT DE CE FICHIER.
     *
     * Certaines syntaxes USSD acceptent le code secret en dernier
     * paramètre. Le pré-remplir serait une faute grave : une chaîne USSD
     * s'affiche en clair pendant la frappe, reste dans le journal
     * d'appels du téléphone, et une application qui demande un code Orange
     * Money fait le geste même que les campagnes anti-arnaque décrivent.
     */
    @Test
    fun `tout emplacement de code secret fait refuser le modele`() {
        for (mauvais in listOf(
            "*144*2*3*{numero}*{montant}*{pin}#",
            "*144*2*3*{numero}*{montant}*{code}#",
            "*144*2*3*{numero}*{montant}*{secret}#",
            "*144*2*3*{numero}*{montant}*{mdp}#"
        )) {
            assertFalse(mauvais, CodeUssd.modeleValide(mauvais))
            assertNull(mauvais, CodeUssd.composer(mauvais, "70123456", 5_000.0))
        }
    }

    /**
     * ON REFUSE TOUT CE QUI N'EST PAS LES DEUX EMPLACEMENTS CONNUS.
     *
     * Une liste noire de mots interdits s'oublie — il suffit d'un
     * « {codepin} » pour passer à travers. Une liste blanche, non. Et ce
     * choix attrape en prime les fautes de frappe, qui laisseraient des
     * accolades dans la chaîne composée.
     */
    @Test
    fun `une faute de frappe dans un emplacement fait refuser`() {
        assertFalse(CodeUssd.modeleValide("*144*2*1*{numro}*{montant}#"))
        assertFalse(CodeUssd.modeleValide("*144*2*1*{numero}*{montan}#"))
        assertFalse(CodeUssd.modeleValide("*144*2*1*{numero*{montant}#"))
    }

    @Test
    fun `un modele sans les deux emplacements est refuse`() {
        assertFalse(CodeUssd.modeleValide("*144*2*1*{numero}#"))
        assertFalse(CodeUssd.modeleValide("*144*2*1*{montant}#"))
        assertFalse(CodeUssd.modeleValide("*144#"))
    }

    @Test
    fun `un modele qui n est pas un code USSD est refuse`() {
        // Sans etoile ni diese, ce n'est pas un code : c'est un numero
        // qu'on appellerait.
        assertFalse(CodeUssd.modeleValide("144*{numero}*{montant}#"))
        // Sans diese final, le code n'est jamais valide par l'operateur.
        assertFalse(CodeUssd.modeleValide("*144*{numero}*{montant}"))
        // Une lettre n'a rien a faire dans une syntaxe USSD.
        assertFalse(CodeUssd.modeleValide("*144*ABC*{numero}*{montant}#"))
    }

    @Test
    fun `un modele vide ou absent est refuse`() {
        assertFalse(CodeUssd.modeleValide(null))
        assertFalse(CodeUssd.modeleValide(""))
        assertFalse(CodeUssd.modeleValide("   "))
        assertNull(CodeUssd.composer(null, "70123456", 5_000.0))
    }

    @Test
    fun `un numero trop court est refuse`() {
        // Huit chiffres au Burkina Faso. Sept, c'est une saisie
        // incomplete, et composer avec elle enverrait les fonds nulle part
        // — ou chez quelqu'un d'autre.
        assertNull(CodeUssd.composer(modele, "7012345", 5_000.0))
        assertNull(CodeUssd.composer(modele, "", 5_000.0))
    }

    @Test
    fun `un montant nul ou negatif est refuse`() {
        assertNull(CodeUssd.composer(modele, "70123456", 0.0))
        assertNull(CodeUssd.composer(modele, "70123456", -1_000.0))
        // Arrondi a zero : un montant que personne ne voulait envoyer.
        assertNull(CodeUssd.composer(modele, "70123456", 0.4))
    }

    // ─── L'encodage pour l'URI ──────────────────────────────────────

    /**
     * LE DIÈSE DOIT ÊTRE ENCODÉ, SINON RIEN NE MARCHE.
     *
     * Dans une URI, « # » ouvre un fragment : tout ce qui le suit n'est
     * pas transmis. Le composeur recevrait la chaîne SANS son dièse
     * final — c'est-à-dire sans la touche qui valide le code.
     *
     * Le symptôme est le pire possible : le composeur s'ouvre, la chaîne a
     * l'air juste, et rien ne se passe à l'appel. On cherche du côté de
     * l'opérateur pendant une heure.
     */
    @Test
    fun `le diese est encode pour l URI, l etoile non`() {
        assertEquals(
            "*144*2*1*70123456*10000%23",
            CodeUssd.pourUriTel("*144*2*1*70123456*10000#")
        )
    }

    @Test
    fun `tous les dieses sont encodes, pas seulement le dernier`() {
        // « #144#...# » en porte trois. Un seul oublie au milieu coupe la
        // chaine au meme endroit qu'un oubli a la fin.
        assertEquals("%23144%2370123456%23", CodeUssd.pourUriTel("#144#70123456#"))
    }

    // ─── Le chemin complet ──────────────────────────────────────────

    @Test
    fun `du modele a l URI, sans rien perdre`() {
        val compose = CodeUssd.composer(modele, "70 12 34 56", 10_000.0)
        assertEquals("*144*2*1*70123456*10000#", compose)
        assertEquals("*144*2*1*70123456*10000%23", CodeUssd.pourUriTel(compose!!))
    }
}
