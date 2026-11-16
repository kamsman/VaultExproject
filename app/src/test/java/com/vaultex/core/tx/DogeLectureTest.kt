package com.vaultex.core.tx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ═══════════════════════════════════════════════════════════════════════
 * DEUX RAISONNEMENTS QUI ONT DÉJÀ FAILLI COÛTER CHER
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Les deux viennent de mesures prises sur les vrais services, et aucun
 * des deux ne lève d'erreur quand il se trompe.
 *
 * ─── LE MONTANT ────────────────────────────────────────────────────────
 *
 * `"value": "0.01000000"` : une chaîne, en DOGE. Le convertir par un
 * nombre à virgule flottante marche sur les petits montants et dérive
 * sur les gros — `92233720.36854775` rend une unité de trop. Assez pour
 * produire une transaction dont les entrées ne couvrent pas les sorties.
 *
 * ─── LA PAGE ───────────────────────────────────────────────────────────
 *
 * Une adresse détenant 5 907 762 DOGE a rendu dix sorties de 0,01 — soit
 * 0,10 DOGE — sans que rien ne signale qu'il y avait une suite. Et
 * sochain rend EXACTEMENT les mêmes dix pour `page=1` et `page=2` : le
 * numéro est ignoré, en silence.
 *
 * C'est de là que vient la troisième condition d'arrêt, celle qu'aucune
 * documentation ne suggère : une pièce déjà vue.
 * ═══════════════════════════════════════════════════════════════════════
 */
class DogeLectureTest {

    // ─── Les montants ──────────────────────────────────────────────────

    @Test
    fun `un montant en DOGE devient des unites, sans flottant`() {
        assertEquals(1_000_000L, DogeLecture.unitesDepuisTexte("0.01000000"))
        assertEquals(590_776_289_812_679L, DogeLecture.unitesDepuisTexte("5907762.89812679"))
        assertEquals(1_000_000_000L, DogeLecture.unitesDepuisTexte("10"))
        assertEquals(0L, DogeLecture.unitesDepuisTexte("0"))
        assertEquals(110_000_000L, DogeLecture.unitesDepuisTexte("1.1"))
        assertEquals(1L, DogeLecture.unitesDepuisTexte("0.00000001"))
        assertEquals(50_000_000L, DogeLecture.unitesDepuisTexte(".5"))
    }

    /*
    LE CAS QUI JUSTIFIE TOUT CE FICHIER. `(float) 92233720.36854775 * 1e8`
    rend 9 223 372 036 854 776 — une unite de plus que la verite. La
    lecture en texte rend le bon nombre.
    */
    @Test
    fun `un gros montant ne derive pas d une unite`() {
        assertEquals(
            9_223_372_036_854_775L,
            DogeLecture.unitesDepuisTexte("92233720.36854775")
        )
    }

    @Test
    fun `les espaces et le plus sont toleres, le moins non`() {
        assertEquals(325_000_000L, DogeLecture.unitesDepuisTexte("  3.25  "))
        assertEquals(250_000_000L, DogeLecture.unitesDepuisTexte("+2.5"))
        assertNull("un montant negatif ne veut rien dire", DogeLecture.unitesDepuisTexte("-1"))
    }

    /*
    UNE REPONSE ILLISIBLE REND null, ELLE NE LEVE PAS. Un fournisseur
    distant qui abime une ligne ne doit pas faire tomber un ecran entier.
    */
    @Test
    fun `ce qui n est pas un montant rend null`() {
        assertNull(DogeLecture.unitesDepuisTexte(""))
        assertNull(DogeLecture.unitesDepuisTexte(null))
        assertNull(DogeLecture.unitesDepuisTexte("abc"))
        assertNull(DogeLecture.unitesDepuisTexte("1,5"))
    }

    /*
    NEUF DECIMALES NE SONT PAS ARRONDIES, ELLES SONT REFUSEES. Un service
    qui en rendrait neuf dirait quelque chose qu'on ne comprend pas, et
    tronquer reviendrait a decider a sa place.
    */
    @Test
    fun `plus de huit decimales est refuse, pas arrondi`() {
        assertNull(DogeLecture.unitesDepuisTexte("1.123456789"))
    }

    @Test
    fun `l affichage fait l aller-retour`() {
        assertEquals("0.01", DogeLecture.texteDepuisUnites(1_000_000L))
        assertEquals("1.0", DogeLecture.texteDepuisUnites(100_000_000L))
        assertEquals("5907762.89812679", DogeLecture.texteDepuisUnites(590_776_289_812_679L))
        assertEquals(
            590_776_289_812_679L,
            DogeLecture.unitesDepuisTexte(DogeLecture.texteDepuisUnites(590_776_289_812_679L))
        )
    }

    // ─── Le parcours des pages ─────────────────────────────────────────

    private fun piece(n: Int, unites: Long = 1_000_000L) =
        DogeLecture.PieceLue("%064x".format(n), 0, unites, 5_000_000L)

    @Test
    fun `trois pages pleines puis une courte sont toutes lues`() {
        val pages = mapOf(
            1 to (1..10).map { piece(it) },
            2 to (11..20).map { piece(it) },
            3 to (21..24).map { piece(it) },
        )
        val lues = DogeLecture.parcourir { n -> pages[n] ?: emptyList() }
        assertEquals(24, lues.size)
    }

    /*
    ═══════════════════════════════════════════════════════════════════
    LE CAS SOCHAIN, ET C'EST POUR LUI QUE LA GARDE EXISTE
    ═══════════════════════════════════════════════════════════════════

    Le service rend LA MEME page quel que soit le numero. Sans la garde
    sur les pieces deja vues, le parcours tournerait jusqu'au plafond et
    compterait vingt fois les memes dix pieces — un solde multiplie par
    vingt, et des entrees qui n'existent pas.
    */
    @Test
    fun `un service qui ignore le numero de page ne fait pas boucler`() {
        var appels = 0
        val memePage = (1..10).map { piece(it) }
        val lues = DogeLecture.parcourir { appels++; memePage }
        assertEquals("les dix pieces, pas deux cents", 10, lues.size)
        assertEquals("on s'arrete au second appel, pas au vingtieme", 2, appels)
    }

    @Test
    fun `un echec reseau rend ce qui a deja ete lu`() {
        val lues = DogeLecture.parcourir { n ->
            if (n == 1) (1..10).map { piece(it) } else null
        }
        assertEquals(10, lues.size)
    }

    @Test
    fun `une premiere page vide rend une liste vide`() {
        assertEquals(0, DogeLecture.parcourir { emptyList() }.size)
    }

    /*
    LE PLAFOND EXISTE POUR L'ENVOI : inutile de lire cinquante pages
    pour depenser deux pieces. Un portefeuille qui le ferait a chaque
    preparation d'envoi epuiserait le quota du fournisseur.
    */
    @Test
    fun `le plafond arrete le parcours des qu il y a de quoi payer`() {
        var appels = 0
        val lues = DogeLecture.parcourir(plafondUnites = 15_000_000L) { n ->
            appels++
            ((n - 1) * 10 + 1..n * 10).map { piece(it) }
        }
        assertEquals("deux pages suffisent a couvrir 15 pieces de 0,01", 2, appels)
        assertEquals(20, lues.size)
    }

    @Test
    fun `sans plafond on lit tout, dans la limite des pages`() {
        var appels = 0
        DogeLecture.parcourir(pagesMaximum = 3) { n ->
            appels++
            ((n - 1) * 10 + 1..n * 10).map { piece(it) }
        }
        assertEquals(3, appels)
    }
}
