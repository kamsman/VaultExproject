package com.vaultex.core.validation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cas limites complémentaires d'AddressValidator (longueurs, casse,
 * jeux de caractères, routage par chaîne).
 */
class AddressValidatorExtraTest {

    private val validEvm = "0x9858EfFD232B4033E47d90003D41EC34EcaEda94"

    @Test
    fun `EVM tolere majuscules et minuscules`() {
        assertTrue(AddressValidator.isValidEvm(validEvm.lowercase()))
        assertTrue(AddressValidator.isValidEvm(validEvm.uppercase().replace("0X", "0x")))
    }

    @Test
    fun `EVM rejette les longueurs incorrectes`() {
        assertFalse(AddressValidator.isValidEvm("0x" + "a".repeat(39))) // 39 hex
        assertFalse(AddressValidator.isValidEvm("0x" + "a".repeat(41))) // 41 hex
    }

    @Test
    fun `BTC accepte P2SH commencant par 3`() {
        assertTrue(AddressValidator.isValidBtc("3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy"))
    }

    @Test
    fun `BTC rejette une chaine vide`() {
        assertFalse(AddressValidator.isValidBtc(""))
    }

    @Test
    fun `Tron rejette une longueur differente de 34`() {
        assertFalse(AddressValidator.isValidTron("T" + "a".repeat(40)))
        assertFalse(AddressValidator.isValidTron("T"))
    }

    @Test
    fun `Tron rejette une adresse ne commencant pas par T`() {
        assertFalse(AddressValidator.isValidTron("XR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"))
    }

    @Test
    fun `Solana accepte la longueur minimale de 32`() {
        assertTrue(AddressValidator.isValidSolana("1".repeat(32)))
    }

    @Test
    fun `Solana rejette les chaines trop longues`() {
        assertFalse(AddressValidator.isValidSolana("1".repeat(45)))
    }

    @Test
    fun `isValid route USDT-BNB vers la validation EVM`() {
        assertTrue(AddressValidator.isValid(validEvm, "USDT-BNB"))
        assertFalse(AddressValidator.isValid("0x123", "USDT-BNB"))
    }

    @Test
    fun `isValid route SOL vers la validation Solana`() {
        assertTrue(AddressValidator.isValid("11111111111111111111111111111111", "SOL"))
        assertFalse(AddressValidator.isValid("0x123", "SOL"))
    }

    @Test
    fun `isValid chaine inconnue applique le repli de longueur`() {
        assertTrue(AddressValidator.isValid("a".repeat(26), "UNKNOWN"))
        assertFalse(AddressValidator.isValid("court", "UNKNOWN"))
    }
}
