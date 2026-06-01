package com.vaultex.core.crypto

import org.junit.Assert.*
import org.junit.Test

class WalletManagerTest {

    // Vecteur BIP39 officiel (sans passphrase)
    private val knownMnemonic = "abandon abandon abandon abandon abandon abandon " +
            "abandon abandon abandon abandon abandon about"

    @Test
    fun `deriveAddresses returns non-null addresses for all 5 chains`() {
        val addrs = WalletManager.deriveAddresses(knownMnemonic)
        assertTrue("BTC address non vide", addrs.btc.isNotBlank())
        assertTrue("ETH address non vide", addrs.eth.isNotBlank())
        assertTrue("BNB address non vide", addrs.bnb.isNotBlank())
        assertTrue("SOL address non vide", addrs.sol.isNotBlank())
        assertTrue("TRX address non vide", addrs.trx.isNotBlank())
    }

    @Test
    fun `BTC address starts with 1 (P2PKH Legacy)`() {
        val addrs = WalletManager.deriveAddresses(knownMnemonic)
        assertTrue("BTC P2PKH commence par 1", addrs.btc.startsWith("1"))
    }

    @Test
    fun `ETH address starts with 0x and is 42 chars`() {
        val addrs = WalletManager.deriveAddresses(knownMnemonic)
        assertTrue("ETH commence par 0x", addrs.eth.startsWith("0x"))
        assertEquals("ETH longueur 42", 42, addrs.eth.length)
    }

    @Test
    fun `BNB and ETH share same address for same coin_type`() {
        val addrs = WalletManager.deriveAddresses(knownMnemonic)
        // BNB utilise coin_type 60 comme ETH → même adresse depuis la même seed
        assertEquals("BNB == ETH pour coin_type 60", addrs.eth, addrs.bnb)
    }

    @Test
    fun `SOL address is 32-44 chars Base58`() {
        val addrs = WalletManager.deriveAddresses(knownMnemonic)
        val len = addrs.sol.length
        assertTrue("SOL longueur Base58 valide ($len)", len in 32..44)
    }

    @Test
    fun `TRX address starts with T and is 34 chars`() {
        val addrs = WalletManager.deriveAddresses(knownMnemonic)
        assertTrue("TRX commence par T", addrs.trx.startsWith("T"))
        assertEquals("TRX longueur 34", 34, addrs.trx.length)
    }

    @Test
    fun `derivation is deterministic — same mnemonic same addresses`() {
        val a1 = WalletManager.deriveAddresses(knownMnemonic)
        val a2 = WalletManager.deriveAddresses(knownMnemonic)
        assertEquals(a1.btc, a2.btc)
        assertEquals(a1.eth, a2.eth)
        assertEquals(a1.sol, a2.sol)
        assertEquals(a1.trx, a2.trx)
    }

    @Test
    fun `different mnemonics produce different addresses`() {
        val mnemonic2 = "zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong"
        val a1 = WalletManager.deriveAddresses(knownMnemonic)
        // zoo wrong a un mauvais checksum → utiliser un vecteur valide différent
        val mnemonic3 = "legal winner thank year wave sausage worth useful legal " +
                "winner thank yellow"
        val a3 = WalletManager.deriveAddresses(mnemonic3)
        assertNotEquals(a1.eth, a3.eth)
        assertNotEquals(a1.btc, a3.btc)
    }
}
