package com.vaultex.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ═══════════════════════════════════════════════════════════════════════
 * UN PRIX NE S'ARRONDIT PAS À L'ENTIER
 * ═══════════════════════════════════════════════════════════════════════
 *
 * CONSTATÉ SUR UNE CAPTURE de la fiche Dogecoin : l'axe vertical du
 * graphique affichait cinq fois « 0 » pour un cours de 0,0592 $. Il
 * passait par le formateur des CAPITALISATIONS, dont la dernière branche
 * arrondit à l'entier — juste pour « 9Md », faux pour un prix.
 *
 * ET FAUX EN SILENCE : un axe gradué de zéros ressemble à un graphique
 * en panne, pas à une erreur de format. Rien ne le signalait, et toutes
 * les monnaies sous un dollar étaient concernées — DOGE, TRX, et tout le
 * bas du marché.
 *
 * Le commentaire de `formatPrice` décrivait DÉJÀ ce défaut sur SHIB. La
 * règle existait ; l'axe ne l'employait simplement pas.
 *
 * ─── CE QUE CES CAS VERROUILLENT ───────────────────────────────────────
 *
 * Que la règle reste celle-là : quatre chiffres significatifs sous 0,01,
 * quatre décimales entre 0,01 et 1, deux au-dessus. Une « simplification »
 * qui ramènerait tout à deux décimales ferait réapparaître « 0 » sur la
 * moitié du marché, et personne ne le verrait avant la prochaine capture.
 *
 * ─── POURQUOI PAS DE GRAND NOMBRE ICI ──────────────────────────────────
 *
 * Le séparateur de milliers du français change selon la version du JDK —
 * espace ordinaire ou espace insécable étroite. Un test qui le figerait
 * échouerait un jour sans qu'aucun défaut existe. On vérifie donc les
 * grandes valeurs par leurs décimales, pas par leur texte exact.
 * ═══════════════════════════════════════════════════════════════════════
 */
class CurrencyFormatPrixTest {

    /*
    LE CAS DE LA CAPTURE, EN PREMIER. 0,0592 est le cours du Dogecoin au
    moment ou le defaut a ete vu.
    */
    @Test
    fun `le cours du Dogecoin ne devient pas zero`() {
        val s = CurrencyFormat.nombrePrix(0.0592)
        assertFalse("un prix de 0,0592 ne doit JAMAIS s'afficher « 0 »", s == "0")
        assertEquals("0,0592", s)
    }

    @Test
    fun `sous un centime on garde quatre chiffres significatifs`() {
        // Le cas SHIB, decrit dans le commentaire de formatPrice.
        assertEquals("0,00001234", CurrencyFormat.nombrePrix(0.00001234))
        assertTrue(
            "PEPE ne doit pas non plus s'afficher zero",
            CurrencyFormat.nombrePrix(0.0000001234) != "0"
        )
    }

    @Test
    fun `entre un centime et un dollar on garde quatre decimales`() {
        assertEquals("0,0592", CurrencyFormat.nombrePrix(0.0592))
        assertEquals("0,01", CurrencyFormat.nombrePrix(0.01))
        assertEquals("0,9999", CurrencyFormat.nombrePrix(0.9999))
    }

    @Test
    fun `au-dessus d un dollar deux decimales suffisent`() {
        assertEquals("1", CurrencyFormat.nombrePrix(1.0))
        assertEquals("1,5", CurrencyFormat.nombrePrix(1.5))
        assertEquals("2,34", CurrencyFormat.nombrePrix(2.3449))
    }

    /*
    LES GRANDES VALEURS SONT VERIFIEES PAR LEURS DECIMALES, pas par leur
    texte : le separateur de milliers varie selon le JDK.
    */
    @Test
    fun `un grand prix ne porte pas plus de deux decimales`() {
        val s = CurrencyFormat.nombrePrix(64030.5678)
        val decimales = s.substringAfter(',', "").length
        assertTrue("attendu au plus 2 decimales, recu « $s »", decimales <= 2)
        assertTrue("les chiffres doivent y etre", s.replace(Regex("[^0-9]"), "").startsWith("64030"))
    }

    @Test
    fun `zero reste zero`() {
        assertEquals("0", CurrencyFormat.nombrePrix(0.0))
    }

    /*
    LE SYMBOLE MONETAIRE RESTE L'AFFAIRE DE formatPrice. Si nombrePrix se
    mettait a en ajouter un, l'axe du graphique porterait cinq dollars de
    plus, et formatPrice en afficherait deux.
    */
    @Test
    fun `nombrePrix ne porte aucun symbole`() {
        for (v in listOf(0.0592, 1.5, 64030.0, 0.00001234)) {
            val s = CurrencyFormat.nombrePrix(v)
            assertFalse("« $s » ne doit pas porter de symbole", s.contains("$"))
            assertFalse("« $s » ne doit pas porter de symbole", s.contains("FCFA"))
            assertFalse("« $s » ne doit pas porter de symbole", s.contains("€"))
        }
    }

    /*
    ET formatPrice CONTINUE DE L'AJOUTER. L'extraction de nombrePrix ne
    devait rien changer pour ses appelants — c'est ce que verifie ce cas.
    */
    @Test
    fun `formatPrice garde ses symboles apres extraction`() {
        assertEquals("$0,0592", CurrencyFormat.formatPrice(0.0592, "USD"))
        assertEquals("0,0592 €", CurrencyFormat.formatPrice(0.0592, "EUR"))
        assertTrue(CurrencyFormat.formatPrice(1500.0, "XOF").endsWith(" FCFA"))
    }
}
