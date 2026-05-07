package com.vaultex.core.crypto

import java.math.BigInteger

/**
 * Base58 encoding utility — utilisé pour les adresses Tron et Solana.
 */
object Base58 {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val ENCODED_ZERO = ALPHABET[0]

    fun encode(input: ByteArray): String {
        if (input.isEmpty()) return ""

        // Compter les zéros initiaux
        var zeros = 0
        while (zeros < input.size && input[zeros].toInt() == 0) zeros++

        val inputCopy = input.copyOf()
        val encoded = CharArray(inputCopy.size * 2)
        var outputStart = encoded.size

        var inputStart = zeros
        while (inputStart < inputCopy.size) {
            encoded[--outputStart] = ALPHABET[divmod(inputCopy, inputStart, 256, 58).toInt()]
            if (inputCopy[inputStart].toInt() == 0) inputStart++
        }

        // Skip leading zeros in encoded result
        while (outputStart < encoded.size && encoded[outputStart] == ENCODED_ZERO) outputStart++

        // Add 1 ('1') for each leading zero byte
        repeat(zeros) { encoded[--outputStart] = ENCODED_ZERO }

        return String(encoded, outputStart, encoded.size - outputStart)
    }

    fun decode(input: String): ByteArray {
        if (input.isEmpty()) return ByteArray(0)
        val input58 = ByteArray(input.length)
        for (i in input.indices) {
            val c = input[i]
            val digit = ALPHABET.indexOf(c)
            require(digit >= 0) { "Caractère Base58 invalide: $c" }
            input58[i] = digit.toByte()
        }

        var zeros = 0
        while (zeros < input58.size && input58[zeros].toInt() == 0) zeros++

        val decoded = ByteArray(input.length)
        var outputStart = decoded.size
        var inputStart = zeros
        while (inputStart < input58.size) {
            decoded[--outputStart] = divmod(input58, inputStart, 58, 256).toByte()
            if (input58[inputStart].toInt() == 0) inputStart++
        }
        while (outputStart < decoded.size && decoded[outputStart].toInt() == 0) outputStart++
        return decoded.copyOfRange(outputStart - zeros, decoded.size)
    }

    private fun divmod(number: ByteArray, firstDigit: Int, base: Int, divisor: Int): Byte {
        var remainder = 0
        for (i in firstDigit until number.size) {
            val digit = number[i].toInt() and 0xFF
            val temp = remainder * base + digit
            number[i] = (temp / divisor).toByte()
            remainder = temp % divisor
        }
        return remainder.toByte()
    }
}
