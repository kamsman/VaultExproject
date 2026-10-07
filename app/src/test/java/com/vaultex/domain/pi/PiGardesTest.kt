package com.vaultex.domain.pi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ═══════════════════════════════════════════════════════════════════════
 * LES RÈGLES DU RÉSEAU PI QUI COÛTENT DE L'ARGENT QUAND ON LES RATE
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Ce ne sont pas des calculs décoratifs. Chacun de ceux qui suivent
 * décide d'un montant maximum envoyable, et s'il est trop GÉNÉREUX la
 * transaction part, le réseau la refuse, et les frais sont brûlés. Chaque
 * appui sur « Max » coûterait alors quelque chose, sans jamais rien
 * envoyer.
 *
 * C'est la seule partie de l'envoi Pi qui soit testable sans réseau ni
 * clé, et c'est aussi celle où une erreur passe le plus facilement
 * inaperçue : elle ne lève aucune exception, elle rend juste un nombre un
 * peu trop grand.
 * ═══════════════════════════════════════════════════════════════════════
 */
class PiGardesTest {

    private companion object {
        /** Un Pi en stroops : la réserve de base de la famille Stellar. */
        const val RESERVE = 10_000_000L
        const val FRAIS = 200L
        const val DIX_PI = 100_000_000L
    }

    private fun compte(
        solde: Long = DIX_PI,
        sequence: Long = 1L,
        annexes: Int = 0,
        parrainages: Int = 0,
        parraine: Int = 0,
        engage: Long = 0L
    ) = ComptePi(solde, sequence, annexes, parrainages, parraine, engage)

    /** Un compte ordinaire doit laisser DEUX parts de réserve. */
    @Test fun `la reserve d'un compte simple vaut deux parts`() {
        assertEquals(2 * RESERVE, compte().reserveStroops(RESERVE))
    }

    @Test fun `chaque entree annexe ajoute une part de reserve`() {
        assertEquals(3 * RESERVE, compte(annexes = 1).reserveStroops(RESERVE))
        assertEquals(5 * RESERVE, compte(annexes = 3).reserveStroops(RESERVE))
    }

    @Test fun `un parrainage accorde alourdit la reserve`() {
        assertEquals(3 * RESERVE, compte(parrainages = 1).reserveStroops(RESERVE))
    }

    @Test fun `un parrainage recu allege la reserve`() {
        assertEquals(
            2 * RESERVE,
            compte(annexes = 1, parraine = 1).reserveStroops(RESERVE)
        )
    }

    /**
     * La réserve ne descend JAMAIS sous deux parts.
     *
     * La formule du réseau peut donner un nombre de parts inférieur à deux
     * si les parrainages reçus dépassent les entrées annexes. Laisser le
     * calcul descendre produirait un disponible surévalué, et une
     * transaction refusée dont les frais sont brûlés. On plafonne par le
     * bas plutôt que de faire confiance à l'arithmétique.
     */
    @Test fun `la reserve ne descend jamais sous deux parts`() {
        assertEquals(2 * RESERVE, compte(parraine = 5).reserveStroops(RESERVE))
    }

    /**
     * CE QUI EST ENGAGÉ DANS UNE OFFRE DE VENTE N'EST PAS DISPONIBLE.
     *
     * VaultEx ne place aucune offre, donc ce champ vaut toujours zéro pour
     * un compte qu'elle seule utilise. Mais la même phrase secrète peut
     * servir dans une autre application, et l'ignorer donnerait un
     * disponible trop grand sur un compte parfaitement légitime.
     */
    @Test fun `le montant engage en vente sort du disponible`() {
        val engage = 30_000_000L
        assertEquals(
            2 * RESERVE + engage,
            compte(engage = engage).reserveStroops(RESERVE)
        )
    }

    @Test fun `le disponible retire la reserve et les frais`() {
        assertEquals(
            DIX_PI - 2 * RESERVE - FRAIS,
            compte().disponibleStroops(RESERVE, FRAIS)
        )
    }

    /**
     * Un disponible négatif vaut ZÉRO, jamais un nombre négatif.
     *
     * Un solde sous la réserve est un état courant : il suffit d'avoir
     * reçu exactement le minimum. Laisser filer un négatif le ferait
     * comparer à un montant saisi, et `montant > disponible` resterait
     * faux pour tout montant — la garde s'inverserait silencieusement et
     * laisserait passer n'importe quel envoi.
     */
    @Test fun `un solde sous la reserve donne zero disponible`() {
        assertEquals(0L, compte(solde = RESERVE).disponibleStroops(RESERVE, FRAIS))
        assertEquals(0L, compte(solde = 0L).disponibleStroops(RESERVE, FRAIS))
        assertEquals(
            0L,
            // Juste de quoi couvrir la réserve, mais pas les frais.
            compte(solde = 2 * RESERVE).disponibleStroops(RESERVE, FRAIS)
        )
    }

    /** Le disponible est exactement envoyable : ni un stroop de plus. */
    @Test fun `le disponible est envoyable au stroop pres`() {
        val dispo = compte().disponibleStroops(RESERVE, FRAIS)
        val soldeApres = DIX_PI - dispo - FRAIS
        assertEquals(
            "il doit rester exactement la réserve",
            compte().reserveStroops(RESERVE), soldeApres
        )
    }

    /*
    ───────────────────────────────────────────────────────────────────────
    LA LECTURE DES CODES DE REFUS D'HORIZON
    ───────────────────────────────────────────────────────────────────────

    Le point important n'est pas la traduction : c'est `estRejetDefinitif`,
    qui décide si l'on a le droit de reconstruire et renvoyer. Un faux
    positif ici fait payer deux fois.
    */

    @Test fun `un corps d'erreur Horizon est lu`() {
        val codes = PiCodesResultat.lire(
            """{"extras":{"result_codes":{"transaction":"tx_failed",
               "operations":["op_no_destination"]}}}"""
        )
        assertEquals("tx_failed", codes.transaction)
        assertEquals(listOf("op_no_destination"), codes.operations)
    }

    /** L'opération passe avant la transaction : `tx_failed` ne dit rien. */
    @Test fun `le message vient de l'operation et non de tx_failed`() {
        val codes = PiCodesResultat.Codes("tx_failed", listOf("op_no_destination"))
        assertTrue(PiCodesResultat.message(codes).contains("jamais reçu de Pi"))
    }

    /**
     * UN CORPS ILLISIBLE N'EST PAS UN REJET.
     *
     * C'est le test le plus important du fichier. Une passerelle qui
     * répond du HTML, un 502, un corps tronqué : la transaction a
     * peut-être été reçue et appliquée. Traiter cela comme un rejet
     * autoriserait à reconstruire et renvoyer — donc à débiter deux fois.
     *
     * La réponse prudente est « on ne sait pas », et c'est faux par
     * défaut.
     */
    @Test fun `un corps illisible n'autorise pas a renvoyer`() {
        assertFalse(PiCodesResultat.estRejetDefinitif(PiCodesResultat.lire(null)))
        assertFalse(PiCodesResultat.estRejetDefinitif(PiCodesResultat.lire("")))
        assertFalse(PiCodesResultat.estRejetDefinitif(PiCodesResultat.lire("<html>502</html>")))
        assertFalse(PiCodesResultat.estRejetDefinitif(PiCodesResultat.lire("{}")))
        assertFalse(
            PiCodesResultat.estRejetDefinitif(PiCodesResultat.lire("""{"extras":{}}"""))
        )
    }

    @Test fun `un code lisible autorise a renvoyer`() {
        assertTrue(
            PiCodesResultat.estRejetDefinitif(
                PiCodesResultat.lire("""{"extras":{"result_codes":{"transaction":"tx_bad_seq"}}}""")
            )
        )
    }

    /**
     * `tx_failed` A COÛTÉ LES FRAIS, les autres refus non.
     *
     * Les deux arrivent en 400. Annoncer « rien n'a bougé » sur un
     * `tx_failed` serait démenti par le solde quelques secondes plus tard.
     */
    @Test fun `seuls les refus apres inscription coutent les frais`() {
        assertTrue(PiCodesResultat.fraisPreleves(PiCodesResultat.Codes("tx_failed")))
        assertTrue(
            PiCodesResultat.fraisPreleves(
                PiCodesResultat.Codes("tx_failed", listOf("op_underfunded"))
            )
        )
        assertFalse(PiCodesResultat.fraisPreleves(PiCodesResultat.Codes("tx_bad_seq")))
        assertFalse(PiCodesResultat.fraisPreleves(PiCodesResultat.Codes("tx_bad_auth")))
        assertFalse(PiCodesResultat.fraisPreleves(PiCodesResultat.Codes("tx_too_late")))
    }

    @Test fun `seule une sequence depassee vaut un second essai`() {
        assertTrue(PiCodesResultat.vautUnSecondEssai(PiCodesResultat.Codes("tx_bad_seq")))
        assertFalse(PiCodesResultat.vautUnSecondEssai(PiCodesResultat.Codes("tx_bad_auth")))
        assertFalse(
            PiCodesResultat.vautUnSecondEssai(
                PiCodesResultat.Codes("tx_failed", listOf("op_no_destination"))
            )
        )
    }

    /** Un code inconnu reste cherchable plutôt que rassurant et faux. */
    @Test fun `un code inconnu est repris tel quel`() {
        assertTrue(
            PiCodesResultat.message(PiCodesResultat.Codes("tx_quelque_chose"))
                .contains("tx_quelque_chose")
        )
    }
}
