package com.vaultex.domain.usecase

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests des gardes de validation de SendCryptoUseCase.
 *
 * Ces branches court-circuitent AVANT tout appel réseau ou accès au
 * stockage sécurisé. L'ordre réel dans sendByChain est :
 *
 *   1. `forbiddenDestination()` — destinations à perte certaine, en tout
 *      premier, parce qu'une adresse qui détruit les fonds est plus grave
 *      qu'un montant mal écrit et doit primer dans le message affiché ;
 *   2. le parsing du montant (when) ;
 *   3. la validation d'adresse dans chaque sendXxx, avant SecureStorage.
 *
 * Les mocks (relaxed) ne sont donc jamais invoqués — résultats 100%
 * déterministes, sans réseau.
 */
class SendCryptoUseCaseTest {

    // Nommés (et non anonymes) pour pouvoir vérifier qu'ils ne sont JAMAIS
    // sollicités : c'est ce qui prouve que les gardes tombent avant le
    // stockage sécurisé et avant le réseau.
    private val secureStorageMock: com.vaultex.core.security.SecureStorage = mockk(relaxed = true)
    private val tronApiMock: com.vaultex.data.remote.api.TronApi = mockk(relaxed = true)
    private val contextMock: android.content.Context = mockk(relaxed = true)

    private val useCase = SendCryptoUseCase(
        secureStorage = secureStorageMock,
        evmTx = mockk(relaxed = true),
        btcTx = mockk(relaxed = true),
        solTx = mockk(relaxed = true),
        tronTx = mockk(relaxed = true),
        ethRpc = mockk(relaxed = true),
        bnbRpc = mockk(relaxed = true),
        bitcoinApi = mockk(relaxed = true),
        solanaRpc = mockk(relaxed = true),
        tronApi = tronApiMock,
        appContext = contextMock
    )

    /*
    ─── POURQUOI CES TESTS NE COMPARENT PLUS DES PHRASES ──────────────────
    Les refus d'envoi sont passés dans les ressources (values/, values-en/,
    values-ar/) : les comparer au mot près reviendrait à figer la rédaction
    française et à faire échouer ces tests à la première reformulation, alors
    que ce qu'ils vérifient n'est pas le texte — c'est QUELLE garde tombe, et
    dans quel ordre.

    Le Context factice résout donc chaque identifiant de ressource en sa CLÉ
    (« @tx_err_montant_invalide »). La vérification reste exacte, et devient
    indépendante de la langue comme de la formulation.

    LocaleManager.wrap() est court-circuité parce qu'il touche Configuration
    et createConfigurationContext : des stubs d'android.jar, qui lèvent une
    exception dans un test JVM.
    ───────────────────────────────────────────────────────────────────────
     */
    private val clesParId: Map<Int, String> = mapOf(
        com.vaultex.R.string.tx_err_montant_invalide to "tx_err_montant_invalide",
        com.vaultex.R.string.tx_err_chaine_non_supportee to "tx_err_chaine_non_supportee",
        com.vaultex.R.string.tx_err_adresse_evm to "tx_err_adresse_evm",
        com.vaultex.R.string.tx_err_adresse_btc to "tx_err_adresse_btc",
        com.vaultex.R.string.tx_err_adresse_tron to "tx_err_adresse_tron",
        com.vaultex.R.string.tx_err_adresse_solana to "tx_err_adresse_solana",
        com.vaultex.R.string.tx_err_destination_interdite to "tx_err_destination_interdite",
        com.vaultex.R.string.tx_err_wallet_absent to "tx_err_wallet_absent",
        com.vaultex.R.string.tx_err_token_perso_invalide to "tx_err_token_perso_invalide",
        com.vaultex.R.string.tx_err_decimales_invalides to "tx_err_decimales_invalides"
    )

    private fun cle(id: Int): String = "@" + (clesParId[id] ?: "id_inconnu_$id")

    @Before fun ressourcesFactices() {
        mockkObject(com.vaultex.core.session.LocaleManager)
        every { com.vaultex.core.session.LocaleManager.wrap(any()) } returns contextMock
        every { contextMock.getString(any()) } answers { cle(firstArg<Int>()) }
        every { contextMock.getString(any(), *anyVararg()) } answers { cle(firstArg<Int>()) }
    }

    @After fun rendreLocaleManager() {
        unmockkObject(com.vaultex.core.session.LocaleManager)
    }

    private val validEvm = "0x9858EfFD232B4033E47d90003D41EC34EcaEda94"

    /**
     * Adresse Tron de compte ordinaire (base58check valide, checksum correct).
     *
     * ATTENTION : ne PAS remettre ici `TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t`.
     * C'est l'adresse du CONTRAT USDT TRC20, que `forbiddenDestination()`
     * refuse à juste titre — y envoyer des fonds les perdrait définitivement.
     * Elle a servi de fixture « adresse valide » et faisait échouer ces tests
     * en déclenchant le garde avant le contrôle du montant.
     */
    private val validTrx = "TWFtK7tZhoE4k7WTViiEVbYpo9R7bZb8XN"
    private val invalidAddr = "0x123"

    private fun message(r: SendCryptoUseCase.Result): String {
        assertTrue("attendu Error, obtenu $r", r is SendCryptoUseCase.Result.Error)
        return (r as SendCryptoUseCase.Result.Error).message
    }

    // ─── Montant invalide (par chaîne) ───────────────────────────────

    @Test fun `eth montant invalide`() = runBlocking {
        assertEquals("@tx_err_montant_invalide", message(useCase.sendByChain("ETH", validEvm, "abc")))
    }

    @Test fun `bnb montant invalide`() = runBlocking {
        assertEquals("@tx_err_montant_invalide", message(useCase.sendByChain("BNB", validEvm, "")))
    }

    @Test fun `btc montant invalide`() = runBlocking {
        assertEquals("@tx_err_montant_invalide", message(useCase.sendByChain("BTC", "bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq", "x")))
    }

    @Test fun `trx montant invalide`() = runBlocking {
        assertEquals("@tx_err_montant_invalide", message(useCase.sendByChain("TRX", validTrx, "abc")))
    }

    @Test fun `sol montant invalide`() = runBlocking {
        assertEquals("@tx_err_montant_invalide", message(useCase.sendByChain("SOL", "11111111111111111111111111111111", "abc")))
    }

    @Test fun `usdt montant invalide`() = runBlocking {
        assertEquals("@tx_err_montant_invalide", message(useCase.sendByChain("USDT", validTrx, "abc")))
    }

    @Test fun `usdt-eth montant invalide`() = runBlocking {
        assertEquals("@tx_err_montant_invalide", message(useCase.sendByChain("USDT-ETH", validEvm, "abc")))
    }

    @Test fun `usdt-bnb montant invalide`() = runBlocking {
        assertEquals("@tx_err_montant_invalide", message(useCase.sendByChain("USDT-BNB", validEvm, "abc")))
    }

    // ─── Chaîne non supportée ────────────────────────────────────────

    @Test fun `chaine inconnue rejetee`() = runBlocking {
        assertEquals("@tx_err_chaine_non_supportee", message(useCase.sendByChain("DOGE", validEvm, "1.0")))
    }

    // ─── Adresse invalide (montant valide, court-circuit avant SecureStorage) ──

    @Test fun `eth adresse invalide`() = runBlocking {
        assertEquals(
            "@tx_err_adresse_evm",
            message(useCase.sendByChain("ETH", invalidAddr, "1.0"))
        )
    }

    @Test fun `btc adresse invalide`() = runBlocking {
        assertEquals("@tx_err_adresse_btc", message(useCase.sendByChain("BTC", invalidAddr, "0.01")))
    }

    @Test fun `trx adresse invalide`() = runBlocking {
        assertEquals(
            "@tx_err_adresse_tron",
            message(useCase.sendByChain("TRX", invalidAddr, "1.0"))
        )
    }

    @Test fun `sol adresse invalide`() = runBlocking {
        assertEquals("@tx_err_adresse_solana", message(useCase.sendByChain("SOL", invalidAddr, "1.0")))
    }

    @Test fun `usdt trc20 adresse invalide`() = runBlocking {
        assertEquals(
            "@tx_err_adresse_tron",
            message(useCase.sendByChain("USDT", invalidAddr, "1.0"))
        )
    }

    // ─── Destinations à perte certaine ───────────────────────────────
    //
    // Ces adresses sont syntaxiquement valides : elles franchissent
    // AddressValidator sans problème. Seul `forbiddenDestination()` les
    // arrête. Sans ces tests, une régression du garde passerait inaperçue
    // jusqu'à ce qu'un utilisateur perde des fonds.

    private val burnAddresses = mapOf(
        "contrat USDT TRC20" to ("USDT" to "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"),
        "contrat USDT ERC20" to ("USDT-ETH" to "0xdAC17F958D2ee523a2206206994597C13D831ec7"),
        "contrat USDT BEP20" to ("USDT-BNB" to "0x55d398326f99059fF775485246999027B3197955"),
        "adresse nulle"      to ("ETH" to "0x0000000000000000000000000000000000000000"),
        "adresse dead"       to ("ETH" to "0x000000000000000000000000000000000000dEaD")
    )

    @Test fun `destinations a perte certaine refusees`() = runBlocking {
        burnAddresses.forEach { (nom, cible) ->
            val (chain, addr) = cible
            val msg = message(useCase.sendByChain(chain, addr, "1.0"))
            assertTrue(
                "$nom ($addr) aurait dû être refusée, message obtenu : $msg",
                msg == "@tx_err_destination_interdite"
            )
        }
    }

    /** Le garde doit être insensible à la casse : le hex EVM s'écrit des deux façons. */
    @Test fun `destination interdite detectee quelle que soit la casse`() = runBlocking {
        val msg = message(useCase.sendByChain("USDT-ETH", "0xDAC17F958D2EE523A2206206994597C13D831EC7", "1.0"))
        assertEquals("la casse ne doit pas contourner le garde", "@tx_err_destination_interdite", msg)
    }

    /** Le garde s'applique aussi aux tokens personnalisés (chemin "ERC20:"). */
    @Test fun `token personnalise vers adresse interdite refuse`() = runBlocking {
        val msg = message(useCase.sendByChain("ERC20:ETH:0xabc:18", "0x000000000000000000000000000000000000dEaD", "1.0"))
        assertEquals("le chemin ERC20: doit passer par le même garde", "@tx_err_destination_interdite", msg)
    }

    /** Une adresse ordinaire ne doit évidemment PAS être bloquée par le garde. */
    @Test fun `adresse ordinaire non bloquee par le garde`() = runBlocking {
        val msg = message(useCase.sendByChain("TRX", validTrx, "abc"))
        assertEquals("@tx_err_montant_invalide", msg)
    }

    // ─── Montants nuls ou négatifs ───────────────────────────────────
    //
    // Un envoi de 0 USDT brûlerait ~15 TRX de frais réseau pour ne rien
    // transférer. Le garde doit tomber avant tout appel réseau.

    @Test fun `usdt montant zero refuse`() = runBlocking {
        assertEquals("@tx_err_montant_invalide", message(useCase.sendByChain("USDT", validTrx, "0")))
    }

    @Test fun `usdt montant negatif refuse`() = runBlocking {
        assertEquals("@tx_err_montant_invalide", message(useCase.sendByChain("USDT", validTrx, "-5")))
    }

    /**
     * Le montant doit être validé AVANT le stockage sécurisé et le réseau.
     *
     * Ce test verrouille un ordre qui avait été inversé : `sendUsdtTrc20`
     * parsait le montant tout en bas, après avoir déchiffré le mnémonique et
     * appelé TronGrid deux fois. Un montant mal écrit consommait donc du quota
     * API et renvoyait « adresse jamais activée » — un message sans rapport
     * avec l'erreur réelle de l'utilisateur.
     */
    @Test fun `usdt montant invalide ne declenche aucun appel reseau`() = runBlocking {
        assertEquals("@tx_err_montant_invalide", message(useCase.sendByChain("USDT", validTrx, "abc")))
        coVerify(exactly = 0) { tronApiMock.getAccount(any()) }
        verify(exactly = 0) { secureStorageMock.getMnemonic() }
    }
}
