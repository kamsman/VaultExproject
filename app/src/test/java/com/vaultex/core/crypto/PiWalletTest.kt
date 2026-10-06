package com.vaultex.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrat de l'adresse Pi.
 *
 * Aucun de ces tests ne touche le réseau : ils vérifient la FORME, qui est
 * la seule chose qu'on puisse vérifier sans fonds — et la seule qui, fausse,
 * enverrait de l'argent dans le vide sans lever la moindre erreur.
 *
 * CE QU'ILS NE PROUVENT PAS, et il faut le savoir : qu'une adresse produite
 * ici soit bien celle que le Pi Wallet donnerait pour la même phrase. Cela
 * ne se vérifie qu'en comparant, sur un appareil, avec une adresse réelle —
 * et c'est la toute première chose à faire avant d'y envoyer un seul Pi.
 */
class PiWalletTest {

    @Test fun `une adresse derivee a la forme attendue`() {
        val seed = ByteArray(64) { it.toByte() }
        val adresse = PiWallet.deriveAddress(seed)

        assertEquals("une adresse de compte fait 56 caractères", 56, adresse.length)
        assertTrue("elle commence par G", adresse.startsWith("G"))
        assertTrue(
            "elle ne contient que l'alphabet base32",
            adresse.all { it in 'A'..'Z' || it in '2'..'7' }
        )
    }

    @Test fun `une adresse derivee se valide elle-meme`() {
        val seed = ByteArray(64) { (it * 7).toByte() }
        assertTrue(PiWallet.adresseValide(PiWallet.deriveAddress(seed)))
    }

    /**
     * La même graine donne toujours la même adresse.
     *
     * C'est ce qui garantit qu'un utilisateur retrouve ses fonds après une
     * réinstallation. Une dérivation qui varierait — ordre des octets,
     * aléa résiduel — rendrait l'adresse irrécupérable, et on ne s'en
     * apercevrait qu'au moment où quelqu'un en aurait besoin.
     */
    @Test fun `la derivation est deterministe`() {
        val seed = ByteArray(64) { (it + 3).toByte() }
        assertEquals(PiWallet.deriveAddress(seed), PiWallet.deriveAddress(seed))
    }

    @Test fun `deux graines donnent deux adresses`() {
        val a = PiWallet.deriveAddress(ByteArray(64) { it.toByte() })
        val b = PiWallet.deriveAddress(ByteArray(64) { (it + 1).toByte() })
        assertFalse(a == b)
    }

    /*
    ─── LA SOMME DE CONTRÔLE FAIT SON TRAVAIL ─────────────────────────────

    C'est le seul rempart contre une adresse mal recopiée. Sans elle, un
    caractère changé passerait les contrôles de longueur et de préfixe, et
    les fonds partiraient vers une adresse qui n'appartient à personne.
    */
    @Test fun `un caractere modifie invalide l'adresse`() {
        val adresse = PiWallet.deriveAddress(ByteArray(64) { it.toByte() })
        // On change un caractère du milieu, en restant dans l'alphabet.
        val position = 20
        val remplacant = if (adresse[position] == 'A') 'B' else 'A'
        val falsifiee = adresse.substring(0, position) + remplacant + adresse.substring(position + 1)

        assertEquals("même longueur", adresse.length, falsifiee.length)
        assertFalse("la somme de contrôle doit la refuser", PiWallet.adresseValide(falsifiee))
    }

    @Test fun `une adresse tronquee est refusee`() {
        val adresse = PiWallet.deriveAddress(ByteArray(64) { it.toByte() })
        assertFalse(PiWallet.adresseValide(adresse.dropLast(1)))
    }

    @Test fun `ce qui n'est pas une adresse est refuse`() {
        assertFalse(PiWallet.adresseValide(""))
        assertFalse(PiWallet.adresseValide("GBADRESSE"))
        // Adresse Solana : même longueur d'esprit, autre alphabet.
        assertFalse(PiWallet.adresseValide("7wXTW4DH9PMtmY4zvwNxKa58xxhBUy1tG4BpfZveC3PD"))
        // Adresse EVM.
        assertFalse(PiWallet.adresseValide("0x9858EfFD232B4033E47d90003D41EC34EcaEda94"))
    }
}
