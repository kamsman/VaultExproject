package com.vaultex.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * ═══════════════════════════════════════════════════════════════════════
 * LA SIGNATURE SOLANA, ÉPINGLÉE SUR LES VECTEURS DE LA RFC 8032
 * ═══════════════════════════════════════════════════════════════════════
 *
 * CE FICHIER EXISTE À CAUSE D'UN AUDIT DE DÉPENDANCES. BouncyCastle 1.78.1
 * porte cinq avis de sécurité, dont deux critiques. Aucun ne touche le
 * chemin que nous employons — nous n'utilisons que deux classes, pour
 * signer sur Solana, et les avis concernent GOST, LDAP, les contraintes
 * de nom X.509 et l'analyse ASN.1.
 *
 * Mais en voulant monter la version, on a découvert que **rien ne
 * l'exerçait**. Les vecteurs de dérivation sont épinglés ; la SIGNATURE,
 * non. Monter une bibliothèque de cryptographie sans test qui la fasse
 * travailler, c'est casser la signature en silence — et ça ne se verrait
 * qu'au premier envoi Solana réel, c'est-à-dire sur l'argent de
 * quelqu'un.
 *
 * ─── POURQUOI LA RFC ET PAS NOTRE PROPRE SORTIE ────────────────────────
 *
 * Un test qui compare le code à lui-même valide n'importe quoi. Ces
 * valeurs viennent de la RFC 8032, section 7.1 — la norme d'Ed25519 —
 * et non de cette application. Elles sont les mêmes pour toute
 * implémentation correcte, de n'importe quelle maison, de n'importe
 * quelle version.
 *
 * Si BouncyCastle change de comportement en montant de version, ces
 * assertions tombent. C'est exactement ce qu'on veut d'elles.
 *
 * ─── ED25519 EST DÉTERMINISTE, ET C'EST CE QUI REND CE TEST POSSIBLE ───
 *
 * Contrairement à ECDSA, Ed25519 ne tire aucun aléa : la même clé et le
 * même message donnent toujours la même signature, octet pour octet. On
 * peut donc l'épingler, ce qu'on ne pourrait pas faire pour une
 * signature secp256k1.
 * ═══════════════════════════════════════════════════════════════════════
 */
class Ed25519UtilsTest {

    private companion object {
        fun octets(hex: String): ByteArray =
            hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        fun hex(o: ByteArray): String =
            o.joinToString("") { "%02x".format(it) }

        // RFC 8032 — 7.1, TEST 1 : message vide.
        const val CLE_1 = "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60"
        const val PUB_1 = "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"
        const val SIG_1 =
            "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e065224901555" +
            "fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b"

        // RFC 8032 — 7.1, TEST 2 : message d'un seul octet, 0x72.
        const val CLE_2 = "4ccd089b28ff96da9db6c346ec114e0f5b8a319f35aba624da8cf6ed4fb8a6fb"
        const val PUB_2 = "3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c"
        const val MSG_2 = "72"
        const val SIG_2 =
            "92a009a9f0d4cab8720e820b5f642540a2b27b5416503f8fb3762223ebdb69da" +
            "085ac1e43e15996e458f3613d0f11d8c387b2eaeb4302aeeb00d291612bb0c00"
    }

    /*
    LA CLÉ PUBLIQUE EST L'ADRESSE SOLANA. Si cette dérivation changeait,
    l'application afficherait une adresse différente pour la même phrase
    — et les fonds déjà reçus deviendraient inatteignables.
    */
    @Test
    fun `la cle publique suit la RFC 8032`() {
        assertEquals(PUB_1, hex(Ed25519Utils.publicKeyFromPrivate(octets(CLE_1))))
        assertEquals(PUB_2, hex(Ed25519Utils.publicKeyFromPrivate(octets(CLE_2))))
    }

    /*
    LA SIGNATURE, SUR LE MESSAGE VIDE ET SUR UN OCTET. Deux vecteurs
    plutôt qu'un : une implémentation qui traite mal le cas vide peut
    très bien réussir le cas général, et l'inverse.
    */
    @Test
    fun `la signature suit la RFC 8032`() {
        assertEquals(SIG_1, hex(Ed25519Utils.sign(ByteArray(0), octets(CLE_1))))
        assertEquals(SIG_2, hex(Ed25519Utils.sign(octets(MSG_2), octets(CLE_2))))
    }

    /*
    SOIXANTE-QUATRE OCTETS, TOUJOURS. Solana refuse toute autre longueur,
    et une signature tronquée part sans erreur côté application.
    */
    @Test
    fun `une signature fait toujours soixante-quatre octets`() {
        for (taille in listOf(0, 1, 32, 256, 1232)) {
            assertEquals(64, Ed25519Utils.sign(ByteArray(taille) { it.toByte() }, octets(CLE_1)).size)
        }
    }

    /*
    DEUX MESSAGES DIFFÉRENTS NE DONNENT PAS LA MÊME SIGNATURE. Contrôle
    grossier, et c'est voulu : il attraperait une implémentation qui
    ignore le message — le genre de défaut qu'un vecteur unique ne voit
    pas, puisqu'il passerait aussi.
    */
    @Test
    fun `le message entre bien dans la signature`() {
        assertNotEquals(
            hex(Ed25519Utils.sign(octets("00"), octets(CLE_1))),
            hex(Ed25519Utils.sign(octets("01"), octets(CLE_1)))
        )
    }

    /*
    UNE CLÉ D'UNE AUTRE TAILLE EST REFUSÉE, et non tronquée en silence.
    Trente-deux octets est la seule longueur valide d'une clé privée
    Ed25519 ; en accepter une autre signerait avec autre chose que ce que
    l'appelant croit.
    */
    @Test(expected = IllegalArgumentException::class)
    fun `une cle privee de mauvaise taille est refusee`() {
        Ed25519Utils.sign(ByteArray(8), ByteArray(31))
    }
}
