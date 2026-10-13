package com.vaultex.domain.fiat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/*
═══════════════════════════════════════════════════════════════════════════
LE PAVÉ, MIS À L'ÉPREUVE
═══════════════════════════════════════════════════════════════════════════

En écrivant notre propre clavier, on hérite de tout ce que le clavier
système faisait sans qu'on y pense. Ces cas-là ne cassent pas l'affichage :
ils produisent un NOMBRE DIFFÉRENT de celui que la personne croit avoir
tapé, sur un écran où elle envoie de l'argent.

Chaque test ci-dessous décrit une frappe qu'un vrai pouce peut faire.
═══════════════════════════════════════════════════════════════════════════
*/
class PaveNumeriqueTest {

    private fun taper(suite: String, decimalesMax: Int = 6): String {
        var s = ""
        for (c in suite) {
            s = PaveNumerique.appliquer(
                s,
                when (c) {
                    ',' -> ToucheP.Virgule
                    '<' -> ToucheP.Effacer
                    'C' -> ToucheP.Vider
                    else -> ToucheP.Chiffre(c - '0')
                },
                decimalesMax
            )
        }
        return s
    }

    // ─── Ce qui doit marcher ────────────────────────────────────────

    @Test
    fun `une frappe ordinaire donne le nombre tape`() {
        assertEquals("17590", taper("17590", decimalesMax = 0))
    }

    @Test
    fun `la virgule et les decimales passent`() {
        assertEquals("2,006866", taper("2,006866"))
    }

    @Test
    fun `effacer retire un seul caractere`() {
        assertEquals("175", taper("17590<<", decimalesMax = 0))
    }

    @Test
    fun `vider rend le champ vide`() {
        assertEquals("", taper("17590C", decimalesMax = 0))
    }

    @Test
    fun `on peut effacer la virgule et continuer`() {
        // « 2, » puis effacer, puis 5 : on doit obtenir « 25 », et non
        // rester coincé dans une partie décimale qui n'existe plus.
        assertEquals("25", taper("2,<5"))
    }

    // ─── Ce qui doit être refusé ────────────────────────────────────

    @Test
    fun `une seconde virgule est refusee`() {
        // « 1,2,3 » n'est pas un nombre, et toDoubleOrNull rendrait null —
        // donc un montant reçu qui disparaît sans explication.
        assertEquals("1,23", taper("1,2,3"))
    }

    @Test
    fun `pas plus de decimales que la monnaie n en a`() {
        /*
        On a déjà affiché « 8,19672131 USDT » dans ce projet : deux
        chiffres qui n'existent pas sur un jeton à six décimales, donc une
        somme que personne ne peut recevoir.
        */
        assertEquals("8,196721", taper("8,19672131", decimalesMax = 6))
    }

    @Test
    fun `les francs CFA n ont pas de virgule`() {
        // Le franc CFA n'a pas de centimes : la touche ne fait rien.
        assertEquals("5000", PaveNumerique.appliquer("5000", ToucheP.Virgule, decimalesMax = 0))
        assertEquals("", PaveNumerique.appliquer("", ToucheP.Virgule, decimalesMax = 0))
    }

    @Test
    fun `une virgule sans effet ne doit JAMAIS etre atteignable en francs`() {
        /*
        ═════════════════════════════════════════════════════════════════
        L'ARÊTE QUE CE TEST EXISTE POUR NOMMER
        ═════════════════════════════════════════════════════════════════

        J'avais d'abord écrit ici que « 5000,50 » tapé en francs donnerait
        « 5000 ». C'est faux, et la vérification l'a montré : la virgule
        est ignorée, puis les deux chiffres suivants s'ajoutent à la
        partie ENTIÈRE.

            5 0 0 0 , 5 0   →   500050

        Cinq cent mille francs au lieu de cinq mille. Un facteur cent, sur
        l'écran où l'on envoie de l'argent.

        Aucune règle de ce fichier ne peut l'empêcher : appuyer sur une
        touche sans effet puis continuer à taper des chiffres EST une
        frappe cohérente, et « 500050 » en est le résultat correct.

        La protection est donc dans l'écran, et elle est unique : la
        touche virgule est DÉSACTIVÉE quand la monnaie n'a pas de
        décimales. Ce test fige le comportement pour que personne ne la
        réactive en croyant corriger une gêne.
        ═════════════════════════════════════════════════════════════════
        */
        assertEquals("500050", taper("5000,50", decimalesMax = 0))
    }

    @Test
    fun `le zero de tete disparait des qu un chiffre arrive`() {
        // « 05 » ne s'écrit nulle part et laisse croire à une touche ratée.
        assertEquals("5", taper("05", decimalesMax = 0))
    }

    @Test
    fun `un zero apres la virgule est conserve`() {
        // Ici le zéro compte : 2,006866 n'est pas 2,6866. C'est exactement
        // le marquage d'une demande qui se jouerait là.
        assertEquals("2,006866", taper("2,006866"))
    }

    @Test
    fun `une virgule seule commence par un zero`() {
        assertEquals("0,5", taper(",5"))
    }

    @Test
    fun `la partie entiere est bornee`() {
        // Un appui resté enfoncé ne doit pas produire un nombre que
        // Double ne tient plus.
        val s = taper("9".repeat(40), decimalesMax = 0)
        assertEquals(PaveNumerique.ENTIERS_MAX, s.length)
    }

    @Test
    fun `effacer sur un champ vide ne plante pas`() {
        assertEquals("", taper("<<<", decimalesMax = 0))
    }

    // ─── La lecture de la valeur ────────────────────────────────────

    @Test
    fun `une virgule en attente n est pas encore un nombre`() {
        /*
        « 0, » est un état de frappe. Le lire comme 0 ferait afficher un
        montant reçu à l'instant où l'on pose la virgule, et ce montant
        changerait à la frappe suivante.
        */
        assertNull(PaveNumerique.valeur("0,"))
        assertNull(PaveNumerique.valeur("2,"))
    }

    @Test
    fun `un champ vide n est pas un nombre`() {
        assertNull(PaveNumerique.valeur(""))
    }

    @Test
    fun `zero n est pas un montant`() {
        // Zéro est une saisie valide et une opération impossible.
        assertNull(PaveNumerique.valeur("0"))
    }

    @Test
    fun `la virgue francaise est lue comme un point`() {
        assertEquals(2.006866, PaveNumerique.valeur("2,006866")!!, 1e-9)
    }

    @Test
    fun `aucune espace ne se glisse dans la saisie`() {
        /*
        C'EST LA RAISON POUR LAQUELLE LE PAVÉ NE MET PAS EN FORME.

        `NumberFormat` français sépare les milliers par une espace
        insécable étroite (U+202F) selon la version d'Android, et
        `"5 000".toDouble()` rend alors null. Un montant qui se réaffiche
        tel qu'il a été tapé ne peut pas être victime de ça.
        */
        val s = taper("17590", decimalesMax = 0)
        assertEquals(17590.0, PaveNumerique.valeur(s)!!, 1e-9)
        assertEquals(false, s.any { it.isWhitespace() || it == ' ' || it == ' ' })
    }
}
