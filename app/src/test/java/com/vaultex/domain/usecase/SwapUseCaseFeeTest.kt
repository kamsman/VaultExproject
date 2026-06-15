package com.vaultex.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class SwapUseCaseFeeTest {

    @Test
    fun `applyFee preleve 1_5 pourcent et renvoie le net`() {
        val (fee, net) = SwapUseCase.applyFee(100.0)
        assertEquals(1.5, fee, 1e-9)
        assertEquals(98.5, net, 1e-9)
    }

    @Test
    fun `applyFee sur zero`() {
        val (fee, net) = SwapUseCase.applyFee(0.0)
        assertEquals(0.0, fee, 1e-9)
        assertEquals(0.0, net, 1e-9)
    }

    @Test
    fun `fee plus net egale montant initial`() {
        val amount = 1234.56
        val (fee, net) = SwapUseCase.applyFee(amount)
        assertEquals(amount, fee + net, 1e-6)
    }

    @Test
    fun `constantes de frais`() {
        assertEquals(1.5, SwapUseCase.VAULTEX_FEE_PERCENT, 1e-9)
        assertEquals(1.0, SwapUseCase.MOBILE_MONEY_FEE_PERCENT, 1e-9)
    }
}
