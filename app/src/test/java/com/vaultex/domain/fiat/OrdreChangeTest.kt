package com.vaultex.domain.fiat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ═══════════════════════════════════════════════════════════════════════
 * LA RÉFÉRENCE, ET CE QUI REND UN SERVICE UTILISABLE
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Deux petites choses dont la défaillance ne lève aucune erreur : une
 * référence ambiguë fait rater un rapprochement entre un paiement et une
 * demande, et un service annoncé actif sans numéro envoie quelqu'un payer
 * personne.
 * ═══════════════════════════════════════════════════════════════════════
 */
class OrdreChangeTest {

    /**
     * NI O NI ZÉRO, NI I NI UN.
     *
     * Cette référence sera dictée au téléphone, recopiée à la main dans un
     * champ Mobile Money, et cherchée dans un fil Telegram. « VX-0IO1 » se
     * transcrit de huit façons.
     *
     * Le coût d'une confusion n'est pas théorique : c'est un paiement qu'on
     * ne rattache à aucune demande, donc un utilisateur qui a envoyé son
     * argent et que personne ne retrouve.
     */
    @Test fun `la reference evite les caracteres qui se confondent`() {
        repeat(200) {
            val r = OrdreChange.nouvelleReference()
            assertTrue("doit commencer par VX-", r.startsWith("VX-"))
            assertEquals("VX- plus huit caractères", 11, r.length)
            val corps = r.removePrefix("VX-")
            assertFalse("le O et le 0 se confondent : $r", corps.contains('O'))
            assertFalse("le O et le 0 se confondent : $r", corps.contains('0'))
            assertFalse("le I et le 1 se confondent : $r", corps.contains('I'))
            assertFalse("le I et le 1 se confondent : $r", corps.contains('1'))
            assertTrue(
                "alphabet inattendu : $r",
                corps.all { it in 'A'..'Z' || it in '2'..'9' }
            )
        }
    }

    /**
     * Deux demandes ne doivent pas porter la même référence.
     *
     * Le relais refuse une référence déjà vue pendant une heure — c'est ce
     * qui empêche un renvoi après coupure réseau de créer un doublon. Mais
     * si deux utilisateurs différents tiraient la même, le second verrait
     * sa demande silencieusement avalée.
     */
    @Test fun `deux references tirees ne se repetent pas`() {
        val tirages = (1..500).map { OrdreChange.nouvelleReference() }.toSet()
        /*
        On tolère UNE répétition, et on ne peut pas faire autrement : un
        tirage aléatoire n'offre jamais de garantie absolue, seulement une
        probabilité. Sur 32^8 combinaisons, elle est d'environ une sur dix
        millions pour cinq cents demandes — assez pour que ce test ne
        clignote pas, assez pour qu'une vraie régression de l'alphabet ou
        de la longueur le fasse tomber tout de suite.
        */
        assertTrue("collisions : ${500 - tirages.size}", tirages.size >= 499)
    }

    /*
    ───────────────────────────────────────────────────────────────────────
    UN SERVICE ANNONCÉ ACTIF DOIT ÊTRE UTILISABLE
    ───────────────────────────────────────────────────────────────────────
    */

    private fun params(
        actif: Boolean = true,
        numero: String = "70000000",
        min: Double = 5000.0,
        max: Double = 50000.0
    ) = ParametresChange(
        actif = actif,
        margeFcfaParDollar = 25.0,
        minimumFcfa = min,
        plafondFcfa = max,
        numeroMobileMoney = numero,
        nomChangeur = "Changeur",
        operateur = "Orange Money",
        delaiMinutes = 30
    )

    @Test fun `un service complet est utilisable`() {
        assertTrue(params().utilisable)
    }

    @Test fun `l'interrupteur ferme le service`() {
        assertFalse(params(actif = false).utilisable)
    }

    /**
     * SANS NUMÉRO, L'ÉCRAN ENVERRAIT PAYER PERSONNE.
     *
     * Le réglage distant permet d'activer le service avant d'avoir saisi le
     * numéro du changeur — c'est une manipulation de tableau de bord, et
     * l'ordre des champs n'y est garanti par rien. L'utilisateur verrait
     * alors un écran complet, cliquerait, et n'aurait aucun destinataire.
     */
    @Test fun `un service sans numero n'est pas utilisable`() {
        assertFalse(params(numero = "").utilisable)
        assertFalse(params(numero = "   ").utilisable)
    }

    /** Des bornes inversées rendraient tout montant invalide. */
    @Test fun `des bornes incoherentes ferment le service`() {
        assertFalse(params(min = 50000.0, max = 5000.0).utilisable)
        assertFalse(params(min = 10000.0, max = 10000.0).utilisable)
    }

    /*
    ───────────────────────────────────────────────────────────────────────
    CE QUI PART VERS LE CHANGEUR
    ───────────────────────────────────────────────────────────────────────
    */

    @Test fun `l'ordre transporte tout ce que le changeur doit lire`() {
        val ordre = OrdreChange(
            reference = "VX-ABC234",
            sens = "achat",
            monnaie = "USDT",
            montantFcfa = "50 000",
            montantCrypto = "80,00",
            taux = "625 FCFA",
            marge = "25 FCFA/$ (4,17 %)",
            adresse = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t",
            telephone = "70000000",
            referencePaiement = "OM240108123"
        )
        val corps = ordre.versCorps()
        assertEquals("VX-ABC234", corps.reference)
        assertEquals("achat", corps.sens)
        assertEquals("80,00", corps.montantCrypto)
        assertEquals("25 FCFA/$ (4,17 %)", corps.marge)
        assertEquals("OM240108123", corps.referencePaiement)
    }
}
