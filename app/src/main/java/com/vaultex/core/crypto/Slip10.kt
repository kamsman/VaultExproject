package com.vaultex.core.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Implémentation SLIP-0010 pour la dérivation HD ed25519 (Solana).
 * Spec: https://github.com/satoshilabs/slips/blob/master/slip-0010.md
 *
 * NOTE: ed25519 ne supporte que la dérivation hardened (tous les indices doivent être >= 2^31).
 */
object Slip10 {

    private const val MASTER_KEY = "ed25519 seed"

    data class Ed25519DerivedKey(
        val privateKey: ByteArray,  // 32 bytes
        val chainCode: ByteArray    // 32 bytes
    )

    /**
     * Dérive une clé ed25519 à partir d'une seed et d'un path (indices hardened).
     */
    fun deriveEd25519Key(seed: ByteArray, path: IntArray): Ed25519DerivedKey {
        // Master key generation
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(MASTER_KEY.toByteArray(), "HmacSHA512"))
        val i = mac.doFinal(seed)
        var iL = i.copyOfRange(0, 32)
        var iR = i.copyOfRange(32, 64)

        // Dérivation enfant pour chaque niveau du path
        for (index in path) {
            require(index < 0 || index >= 0x80000000.toInt()) {
                "ed25519 ne supporte que la dérivation hardened (index >= 2^31)"
            }
            val data = ByteArray(37)
            data[0] = 0x00  // 0x00 prefix pour ed25519 hardened
            System.arraycopy(iL, 0, data, 1, 32)
            data[33] = (index ushr 24 and 0xFF).toByte()
            data[34] = (index ushr 16 and 0xFF).toByte()
            data[35] = (index ushr 8 and 0xFF).toByte()
            data[36] = (index and 0xFF).toByte()

            val childMac = Mac.getInstance("HmacSHA512")
            childMac.init(SecretKeySpec(iR, "HmacSHA512"))
            val derived = childMac.doFinal(data)
            iL = derived.copyOfRange(0, 32)
            iR = derived.copyOfRange(32, 64)
        }

        return Ed25519DerivedKey(iL, iR)
    }
}
