package com.vaultex.domain.fiat

import com.vaultex.ui.viewmodel.ChangeState
import com.vaultex.ui.viewmodel.EtapeChange
import com.vaultex.ui.viewmodel.SensChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ═══════════════════════════════════════════════════════════════════════
 * LA PORTE DU BOUTON, ET LA LECTURE DU VERDICT
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Deux choses dont la défaillance ne lève aucune erreur, et qui décident
 * toutes les deux de ce qui arrive à de l'argent déjà envoyé.
 *
 * LA LECTURE DU VERDICT. Si « indisponible » se lisait comme « confirmé »,
 * l'écran afficherait un vert que le relais n'a pas donné — et
 * l'utilisateur attendrait tranquillement un paiement que le changeur n'a
 * aucune raison de faire.
 *
 * LA PORTE DU BOUTON. Si elle s'ouvrait sans numéro de téléphone, une
 * vente partirait avec « ENVOYER 5 000 FCFA au numero du client » — sans
 * numéro. Le changeur n'aurait aucun moyen de payer, et la crypto serait
 * déjà partie. C'est un défaut qui existait avant la vérification, et que
 * la relecture de la ligne « A FAIRE » a fait apparaître.
 * ═══════════════════════════════════════════════════════════════════════
 */
class VerificationTest {

    // ─── Lire le verdict du relais ──────────────────────────────────

    @Test
    fun `les quatre etats du relais se lisent`() {
        assertEquals(EtatVerification.CONFIRME, EtatVerification.depuisTexte("confirme"))
        assertEquals(EtatVerification.ABSENT, EtatVerification.depuisTexte("absent"))
        assertEquals(EtatVerification.DEJA_SERVI, EtatVerification.depuisTexte("deja_servi"))
        assertEquals(EtatVerification.INDISPONIBLE, EtatVerification.depuisTexte("indisponible"))
        assertEquals(EtatVerification.INDISPONIBLE, EtatVerification.depuisTexte("inconnue"))
    }

    @Test
    fun `la casse et les espaces ne changent rien`() {
        assertEquals(EtatVerification.CONFIRME, EtatVerification.depuisTexte("  CONFIRME "))
    }

    /**
     * LE CAS LE PLUS IMPORTANT DE CE FICHIER.
     *
     * Un mot inconnu — relais d'une version plus récente, réponse
     * tronquée, champ absent — ne doit JAMAIS devenir CONFIRME. Afficher
     * un vert que le relais n'a pas donné laisserait quelqu'un attendre un
     * paiement que le changeur n'a aucune raison de faire.
     *
     * Il devient INCONNUE, qui n'est pas vert et n'ouvre pas le bouton.
     */
    @Test
    fun `un etat inconnu ne devient jamais vert`() {
        for (mot in listOf(null, "", "oui", "ok", "true", "verifie", "CONFIRMED")) {
            val etat = EtatVerification.depuisTexte(mot)
            assertEquals("pour « $mot »", EtatVerification.INCONNUE, etat)
            assertFalse("« $mot » ne doit pas etre vert", Verification(etat).estVert)
        }
    }

    // ─── Ce qui vaut la peine d'etre reessaye ───────────────────────

    @Test
    fun `absent merite d etre reessaye, pas le reste`() {
        /*
        Un versement absent peut arriver : c'est le retrait encore en file
        chez une plateforme d'echange. Un noeud muet ou une monnaie dont on
        ne lit pas la chaine, non — insister n'y changera rien, et proposer
        « reessayer » serait proposer un geste inutile.
        */
        assertFalse(Verification(EtatVerification.ABSENT).sansEspoir)
        assertTrue(Verification(EtatVerification.INDISPONIBLE).sansEspoir)
        assertTrue(Verification(EtatVerification.DEJA_SERVI).sansEspoir)
    }

    @Test
    fun `seul confirme est vert`() {
        assertTrue(Verification(EtatVerification.CONFIRME).estVert)
        for (e in EtatVerification.values().filter { it != EtatVerification.CONFIRME }) {
            assertFalse(e.name, Verification(e).estVert)
        }
    }

    // ─── Le numero de telephone ─────────────────────────────────────

    @Test
    fun `un numero se compte en chiffres, pas en caracteres`() {
        // « 70 12 34 56 » est la forme sous laquelle on ecrit un numero au
        // Burkina Faso. La refuser ferait buter sur un champ correct.
        assertTrue(vente(telephone = "70 12 34 56").telephoneValide)
        assertTrue(vente(telephone = "70123456").telephoneValide)
        // Avec l'indicatif, onze chiffres : ce n'est pas la forme attendue,
        // et accepter les deux ferait passer un numero a dix chiffres.
        assertFalse(vente(telephone = "+226 70 12 34 56").telephoneValide)
    }

    @Test
    fun `un numero incomplet n est pas valide`() {
        assertFalse(vente(telephone = "7012345").telephoneValide)
        assertFalse(vente(telephone = "").telephoneValide)
        assertFalse(vente(telephone = "701234567").telephoneValide)
    }

    // ─── La porte du bouton ─────────────────────────────────────────

    @Test
    fun `une vente verifiee avec numero peut partir`() {
        val s = vente(
            telephone = "70123456",
            verification = Verification(EtatVerification.CONFIRME)
        )
        assertTrue(s.peutTransmettre())
    }

    /**
     * LE DEFAUT QUI EXISTAIT AVANT LA VERIFICATION.
     *
     * Rien n'exigeait le numéro sur une vente. Elle partait donc, et le
     * message disait au changeur d'envoyer les francs « au numero du
     * client » — sans numéro. Il n'avait aucun moyen de payer, et la
     * crypto de l'utilisateur était déjà partie.
     */
    @Test
    fun `une vente verifiee SANS numero ne peut pas partir`() {
        val s = vente(
            telephone = "",
            verification = Verification(EtatVerification.CONFIRME)
        )
        assertFalse(s.peutTransmettre())
    }

    @Test
    fun `une vente non verifiee ne part pas d elle-meme`() {
        val s = vente(
            telephone = "70123456",
            verification = Verification(EtatVerification.ABSENT)
        )
        assertFalse(s.peutTransmettre())
    }

    /**
     * MAIS ELLE DOIT POUVOIR PARTIR SI L'UTILISATEUR LE DEMANDE.
     *
     * Ses fonds sont déjà partis — c'est irréversible. Si son retrait
     * traîne chez une plateforme d'échange, l'empêcher de déposer sa
     * demande le laisserait avec de la crypto envoyée et rien chez le
     * changeur : exactement la situation qu'on cherche à supprimer.
     */
    @Test
    fun `forcer ouvre la porte, meme sans verification`() {
        val s = vente(
            telephone = "70123456",
            verification = Verification(EtatVerification.ABSENT),
            forcer = true
        )
        assertTrue(s.peutTransmettre())
    }

    @Test
    fun `forcer n ouvre pas la porte sans numero`() {
        // Le numero n'est pas une formalite qu'on peut sauter : sans lui,
        // le changeur n'a nulle part ou payer.
        val s = vente(telephone = "", forcer = true)
        assertFalse(s.peutTransmettre())
    }

    @Test
    fun `un achat ne depend pas de la chaine`() {
        /*
        Un paiement Orange Money n'est sur aucune chaine publique : il n'y
        a rien a verifier, et exiger une verification bloquerait tous les
        achats. C'est la reference recopiee qui reste le seul fil.
        */
        val s = ChangeState(
            etape = EtapeChange.PAIEMENT,
            sens = SensChange.ACHAT,
            reference = "VX-AAAAAAAA",
            referencePaiement = "MP251008123456"
        )
        assertTrue(s.peutTransmettre())
        assertFalse(s.copy(referencePaiement = "").peutTransmettre())
    }

    @Test
    fun `un envoi en cours referme la porte`() {
        // Sans ca, un double appui envoie deux fois la meme demande.
        val s = vente(
            telephone = "70123456",
            verification = Verification(EtatVerification.CONFIRME)
        ).copy(envoiEnCours = true)
        assertFalse(s.peutTransmettre())
    }

    @Test
    fun `sans reference, rien ne part`() {
        val s = vente(
            telephone = "70123456",
            verification = Verification(EtatVerification.CONFIRME)
        ).copy(reference = "")
        assertFalse(s.peutTransmettre())
    }

    // ─── Le minimum par monnaie ─────────────────────────────────────

    /**
     * ═══════════════════════════════════════════════════════════════
     * LE RESEAU LE PLUS CHER NE DOIT PAS DEVENIR UN PIEGE
     * ═══════════════════════════════════════════════════════════════
     *
     * Les frais de reseau n'ont rien a voir d'une chaine a l'autre :
     * 13 a 27 TRX sur TRON, environ 0,20 $ sur BNB Chain, 1 a 10 $ sur
     * Ethereum.
     *
     * Sur une vente de 8 USDT — cinq mille francs — Ethereum prend entre
     * douze et cent vingt-cinq pour cent de l'operation. Quelqu'un
     * choisirait « USDT-ETH » dans la liste PARCE QU'IL Y EST, et ne
     * decouvrirait le cout qu'au moment de signer dans son portefeuille,
     * bien apres avoir lu « Tu recois 4 800 FCFA ».
     */
    @Test
    fun `chaque monnaie a son minimum`() {
        val p = parametres(
            minimumFcfa = 5_000.0,
            minimums = mapOf("USDT" to 20_000.0, "USDT-BNB" to 2_000.0, "USDT-ETH" to 50_000.0)
        )
        assertEquals(20_000.0, p.minimumPour("USDT"), 0.0)
        assertEquals(2_000.0, p.minimumPour("USDT-BNB"), 0.0)
        assertEquals(50_000.0, p.minimumPour("USDT-ETH"), 0.0)
    }

    @Test
    fun `la casse ne compte pas`() {
        val p = parametres(minimums = mapOf("USDT-BNB" to 2_000.0))
        assertEquals(2_000.0, p.minimumPour("usdt-bnb"), 0.0)
    }

    /**
     * UNE MONNAIE ABSENTE RETOMBE SUR LE SEUIL GENERAL.
     *
     * C'est ce qui rend l'ajout d'une chaine sans risque : on applique le
     * comportement d'avant au lieu d'ouvrir un trou. Rendre zero — ou
     * rien — ouvrirait la monnaie sans aucun minimum.
     */
    @Test
    fun `une monnaie absente retombe sur le seuil general`() {
        val p = parametres(minimumFcfa = 5_000.0, minimums = mapOf("USDT" to 20_000.0))
        assertEquals(5_000.0, p.minimumPour("BTC"), 0.0)
        assertEquals(5_000.0, parametres(minimumFcfa = 5_000.0).minimumPour("USDT"), 0.0)
    }

    @Test
    fun `un seuil nul ou negatif est ecarte`() {
        // Zero n'est pas un seuil, c'est l'absence de seuil. On ne laisse
        // pas une valeur mal reglee ouvrir une monnaie sans minimum.
        val p = parametres(
            minimumFcfa = 5_000.0,
            minimums = mapOf("USDT" to 0.0, "USDT-BNB" to -1.0)
        )
        assertEquals(5_000.0, p.minimumPour("USDT"), 0.0)
        assertEquals(5_000.0, p.minimumPour("USDT-BNB"), 0.0)
    }

    /** Et le bouton doit s'eteindre sur le seuil de LA monnaie choisie. */
    @Test
    fun `le blocage suit le minimum de la monnaie`() {
        val p = parametres(
            minimumFcfa = 5_000.0,
            minimums = mapOf("USDT-BNB" to 2_000.0, "USDT-ETH" to 50_000.0)
        )
        val prix = TauxFcfa.prix(585.0, 25.0, 585.0)!!
        // 5 USDT a ~560 FCFA font 2 800 francs : au-dessus du seuil BNB,
        // tres en dessous du seuil Ethereum.
        val base = ChangeState(
            parametres = p,
            sens = SensChange.VENTE,
            saisie = "5",
            prix = prix,
            fcfaParDollar = 585.0
        )
        assertEquals(null, base.copy(monnaie = "USDT-BNB").blocage())
        assertEquals("minimum", base.copy(monnaie = "USDT-ETH").blocage())
    }

    private fun parametres(
        minimumFcfa: Double = 5_000.0,
        minimums: Map<String, Double> = emptyMap()
    ) = ParametresChange(
        actif = true,
        margeFcfaParDollar = 25.0,
        minimumFcfa = minimumFcfa,
        plafondFcfa = 50_000.0,
        numeroMobileMoney = "64088910",
        nomChangeur = "KOANDA",
        operateur = "Orange Money",
        delaiMinutes = 30,
        monnaies = listOf("USDT", "USDT-BNB", "USDT-ETH"),
        adresses = mapOf(
            "USDT" to "TQn9Y2khDD95J42FQtQTdwVVRZqjGBCvpM",
            "USDT-BNB" to "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045",
            "USDT-ETH" to "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045"
        ),
        minimumsParMonnaie = minimums
    )

    // ─── Les decimales affichees ────────────────────────────────────

    /**
     * NE PAS PROMETTRE UNE PRECISION QUI N'EXISTE PAS.
     *
     * L'ecran affichait « 8,19672131 USDT » pour un achat de 5 000 francs.
     * Le calcul etait juste, le nombre non : l'USDT a SIX decimales, et
     * les deux derniers chiffres ne peuvent pas etre envoyes. On annoncait
     * un montant que personne ne peut recevoir.
     *
     * Jamais un franc de perdu — l'ecart est d'un millionieme — mais c'est
     * le genre de petite faussete qui use la confiance : qui compare le
     * montant promis a celui recu trouve une difference, et il a raison.
     */
    @Test
    fun `les decimales affichees sont celles de la monnaie`() {
        assertEquals(6, TauxFcfa.decimalesAffichage("USDT"))
        assertEquals(6, TauxFcfa.decimalesAffichage("usdt"))
        assertEquals(6, TauxFcfa.decimalesAffichage("TRX"))
        // Le bitcoin garde ses huit : a 50 000 francs l'operation, on y
        // parle de dix-milliemes.
        assertEquals(8, TauxFcfa.decimalesAffichage("BTC"))
        // Pi en a sept, comme Stellar dont il est issu.
        assertEquals(7, TauxFcfa.decimalesAffichage("PI"))
        // Une monnaie inconnue retombe sur six, qui ne promet rien de trop.
        assertEquals(6, TauxFcfa.decimalesAffichage("DOGE"))
    }

    private fun vente(
        telephone: String = "",
        verification: Verification? = null,
        forcer: Boolean = false
    ) = ChangeState(
        etape = EtapeChange.PAIEMENT,
        sens = SensChange.VENTE,
        monnaie = "USDT",
        reference = "VX-AAAAAAAA",
        telephone = telephone,
        verification = verification,
        forcer = forcer
    )
}
