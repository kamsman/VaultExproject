package com.vaultex.data.repository

import com.vaultex.core.validation.AddressValidator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de WalletRepositoryImpl (délègue à WalletManager). Dérivation
 * déterministe sur le vecteur BIP39 canonique + validation de phrase.
 */
class WalletRepositoryImplTest {

    private val repo = WalletRepositoryImpl()
    private val mnemonic =
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    @Test
    fun `validateMnemonic accepte une phrase BIP39 valide`() {
        assertTrue(repo.validateMnemonic(mnemonic))
    }

    @Test
    fun `validateMnemonic rejette une phrase invalide`() {
        assertFalse(repo.validateMnemonic("ceci n est pas une phrase bip39 valide du tout"))
    }

    @Test
    fun `validateMnemonic rejette une phrase vide`() {
        assertFalse(repo.validateMnemonic(""))
    }

    @Test
    fun `deriveAddresses est deterministe`() = runBlocking {
        val a = repo.deriveAddresses(mnemonic, "")
        val b = repo.deriveAddresses(mnemonic, "")
        assertEquals(a.eth, b.eth)
        assertEquals(a.btc, b.btc)
        assertEquals(a.trx, b.trx)
        assertEquals(a.sol, b.sol)
    }

    @Test
    fun `deriveAddresses produit des adresses valides`() = runBlocking {
        val a = repo.deriveAddresses(mnemonic, "")
        assertTrue(AddressValidator.isValidEvm(a.eth))
        assertTrue(AddressValidator.isValidBtc(a.btc))
        assertTrue(AddressValidator.isValidTron(a.trx))
        assertTrue(AddressValidator.isValidSolana(a.sol))
    }

    @Test
    fun `la passphrase BIP39 change l adresse ETH`() = runBlocking {
        val sans = repo.deriveAddresses(mnemonic, "")
        val avec = repo.deriveAddresses(mnemonic, "passphrase-secrete")
        assertNotEquals(sans.eth, avec.eth)
    }
}
