package com.vaultex.core.crypto

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters

/**
 * Utilitaires ed25519 — calcul de la clé publique depuis la clé privée pour Solana.
 * Utilise BouncyCastle pour l'opération ed25519.
 */
object Ed25519Utils {

    /**
     * Calcule la clé publique ed25519 (32 bytes) à partir d'une clé privée (32 bytes).
     * C'est cette clé publique qui sert d'adresse Solana (encodée en Base58).
     */
    fun publicKeyFromPrivate(privateKey: ByteArray): ByteArray {
        require(privateKey.size == 32) { "Clé privée ed25519 doit faire 32 bytes" }
        val privParams = Ed25519PrivateKeyParameters(privateKey, 0)
        return privParams.generatePublicKey().encoded
    }
}
