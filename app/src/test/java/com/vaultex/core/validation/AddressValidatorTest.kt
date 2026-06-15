package com.vaultex.core.validation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressValidatorTest {

    @Test
    fun `adresses EVM valides et invalides`() {
        assertTrue(AddressValidator.isValidEvm("0x9858EfFD232B4033E47d90003D41EC34EcaEda94"))
        assertFalse(AddressValidator.isValidEvm("0x123"))                 // trop court
        assertFalse(AddressValidator.isValidEvm("9858EfFD232B4033E47d90003D41EC34EcaEda94")) // sans 0x
        assertFalse(AddressValidator.isValidEvm("0xZZZZEfFD232B4033E47d90003D41EC34EcaEda94")) // non hex
    }

    @Test
    fun `adresses BTC valides et invalides`() {
        assertTrue(AddressValidator.isValidBtc("bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq"))
        assertTrue(AddressValidator.isValidBtc("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"))
        assertFalse(AddressValidator.isValidBtc("0x9858EfFD232B4033E47d90003D41EC34EcaEda94"))
        assertFalse(AddressValidator.isValidBtc("xyz"))
    }

    @Test
    fun `adresses Tron valides et invalides (checksum)`() {
        // Adresse base58check valide (contrat USDT-TRC20)
        assertTrue(AddressValidator.isValidTron("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"))
        assertFalse(AddressValidator.isValidTron("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6X")) // checksum cassé
        assertFalse(AddressValidator.isValidTron("0x9858EfFD232B4033E47d90003D41EC34EcaEda94"))
        assertFalse(AddressValidator.isValidTron("Ttooshort"))
    }

    @Test
    fun `adresses Solana valides et invalides`() {
        assertTrue(AddressValidator.isValidSolana("11111111111111111111111111111111"))
        assertFalse(AddressValidator.isValidSolana("0x123"))
        assertFalse(AddressValidator.isValidSolana("O0Il-not-base58!!"))
    }

    @Test
    fun `routage par chaine`() {
        val evm = "0x9858EfFD232B4033E47d90003D41EC34EcaEda94"
        assertTrue(AddressValidator.isValid(evm, "ETH"))
        assertTrue(AddressValidator.isValid(evm, "BNB"))
        assertTrue(AddressValidator.isValid(evm, "USDT-ETH"))
        assertTrue(AddressValidator.isValid("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t", "USDT"))
        assertFalse(AddressValidator.isValid(evm, "BTC"))
    }
}
