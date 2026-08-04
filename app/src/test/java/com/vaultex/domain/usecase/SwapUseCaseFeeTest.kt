package com.vaultex.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Frais de service prélevés sur un échange.
 *
 * Les tests de CALCUL dérivent leurs attentes de [SwapUseCase.VAULTEX_FEE_PERCENT]
 * au lieu de réécrire « 1,5 » à chaque ligne. Ils vérifient ainsi la LOGIQUE de
 * prélèvement, qui ne doit pas changer, sans casser le jour où le taux change —
 * ce qui est prévu (passage à 1 % après accord ChangeNOW). Une seule assertion
 * fixe la valeur commerciale du moment, et c'est volontairement la seule à
 * mettre à jour ce jour-là.
 *
 * (Ce fichier référençait `MOBILE_MONEY_FEE_PERCENT`, retirée avec l'écran
 * Mobile Money. Le code de test se compile séparément du code principal : la
 * rupture n'est apparue qu'à `compileDebugUnitTestKotlin`, bien après le
 * retrait.)
 */
class SwapUseCaseFeeTest {

    private val pct = SwapUseCase.VAULTEX_FEE_PERCENT

    @Test
    fun `applyFee preleve le pourcentage annonce et renvoie le net`() {
        val (fee, net) = SwapUseCase.applyFee(100.0)
        assertEquals(pct, fee, 1e-9)              // 100 × pct% = pct
        assertEquals(100.0 - pct, net, 1e-9)
    }

    @Test
    fun `applyFee sur zero`() {
        val (fee, net) = SwapUseCase.applyFee(0.0)
        assertEquals(0.0, fee, 1e-9)
        assertEquals(0.0, net, 1e-9)
    }

    @Test
    fun `fee plus net egale toujours le montant initial`() {
        // Invariant le plus important : aucune fraction ne doit se perdre ni
        // apparaître entre ce que l'utilisateur envoie et ce qui est prélevé.
        for (amount in listOf(0.0, 1.0, 1234.56, 0.00000001, 9_999_999.99)) {
            val (fee, net) = SwapUseCase.applyFee(amount)
            assertEquals("montant $amount", amount, fee + net, amount * 1e-12 + 1e-12)
        }
    }

    @Test
    fun `le taux reste dans une fourchette plausible`() {
        // Garde-fou contre une faute de frappe : un taux à 15 au lieu de 1,5
        // viderait 15 % de chaque échange sans qu'aucun autre test ne bronche.
        assertTrue("taux de frais aberrant : $pct", pct > 0.0 && pct <= 3.0)
    }

    @Test
    fun `taux commercial en vigueur`() {
        // SEULE assertion à mettre à jour lors d'un changement de tarif.
        assertEquals(1.5, SwapUseCase.VAULTEX_FEE_PERCENT, 1e-9)
    }
}
