package com.vaultex.core.crypto

import org.junit.Assert.*
import org.junit.Test

class Slip10Test {

    @Test
    fun `master key derivation produces 32-byte private key and chain code`() {
        val seed = ByteArray(64) { it.toByte() }
        val result = Slip10.deriveEd25519Key(seed, intArrayOf())
        assertEquals(32, result.privateKey.size)
        assertEquals(32, result.chainCode.size)
    }

    @Test
    fun `hardened index is accepted`() {
        val seed = ByteArray(64) { 0x01 }
        val hardenedIndex = 44 or Int.MIN_VALUE  // bit 31 positionné
        assertNotNull(Slip10.deriveEd25519Key(seed, intArrayOf(hardenedIndex)))
    }

    @Test
    fun `non-hardened index throws IllegalArgumentException`() {
        val seed = ByteArray(64) { 0x01 }
        val normalIndex = 44  // pas de bit hardened → doit échouer
        try {
            Slip10.deriveEd25519Key(seed, intArrayOf(normalIndex))
            fail("Devrait lever IllegalArgumentException pour index non-hardened")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("hardened") == true)
        }
    }

    @Test
    fun `Solana path derivation is deterministic`() {
        val seed = ByteArray(64) { 0xAB.toByte() }
        val path = intArrayOf(
            44 or Int.MIN_VALUE,
            501 or Int.MIN_VALUE,
            0 or Int.MIN_VALUE,
            0 or Int.MIN_VALUE
        )
        val r1 = Slip10.deriveEd25519Key(seed, path)
        val r2 = Slip10.deriveEd25519Key(seed, path)
        assertArrayEquals(r1.privateKey, r2.privateKey)
        assertArrayEquals(r1.chainCode, r2.chainCode)
    }

    @Test
    fun `different paths produce different keys`() {
        val seed = ByteArray(64) { 0x01 }
        val sol = Slip10.deriveEd25519Key(seed, intArrayOf(44 or Int.MIN_VALUE, 501 or Int.MIN_VALUE))
        val other = Slip10.deriveEd25519Key(seed, intArrayOf(44 or Int.MIN_VALUE, 60 or Int.MIN_VALUE))
        assertFalse(sol.privateKey.contentEquals(other.privateKey))
    }
}
