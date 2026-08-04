package com.vaultex.domain.usecase

import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests des gardes de validation de SendCryptoUseCase.
 *
 * Ces branches court-circuitent AVANT tout appel réseau ou accès au
 * stockage sécurisé : le montant est parsé d'abord (when), puis chaque
 * sendXxx valide l'adresse avant de toucher SecureStorage. Les mocks
 * (relaxed) ne sont donc jamais invoqués — résultats 100% déterministes.
 */
class SendCryptoUseCaseTest {

    private val useCase = SendCryptoUseCase(
        secureStorage = mockk(relaxed = true),
        evmTx = mockk(relaxed = true),
        btcTx = mockk(relaxed = true),
        solTx = mockk(relaxed = true),
        tronTx = mockk(relaxed = true),
        ethRpc = mockk(relaxed = true),
        bnbRpc = mockk(relaxed = true),
        bitcoinApi = mockk(relaxed = true),
        solanaRpc = mockk(relaxed = true),
        tronApi = mockk(relaxed = true)
    )

    private val validEvm = "0x9858EfFD232B4033E47d90003D41EC34EcaEda94"
    private val validTrx = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"
    private val invalidAddr = "0x123"

    private fun message(r: SendCryptoUseCase.Result): String {
        assertTrue("attendu Error, obtenu $r", r is SendCryptoUseCase.Result.Error)
        return (r as SendCryptoUseCase.Result.Error).message
    }

    // ─── Montant invalide (par chaîne) ───────────────────────────────

    @Test fun `eth montant invalide`() = runBlocking {
        assertEquals("Montant invalide", message(useCase.sendByChain("ETH", validEvm, "abc")))
    }

    @Test fun `bnb montant invalide`() = runBlocking {
        assertEquals("Montant invalide", message(useCase.sendByChain("BNB", validEvm, "")))
    }

    @Test fun `btc montant invalide`() = runBlocking {
        assertEquals("Montant invalide", message(useCase.sendByChain("BTC", "bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq", "x")))
    }

    @Test fun `trx montant invalide`() = runBlocking {
        assertEquals("Montant invalide", message(useCase.sendByChain("TRX", validTrx, "abc")))
    }

    @Test fun `sol montant invalide`() = runBlocking {
        assertEquals("Montant invalide", message(useCase.sendByChain("SOL", "11111111111111111111111111111111", "abc")))
    }

    @Test fun `usdt montant invalide`() = runBlocking {
        assertEquals("Montant invalide", message(useCase.sendByChain("USDT", validTrx, "abc")))
    }

    @Test fun `usdt-eth montant invalide`() = runBlocking {
        assertEquals("Montant invalide", message(useCase.sendByChain("USDT-ETH", validEvm, "abc")))
    }

    @Test fun `usdt-bnb montant invalide`() = runBlocking {
        assertEquals("Montant invalide", message(useCase.sendByChain("USDT-BNB", validEvm, "abc")))
    }

    // ─── Chaîne non supportée ────────────────────────────────────────

    @Test fun `chaine inconnue rejetee`() = runBlocking {
        assertEquals("Chain non supportée", message(useCase.sendByChain("DOGE", validEvm, "1.0")))
    }

    // ─── Adresse invalide (montant valide, court-circuit avant SecureStorage) ──

    @Test fun `eth adresse invalide`() = runBlocking {
        assertEquals(
            "Adresse ETH/BNB invalide (0x + 40 hex requis)",
            message(useCase.sendByChain("ETH", invalidAddr, "1.0"))
        )
    }

    @Test fun `btc adresse invalide`() = runBlocking {
        assertEquals("Adresse BTC invalide", message(useCase.sendByChain("BTC", invalidAddr, "0.01")))
    }

    @Test fun `trx adresse invalide`() = runBlocking {
        assertEquals(
            "Adresse TRX invalide (T + 34 caractères + checksum)",
            message(useCase.sendByChain("TRX", invalidAddr, "1.0"))
        )
    }

    @Test fun `sol adresse invalide`() = runBlocking {
        assertEquals("Adresse SOL invalide", message(useCase.sendByChain("SOL", invalidAddr, "1.0")))
    }

    @Test fun `usdt trc20 adresse invalide`() = runBlocking {
        assertEquals(
            "Adresse TRX invalide (T + 34 caractères + checksum)",
            message(useCase.sendByChain("USDT", invalidAddr, "1.0"))
        )
    }
}
