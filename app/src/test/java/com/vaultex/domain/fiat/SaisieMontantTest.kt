package com.vaultex.domain.fiat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/*
═══════════════════════════════════════════════════════════════════════════
LA SAISIE D'UN MONTANT, MISE À L'ÉPREUVE
═══════════════════════════════════════════════════════════════════════════

Ces tests décrivaient un pavé numérique construit dans l'application. Le
pavé a été retiré et le clavier du système est revenu — mais les RÈGLES
sont restées nécessaires, et ce sont elles qu'on éprouve ici.

Elles ne cassent pas l'affichage quand elles manquent : elles produisent un
NOMBRE DIFFÉRENT de celui que la personne croit avoir tapé, sur un écran où
elle envoie de l'argent.

Chaque cas ci-dessous est une frappe qu'un vrai pouce peut faire sur un
clavier Android — lequel accepte le point, plusieurs séparateurs, et autant
de décimales qu'on veut.
═══════════════════════════════════════════════════════════════════════════
*/
class SaisieMontantTest {

    /** Ce que le champ garde après chaque frappe, caractère par caractère. */
    private fun taper(suite: String, decimalesMax: Int = 6): String {
        var s = ""
        for (c in suite) s = SaisieMontant.filtrer(s + c, decimalesMax)
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
    fun `le point du clavier devient une virgule`() {
        /*
        Un clavier Android rend l'un ou l'autre selon la disposition et la
        langue. Deux écritures pour le même nombre finissent par diverger
        quelque part — ici, elles sont ramenées à une seule.
        */
        assertEquals("2,006866", taper("2.006866"))
    }

    @Test
    fun `un zero apres la virgule est conserve`() {
        // 2,006866 n'est pas 2,6866 : c'est le marquage d'une demande qui
        // se joue là.
        assertEquals("2,006866", taper("2,006866"))
    }

    @Test
    fun `une virgule seule commence par un zero`() {
        assertEquals("0,5", taper(",5"))
    }

    // ─── Ce qui doit être écarté ────────────────────────────────────

    @Test
    fun `une seconde virgule est ecartee`() {
        // « 1,2,3 » n'est pas un nombre, et sa lecture rendrait null —
        // donc un montant reçu qui disparaît sans explication.
        assertEquals("1,23", taper("1,2,3"))
    }

    @Test
    fun `pas plus de decimales que la monnaie n en a`() {
        /*
        On a déjà affiché « 8,19672131 USDT » dans ce projet : deux
        chiffres qui n'existent pas sur un jeton à six décimales, donc une
        somme que personne ne peut recevoir.

        C'est la garantie que le pavé numérique portait, et qu'un clavier
        système n'a pas. Elle est ici.
        */
        assertEquals("8,196721", taper("8,19672131", decimalesMax = 6))
    }

    @Test
    fun `le bitcoin en a huit`() {
        assertEquals("0,12345678", taper("0,123456789", decimalesMax = 8))
    }

    @Test
    fun `les francs CFA n ont aucune decimale`() {
        /*
        ═══════════════════════════════════════════════════════════════════
        LA VIRGULE EST GARDÉE, LES DÉCIMALES SONT ÉCARTÉES
        ═══════════════════════════════════════════════════════════════════

        J'avais d'abord écrit que le filtre rendrait « 5000 » : la virgule
        ignorée, et la suite avec. Ce test l'a démenti — il rendait
        « 500050 ». Cent fois trop.

        La raison tient à ce qu'est un filtre : il voit la CHAÎNE entière à
        chaque frappe, pas les touches. Une virgule écartée disparaît du
        texte gardé, et le chiffre suivant arrive dans une chaîne où plus
        rien ne dit qu'elle a existé.

        On garde donc la virgule et on borne les décimales à zéro.
        « 5000,50 » devient « 5000, », que `valeur` refuse de lire : le
        montant reçu affiche « — », et la personne voit tout de suite qu'il
        faut retirer la virgule.

        Visible et faux vaut toujours mieux qu'invisible et faux.
        ═══════════════════════════════════════════════════════════════════
        */
        assertEquals("5000,", taper("5000,50", decimalesMax = 0))
        // Et rien n'en est déduit, donc rien de faux ne s'affiche.
        assertNull(SaisieMontant.valeur("5000,"))
    }

    @Test
    fun `une lettre ne fait rien`() {
        // Un clavier décimal en laisse passer sur certaines dispositions,
        // et un collage en apporte toujours.
        assertEquals("1500", taper("1a5b0c0", decimalesMax = 0))
    }

    @Test
    fun `les espaces d un collage sont ecartes`() {
        /*
        « 5 000 » collé depuis un message : l'espace insécable étroite de
        NumberFormat ferait rendre null à toDoubleOrNull, et le montant
        reçu disparaîtrait sans que rien ne le dise.
        */
        assertEquals("5000", taper("5 000", decimalesMax = 0))
        assertEquals("5000", taper("5 000", decimalesMax = 0))
    }

    @Test
    fun `le signe moins est ecarte`() {
        assertEquals("500", taper("-500", decimalesMax = 0))
    }

    @Test
    fun `le zero de tete disparait des qu un chiffre arrive`() {
        // « 05 » ne s'écrit nulle part et laisse croire à une touche ratée.
        assertEquals("5", taper("05", decimalesMax = 0))
    }

    @Test
    fun `la partie entiere est bornee`() {
        // Une touche restée enfoncée ne doit pas produire un nombre que
        // Double ne tient plus.
        assertEquals(SaisieMontant.ENTIERS_MAX, taper("9".repeat(40), decimalesMax = 0).length)
    }

    @Test
    fun `effacer ramene a une saisie vide`() {
        assertEquals("", SaisieMontant.filtrer("", 6))
    }

    // ─── La lecture de la valeur ────────────────────────────────────

    @Test
    fun `une virgule en attente n est pas encore un nombre`() {
        /*
        « 0, » est un état de frappe. Le lire comme 0 ferait afficher un
        montant reçu à l'instant où l'on pose la virgule, et ce montant
        changerait à la frappe suivante.
        */
        assertNull(SaisieMontant.valeur("0,"))
        assertNull(SaisieMontant.valeur("2,"))
    }

    @Test
    fun `un champ vide n est pas un nombre`() {
        assertNull(SaisieMontant.valeur(""))
    }

    @Test
    fun `zero n est pas un montant`() {
        // Zéro est une saisie valide et une opération impossible.
        assertNull(SaisieMontant.valeur("0"))
    }

    @Test
    fun `la virgule francaise est lue comme un point`() {
        assertEquals(2.006866, SaisieMontant.valeur("2,006866")!!, 1e-9)
    }

    @Test
    fun `ce qui sort du filtre est toujours lisible`() {
        /*
        LE LIEN ENTRE LES DEUX FONCTIONS, ET C'EST LUI QUI COMPTE.

        Le filtre garde ce qu'on affiche ; `valeur` lit ce qu'on calcule.
        Si l'un laissait passer ce que l'autre refuse, l'écran montrerait
        un montant et n'en déduirait aucun — sans rien dire.
        */
        for (frappe in listOf("17590", "2,006866", "0,5", "8,19672131", "5000,50")) {
            val garde = taper(frappe, decimalesMax = 6)
            if (garde.isNotEmpty() && !garde.endsWith(SaisieMontant.VIRGULE)) {
                assertEquals(
                    "« $frappe » garde « $garde », qui doit rester lisible",
                    true,
                    SaisieMontant.valeur(garde) != null
                )
            }
        }
    }
}
