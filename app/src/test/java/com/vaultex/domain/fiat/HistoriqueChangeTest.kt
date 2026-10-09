package com.vaultex.domain.fiat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ═══════════════════════════════════════════════════════════════════════
 * CE QUI PEUT SE PERDRE EN SILENCE
 * ═══════════════════════════════════════════════════════════════════════
 *
 * [HistoriqueChange.fusionner] est la seule partie de l'historique qui
 * puisse se tromper sans lever d'erreur : perdre une entree, en garder
 * deux fois la meme, ou inverser l'ordre.
 *
 * Aucune de ces trois fautes ne se verrait a la compilation, et aucune ne
 * se verrait non plus en testant l'ecran une fois : il faut une demande
 * renvoyee apres coupure reseau pour voir le doublon, et cinquante et une
 * demandes pour voir le plafond mal applique.
 * ═══════════════════════════════════════════════════════════════════════
 */
class HistoriqueChangeTest {

    private fun demande(ref: String, t: Long, fcfa: String = "5 000") = DemandeChange(
        reference = ref,
        sens = "achat",
        monnaie = "USDT",
        montantFcfa = fcfa,
        montantCrypto = "8",
        taux = "625 FCFA",
        horodatage = t
    )

    @Test
    fun `la nouvelle demande arrive en tete`() {
        val liste = HistoriqueChange.fusionner(
            listOf(demande("VX-AAAAAAAA", 1_000L)),
            demande("VX-BBBBBBBB", 2_000L)
        )
        assertEquals(2, liste.size)
        assertEquals("VX-BBBBBBBB", liste[0].reference)
        assertEquals("VX-AAAAAAAA", liste[1].reference)
    }

    /**
     * LE CAS QUI JUSTIFIE CETTE FONCTION.
     *
     * Une coupure reseau pendant la transmission, puis un second essai :
     * la reference est la MEME — c'est voulu, c'est ce qui empeche le
     * relais de poster deux fois. L'historique doit faire de meme, sinon
     * l'utilisateur croit avoir paye deux fois.
     */
    @Test
    fun `une reference deja presente ne cree pas de doublon`() {
        val liste = HistoriqueChange.fusionner(
            listOf(demande("VX-AAAAAAAA", 1_000L, fcfa = "5 000")),
            demande("VX-AAAAAAAA", 1_500L, fcfa = "6 000")
        )
        assertEquals(1, liste.size)
        // C'est la NOUVELLE version qui reste : elle porte l'horodatage et
        // les montants tels qu'ils sont partis chez le changeur.
        assertEquals("6 000", liste[0].montantFcfa)
        assertEquals(1_500L, liste[0].horodatage)
    }

    @Test
    fun `l-ordre est du plus recent au plus ancien quel que soit l-ordre d-arrivee`() {
        val existantes = listOf(
            demande("VX-AAAAAAAA", 5_000L),
            demande("VX-BBBBBBBB", 1_000L),
            demande("VX-CCCCCCCC", 3_000L)
        )
        val liste = HistoriqueChange.fusionner(existantes, demande("VX-DDDDDDDD", 2_000L))
        assertEquals(
            listOf("VX-AAAAAAAA", "VX-CCCCCCCC", "VX-DDDDDDDD", "VX-BBBBBBBB"),
            liste.map { it.reference }
        )
    }

    /**
     * UNE DEMANDE PLUS ANCIENNE QUE TOUTES LES AUTRES NE PASSE PAS EN TETE.
     *
     * Le cas arrive apres une reprise : l'horodatage vient de l'instant ou
     * la demande est PARTIE, pas de celui ou l'ecran s'est ouvert. Si le
     * tri ne s'appliquait pas, elle s'afficherait en premier et laisserait
     * croire qu'elle est la plus recente.
     */
    @Test
    fun `une nouvelle demande plus ancienne se range a sa place`() {
        val liste = HistoriqueChange.fusionner(
            listOf(demande("VX-AAAAAAAA", 9_000L)),
            demande("VX-BBBBBBBB", 100L)
        )
        assertEquals("VX-AAAAAAAA", liste[0].reference)
        assertEquals("VX-BBBBBBBB", liste[1].reference)
    }

    @Test
    fun `le plafond garde les plus recentes et jette les plus anciennes`() {
        // MAX entrees deja presentes, de la plus recente a la plus ancienne.
        val existantes = (1..HistoriqueChange.MAX).map { i ->
            demande("VX-%08d".format(i), (HistoriqueChange.MAX - i + 1).toLong() * 10L)
        }
        val liste = HistoriqueChange.fusionner(existantes, demande("VX-NOUVELLE", 100_000L))

        assertEquals(HistoriqueChange.MAX, liste.size)
        assertEquals("VX-NOUVELLE", liste[0].reference)
        // La plus ancienne — celle du bas — est celle qui disparait.
        assertTrue(liste.none { it.reference == "VX-%08d".format(HistoriqueChange.MAX) })
    }

    @Test
    fun `une liste vide accepte la premiere demande`() {
        val liste = HistoriqueChange.fusionner(emptyList(), demande("VX-AAAAAAAA", 1L))
        assertEquals(1, liste.size)
        assertEquals("VX-AAAAAAAA", liste[0].reference)
    }

    @Test
    fun `le sens se relit tel qu-il a ete transmis`() {
        assertTrue(demande("VX-AAAAAAAA", 1L).estAchat)
        assertTrue(!demande("VX-AAAAAAAA", 1L).copy(sens = "vente").estAchat)
    }
}
