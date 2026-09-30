package com.vaultex.core.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Contrat du tampon qui porte une alerte de prix jusqu'à l'écran d'échange.
 *
 * Trois propriétés, et chacune correspond à un défaut possible visible par
 * l'utilisateur :
 *
 * · une suggestion se consomme UNE fois — sinon l'écran d'échange se
 *   repositionnerait à chaque retour sur l'accueil, en écrasant la paire que
 *   l'utilisateur vient de choisir à la main ;
 * · un symbole vide ne pose rien — mieux vaut ouvrir l'accueil que le
 *   formulaire d'échange sur une monnaie inconnue ;
 * · le sens survit au trajet, sans quoi l'alerte proposerait d'acheter quand
 *   elle voulait dire vendre.
 */
class AlerteSwapBufferTest {

    // Le tampon est un objet unique, partagé par tous les tests du module :
    // sans cette remise à zéro, l'ordre d'exécution déciderait du résultat.
    @Before fun vider() { AlerteSwapBuffer.consume() }

    @Test fun `une suggestion se consomme une seule fois`() {
        AlerteSwapBuffer.offer("BTC", vente = true)
        assertTrue(AlerteSwapBuffer.hasPending())

        val premiere = AlerteSwapBuffer.consume()
        assertEquals("BTC", premiere?.symbole)

        assertFalse(AlerteSwapBuffer.hasPending())
        assertNull(AlerteSwapBuffer.consume())
    }

    @Test fun `le sens est conserve`() {
        AlerteSwapBuffer.offer("ETH", vente = true)
        assertEquals(SensEchange.VENTE, AlerteSwapBuffer.consume()?.sens)

        AlerteSwapBuffer.offer("ETH", vente = false)
        assertEquals(SensEchange.ACHAT, AlerteSwapBuffer.consume()?.sens)
    }

    @Test fun `le symbole est normalise en majuscules`() {
        AlerteSwapBuffer.offer("  btc  ", vente = false)
        assertEquals("BTC", AlerteSwapBuffer.consume()?.symbole)
    }

    @Test fun `un symbole vide ou nul ne pose rien`() {
        AlerteSwapBuffer.offer(null, vente = true)
        assertFalse(AlerteSwapBuffer.hasPending())

        AlerteSwapBuffer.offer("   ", vente = true)
        assertFalse(AlerteSwapBuffer.hasPending())
    }

    /**
     * Une seconde alerte remplace la première.
     *
     * C'est le bon choix : deux notifications en attente, la plus récente est
     * celle que l'utilisateur vient de toucher. Empiler mènerait à ouvrir
     * l'échange sur la monnaie de l'alerte précédente.
     */
    @Test fun `la derniere suggestion remplace la precedente`() {
        AlerteSwapBuffer.offer("BTC", vente = true)
        AlerteSwapBuffer.offer("SOL", vente = false)

        val restante = AlerteSwapBuffer.consume()
        assertEquals("SOL", restante?.symbole)
        assertEquals(SensEchange.ACHAT, restante?.sens)
    }
}
