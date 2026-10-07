package com.vaultex.domain.swap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ═══════════════════════════════════════════════════════════════════════
 * LE REGISTRE DE L'ÉCHANGE SUR PLACE
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Trois choses y décident de ce qui part et de combien. Aucune ne lève
 * d'erreur quand elle est fausse : elles produisent un montant, un refus
 * ou une autorisation — simplement pas celui qu'on croyait.
 * ═══════════════════════════════════════════════════════════════════════
 */
class ActifsBnbChainTest {

    /**
     * LE TEST QUI AURAIT ATTRAPÉ LE DÉFAUT.
     *
     * AutorisationSurPlace reconnaissait la monnaie native en vérifiant que
     * son adresse n'avait PAS la forme d'une adresse : pas de « 0x », ou
     * pas quarante-deux caractères.
     *
     * Le marqueur de 1inch a exactement cette forme. Le test ne s'est donc
     * jamais déclenché, et un échange partant du BNB allait demander
     * l'autorisation d'un contrat qui n'existe pas. Il échouait sur un
     * message accusant le service, alors que la faute était chez nous.
     *
     * Les deux assertions qui suivent disent précisément pourquoi la
     * reconnaissance par la forme ne pouvait pas marcher.
     */
    @Test fun `le marqueur natif a la forme d'une adresse ordinaire`() {
        val natif = ActifsBnbChain.de("BNB")!!.adresse
        assertTrue("il commence par 0x", natif.startsWith("0x"))
        assertEquals("il fait 42 caractères", 42, natif.length)
        // Et pourtant il EST natif : seule la comparaison au marqueur le dit.
        assertTrue(ActifsBnbChain.estNatif(natif))
    }

    @Test fun `un contrat de jeton n'est pas natif`() {
        val usdt = ActifsBnbChain.de("USDT-BNB")!!.adresse
        assertFalse(ActifsBnbChain.estNatif(usdt))
    }

    /** La casse d'une adresse EVM n'a aucune valeur sémantique. */
    @Test fun `la nativite ne depend pas de la casse`() {
        val natif = ActifsBnbChain.de("BNB")!!.adresse
        assertTrue(ActifsBnbChain.estNatif(natif.lowercase()))
        assertTrue(ActifsBnbChain.estNatif(natif.uppercase()))
    }

    /*
    ───────────────────────────────────────────────────────────────────────
    LE CÔTÉ DOLLAR, QUI BORNE LE PLAFOND D'ESSAI
    ───────────────────────────────────────────────────────────────────────

    Le plafond vaut 2 DOLLARS. Il était comparé à un nombre de jetons :
    juste pour l'USDT, six cents fois trop large pour le BNB. Savoir quel
    côté est le dollar est ce qui permet de comparer ce qui est comparable.
    */
    @Test fun `l'USDT est le cote dollar, le BNB non`() {
        assertTrue(ActifsBnbChain.estDollar("USDT-BNB"))
        assertTrue(ActifsBnbChain.estDollar("usdt-bnb"))
        assertFalse(ActifsBnbChain.estDollar("BNB"))
        assertFalse(ActifsBnbChain.estDollar("bnb"))
    }

    /**
     * TOUTE PAIRE VALIDE A EXACTEMENT UN CÔTÉ DOLLAR.
     *
     * C'est ce qui rend le plafond calculable sans demander de cours à qui
     * que ce soit. Si une troisième monnaie entrait au registre — un CAKE,
     * un WBNB — cette propriété tomberait, et avec elle le raisonnement du
     * plafond dans EchangeSurPlaceUseCase. Ce test le signalerait.
     */
    @Test fun `toute paire du registre a un cote dollar`() {
        val cles = listOf("BNB", "USDT-BNB")
        for (de in cles) for (vers in cles) {
            if (de == vers) continue
            assertTrue(
                "la paire $de → $vers n'a aucun côté en dollars",
                ActifsBnbChain.estDollar(de) || ActifsBnbChain.estDollar(vers)
            )
        }
    }

    /*
    ───────────────────────────────────────────────────────────────────────
    LES DÉCIMALES
    ───────────────────────────────────────────────────────────────────────

    Une erreur ici décale tout montant d'un facteur mille milliards, sans
    lever la moindre exception. L'USDT de BNB Chain en a DIX-HUIT, là où
    celui d'Ethereum et de Tron en ont six — c'est le piège classique de
    cette chaîne.
    */
    @Test fun `l'USDT de BNB Chain a dix-huit decimales`() {
        assertEquals(18, ActifsBnbChain.de("USDT-BNB")!!.decimales)
    }

    @Test fun `le BNB a dix-huit decimales`() {
        assertEquals(18, ActifsBnbChain.de("BNB")!!.decimales)
    }

    @Test fun `le contrat USDT est celui des envois`() {
        // Deux tables qui divergeraient enverraient les fonds ailleurs.
        assertEquals(
            com.vaultex.domain.usecase.SendCryptoUseCase.USDT_BEP20_CONTRACT,
            ActifsBnbChain.de("USDT-BNB")!!.adresse
        )
    }

    @Test fun `une monnaie hors perimetre est refusee`() {
        assertNull(ActifsBnbChain.de("USDT-ETH"))
        assertNull(ActifsBnbChain.de("BTC"))
        assertNull(ActifsBnbChain.de("SOL"))
        assertNull(ActifsBnbChain.de(""))
    }

    @Test fun `la recherche ignore la casse`() {
        assertEquals(ActifsBnbChain.de("BNB"), ActifsBnbChain.de("bnb"))
        assertEquals(ActifsBnbChain.de("USDT-BNB"), ActifsBnbChain.de("usdt-bnb"))
    }

    /** BNB Chain, et pas une autre : 56. */
    @Test fun `l'identifiant de chaine est celui de BNB Chain`() {
        assertEquals(56L, ActifsBnbChain.CHAIN_ID)
    }
}
