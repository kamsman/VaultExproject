package com.vaultex.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Bip39GeneratorTest {

    @Test
    fun `generates 12-word mnemonic by default`() {
        val mnemonic = Bip39Generator.generateMnemonic(12)
        val words = mnemonic.split(" ")
        assertEquals(12, words.size)
    }

    @Test
    fun `generates 24-word mnemonic when requested`() {
        val mnemonic = Bip39Generator.generateMnemonic(24)
        val words = mnemonic.split(" ")
        assertEquals(24, words.size)
    }

    @Test
    fun `each generation produces a different mnemonic`() {
        val m1 = Bip39Generator.generateMnemonic(12)
        val m2 = Bip39Generator.generateMnemonic(12)
        assertNotEquals(m1, m2)
    }

    @Test
    fun `valid mnemonic passes validation`() {
        val mnemonic = Bip39Generator.generateMnemonic(12)
        val result = Bip39Generator.validateMnemonic(mnemonic)
        assertTrue(result is MnemonicValidationResult.Valid)
    }

    @Test
    fun `invalid checksum is detected`() {
        val invalidMnemonic = "abandon abandon abandon abandon abandon abandon " +
            "abandon abandon abandon abandon abandon zoo"  // checksum wrong
        val result = Bip39Generator.validateMnemonic(invalidMnemonic)
        assertTrue(result is MnemonicValidationResult.InvalidChecksum)
    }

    @Test
    fun `mnemonic to seed produces 64 byte seed`() {
        // BIP39 test vector
        val mnemonic = "abandon abandon abandon abandon abandon abandon " +
            "abandon abandon abandon abandon abandon about"
        val seed = Bip39Generator.mnemonicToSeed(mnemonic)
        assertEquals(64, seed.size)
    }
}
