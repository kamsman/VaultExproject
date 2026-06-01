package com.vaultex.core.crypto

import org.junit.Assert.*
import org.junit.Test
import org.web3j.crypto.MnemonicUtils

/**
 * Tests BIP39 via WalletManager (remplace l'ancien Bip39Generator supprimé).
 */
class Bip39GeneratorTest {

    @Test
    fun `generates 12-word mnemonic by default`() {
        val words = WalletManager.generateMnemonic()
        assertEquals(12, words.size)
    }

    @Test
    fun `each generation produces a different mnemonic`() {
        val m1 = WalletManager.generateMnemonic().joinToString(" ")
        val m2 = WalletManager.generateMnemonic().joinToString(" ")
        assertNotEquals(m1, m2)
    }

    @Test
    fun `valid mnemonic passes validation`() {
        val mnemonic = WalletManager.generateMnemonic().joinToString(" ")
        assertTrue(WalletManager.validateMnemonic(mnemonic))
    }

    @Test
    fun `invalid checksum is detected`() {
        // "zoo" est valide BIP39 mais le checksum est faux avec ces 11 mots
        val invalid = "abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon abandon abandon zoo"
        assertFalse(WalletManager.validateMnemonic(invalid))
    }

    @Test
    fun `valid BIP39 test vector passes validation`() {
        val known = "abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon abandon abandon about"
        assertTrue(WalletManager.validateMnemonic(known))
    }

    @Test
    fun `mnemonic to seed produces 64 byte seed`() {
        val mnemonic = "abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon abandon abandon about"
        val seed = MnemonicUtils.generateSeed(mnemonic, "")
        assertEquals(64, seed.size)
    }

    @Test
    fun `empty string fails validation`() {
        assertFalse(WalletManager.validateMnemonic(""))
    }

    @Test
    fun `gibberish fails validation`() {
        assertFalse(WalletManager.validateMnemonic("foo bar baz"))
    }
}
