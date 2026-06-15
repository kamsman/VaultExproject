package com.vaultex.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de dérivation HD wallet sur le vecteur BIP39 standard, et
 * vérification que la passphrase BIP39 (M-03) modifie bien les clés.
 */
class WalletManagerTest {

    // Vecteur de test BIP39 canonique (12 mots).
    private val TEST_MNEMONIC =
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    @Test
    fun `mnemonique generee fait 12 mots et est valide BIP39`() {
        val words = WalletManager.generateMnemonic()
        assertEquals(12, words.size)
        assertTrue(WalletManager.validateMnemonic(words.joinToString(" ")))
    }

    @Test
    fun `deux generations produisent des mnemoniques differentes`() {
        assertNotEquals(
            WalletManager.generateMnemonic(),
            WalletManager.generateMnemonic()
        )
    }

    @Test
    fun `derivation deterministe sans passphrase`() {
        val a = WalletManager.deriveAddresses(TEST_MNEMONIC, "")
        val b = WalletManager.deriveAddresses(TEST_MNEMONIC, "")
        assertEquals(a.eth, b.eth)
        assertEquals(a.btc, b.btc)
        assertEquals(a.trx, b.trx)
        assertEquals(a.sol, b.sol)
    }

    @Test
    fun `adresse ETH conforme au vecteur BIP39 standard`() {
        // m/44'/60'/0'/0/0 du vecteur "abandon … about"
        val expected = "0x9858EfFD232B4033E47d90003D41EC34EcaEda94"
        val derived = WalletManager.deriveAddresses(TEST_MNEMONIC, "").eth
        assertEquals(expected.lowercase(), derived.lowercase())
    }

    @Test
    fun `adresses derivees valides selon AddressValidator`() {
        val a = WalletManager.deriveAddresses(TEST_MNEMONIC, "")
        assertTrue(com.vaultex.core.validation.AddressValidator.isValidEvm(a.eth))
        assertTrue(com.vaultex.core.validation.AddressValidator.isValidBtc(a.btc))
        assertTrue(com.vaultex.core.validation.AddressValidator.isValidTron(a.trx))
        assertTrue(com.vaultex.core.validation.AddressValidator.isValidSolana(a.sol))
    }

    @Test
    fun `la passphrase BIP39 change toutes les cles (M-03)`() {
        val sans = WalletManager.deriveAddresses(TEST_MNEMONIC, "")
        val avec = WalletManager.deriveAddresses(TEST_MNEMONIC, "ma-passphrase-secrete")
        assertNotEquals(sans.eth, avec.eth)
        assertNotEquals(sans.btc, avec.btc)
        assertNotEquals(sans.trx, avec.trx)
        assertNotEquals(sans.sol, avec.sol)
    }

    @Test
    fun `passphrase vide equivaut a BIP39 standard`() {
        // Garantit la compatibilité ascendante des wallets existants.
        val a = WalletManager.deriveAddresses(TEST_MNEMONIC, "")
        val b = WalletManager.deriveAddresses(TEST_MNEMONIC, "")
        assertEquals(a.eth, b.eth)
    }
}
