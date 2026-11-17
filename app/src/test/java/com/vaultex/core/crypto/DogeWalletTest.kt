package com.vaultex.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.web3j.crypto.MnemonicUtils

/**
 * ═══════════════════════════════════════════════════════════════════════
 * LA DÉRIVATION DOGECOIN, CONFRONTÉE À DEUX RÉFÉRENCES INDÉPENDANTES
 * ═══════════════════════════════════════════════════════════════════════
 *
 * CE TEST EXISTE PARCE QUE JE N'AVAIS PAS DE COMPILATEUR KOTLIN en
 * écrivant [DogeWallet]. Les valeurs ci-dessous n'ont donc pas été
 * produites par ce code : elles ont été calculées ailleurs, deux fois, et
 * c'est le build qui vérifie que bitcoinj retombe dessus.
 *
 * ─── D'OÙ ELLES VIENNENT ───────────────────────────────────────────────
 *
 * 1. `bip_utils`, bibliothèque Python de référence, interrogée pour
 *    Bip44Coins.DOGECOIN. C'est elle qui a donné les octets de version
 *    0x1E et 0x9E — ils n'ont pas été récités.
 *
 * 2. Une réimplémentation écrite de zéro, ne partageant aucun code avec
 *    la première : PBKDF2-HMAC-SHA512 pour BIP-39, HMAC-SHA512 et
 *    arithmétique secp256k1 à la main pour BIP-32, puis HASH160 et
 *    base58check.
 *
 * Les deux concordent sur les quatre phrases ET sur les clés WIF. Une
 * seule référence aurait prouvé que je sais recopier une bibliothèque ;
 * deux prouvent que la mécanique est comprise.
 *
 * ─── CE QU'ILS ATTRAPENT ───────────────────────────────────────────────
 *
 * Exactement les fautes qui ne lèvent AUCUNE exception et produisent une
 * adresse parfaitement valide — donc des Dogecoin envoyés à personne :
 *
 *   — un chemin en `m/84'` au lieu de `m/44'`, par recopie du code BTC ;
 *   — le type de pièce 0 (Bitcoin) au lieu de 3 ;
 *   — l'octet 0x00 au lieu de 0x1E, qui donnerait une adresse Bitcoin ;
 *   — une clé publique non compressée, qui change le HASH160 ;
 *   — une somme de contrôle prise dans le mauvais sens.
 *
 * ─── CE QU'ILS NE PROUVENT PAS ─────────────────────────────────────────
 *
 * Que le réseau Dogecoin accepte ces adresses. Aucun test hors ligne ne
 * peut le prouver. Cela se vérifie en envoyant un Dogecoin depuis un
 * autre portefeuille vers l'adresse produite, et en le voyant arriver —
 * et tant que l'ENVOI n'existe pas dans VaultEx, cet essai se fait avec
 * un montant qu'on accepte de perdre.
 * ═══════════════════════════════════════════════════════════════════════
 */
class DogeWalletTest {

    private companion object {
        const val ABANDON =
            "abandon abandon abandon abandon abandon abandon abandon abandon " +
            "abandon abandon abandon about"
        const val LEGAL =
            "legal winner thank year wave sausage worth useful legal winner thank yellow"
        const val ZOO = "zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong"
        const val LETTER =
            "letter advice cage absurd amount doctor acoustic avoid letter advice cage above"
    }

    private fun graine(phrase: String) = MnemonicUtils.generateSeed(phrase, "")

    @Test
    fun `les quatre phrases de reference donnent les adresses attendues`() {
        assertEquals("DBus3bamQjgJULBJtYXpEzDWQRwF5iwxgC", DogeWallet.deriveAddress(graine(ABANDON)))
        assertEquals("DMM78DyiwEPzWmykiQQUFcPcUv13YPe2ZK", DogeWallet.deriveAddress(graine(LEGAL)))
        assertEquals("DTMaJd8wqye1fymnjxZ5Cc5QkN1w4pMgXT", DogeWallet.deriveAddress(graine(ZOO)))
        assertEquals("D9Z5oxoyo3Lm8uRAKcczviagQnMinjWLgJ", DogeWallet.deriveAddress(graine(LETTER)))
    }

    @Test
    fun `les cles WIF de reference sont identiques`() {
        assertEquals(
            "QPkeC1ZfHx3c9g7WTj9cQ8gnvk2iSAfAcbq1aVAWjNTwDAKfZUzx",
            DogeWallet.derivePrivateKeyWif(graine(ABANDON))
        )
        assertEquals(
            "QS5B2Yt6HFyVS6G16jiVC1AgdJr44X4kxM3SMyYt86bRhXaickki",
            DogeWallet.derivePrivateKeyWif(graine(LEGAL))
        )
        assertEquals(
            "QNnJGzaAidpwrbqHuLVsjgdnagU3TwgxCwAS2qF9bfjjuMnCn8Lw",
            DogeWallet.derivePrivateKeyWif(graine(LETTER))
        )
    }

    /*
    L'INDEX DOIT VRAIMENT CHANGER LA CLÉ, et pas seulement être accepté.
    Un paramètre ignoré rendrait quatre fois la même adresse — le genre de
    défaut qu'un test sur le seul index 0 ne voit jamais.
    */
    @Test
    fun `l index d adresse derive bien des cles differentes`() {
        val seed = graine(ABANDON)
        assertEquals("DBus3bamQjgJULBJtYXpEzDWQRwF5iwxgC", DogeWallet.deriveAddress(seed, 0))
        assertEquals("DAcDAtJRztxBHyA6D6h8du1HguyTR43Mas", DogeWallet.deriveAddress(seed, 1))
        assertEquals("D8K3KyDQ9FXeC3ADCuWW7cnWC7RvjHjV8H", DogeWallet.deriveAddress(seed, 2))
        assertEquals("D6RRdXkUbb3pazkYGXAXwbJY5iC8Tyqwzh", DogeWallet.deriveAddress(seed, 3))
    }

    /*
    LA MÊME GRAINE QUE LES CINQ AUTRES CHAÎNES. Si ce test échouait, le
    Dogecoin vivrait sous une phrase différente de tout le reste — et la
    sauvegarde des douze mots ne le restaurerait pas.
    */
    @Test
    fun `la graine est celle de toutes les autres chaines`() {
        val parWalletManager = MnemonicUtils.generateSeed(ABANDON, "")
        assertEquals(
            DogeWallet.deriveAddress(parWalletManager),
            DogeWallet.deriveAddress(graine(ABANDON))
        )
    }

    /*
    ═══════════════════════════════════════════════════════════════════
    LE CABLAGE, ET PAS SEULEMENT LA MECANIQUE
    ═══════════════════════════════════════════════════════════════════

    Les tests du dessus prouvent que DogeWallet derive juste. Celui-ci
    prouve que l'APPLICATION l'appelle avec la bonne graine.

    La difference n'est pas theorique : `deriveAddresses` pourrait passer
    une graine derivee autrement, ou une passphrase differente, et toutes
    les adresses seraient valides — simplement pas celles que les douze
    mots de l'utilisateur restaurent ailleurs. Il le decouvrirait en
    important sa phrase dans un autre portefeuille et en n'y trouvant
    rien.
    ═══════════════════════════════════════════════════════════════════
    */
    @Test
    fun `l application derive la meme adresse que DogeWallet`() {
        val parApplication = WalletManager.deriveAddresses(ABANDON, "").doge
        assertEquals("DBus3bamQjgJULBJtYXpEzDWQRwF5iwxgC", parApplication)
        assertEquals(DogeWallet.deriveAddress(graine(ABANDON)), parApplication)
    }

    @Test
    fun `toutes les adresses commencent par D`() {
        for (phrase in listOf(ABANDON, LEGAL, ZOO, LETTER)) {
            val adresse = DogeWallet.deriveAddress(graine(phrase))
            assertTrue("« $adresse » ne commence pas par D", adresse.startsWith("D"))
        }
    }

    @Test
    fun `une adresse derivee se valide elle-meme`() {
        for (phrase in listOf(ABANDON, LEGAL, ZOO, LETTER)) {
            assertTrue(DogeWallet.estAdresseValide(DogeWallet.deriveAddress(graine(phrase))))
        }
    }

    /*
    UN SEUL CARACTÈRE CHANGÉ, ET C'EST LE CAS RÉEL : une adresse recopiée
    à la main, ou lue sur une capture d'écran. Elle garde sa longueur, son
    « D », son alphabet — et seule la somme de contrôle la démasque.
    */
    @Test
    fun `une adresse au dernier caractere change est refusee`() {
        assertFalse(DogeWallet.estAdresseValide("DBus3bamQjgJULBJtYXpEzDWQRwF5iwxgA"))
    }

    /*
    ═══════════════════════════════════════════════════════════════════
    LE VALIDATEUR DE L'APPLICATION DOIT EMPLOYER CE CONTROLE
    ═══════════════════════════════════════════════════════════════════

    `AddressValidator` retombe par defaut sur « plus de 26 caracteres »,
    qui accepte presque tout. Tant que le Dogecoin ne s'envoyait pas, ca
    n'avait pas de consequence ; depuis que `sendDoge` existe, c'est un
    chemin d'argent.

    Ce test ferme la boucle : il interroge le validateur de
    l'application, pas DogeWallet directement. Si la branche « DOGE »
    disparaissait, une adresse d'une autre chaine passerait — et les
    fonds partiraient a une adresse que personne ne controle.
    ═══════════════════════════════════════════════════════════════════
    */
    @Test
    fun `le validateur de l application refuse une adresse DOGE fausse`() {
        val valide = DogeWallet.deriveAddress(graine(ABANDON))
        assertTrue(
            com.vaultex.core.validation.AddressValidator.isValid(valide, "DOGE")
        )
        for (mauvaise in listOf(
            "DBus3bamQjgJULBJtYXpEzDWQRwF5iwxgA",          // un caractere change
            "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa",          // Bitcoin
            "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t",          // Tron
            "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB" // Solana
        )) {
            assertFalse(
                "« $mauvaise » aurait du etre refusee pour DOGE",
                com.vaultex.core.validation.AddressValidator.isValid(mauvaise, "DOGE")
            )
        }
    }

    @Test
    fun `une adresse d une autre chaine est refusee`() {
        // Bitcoin hérité (version 0x00), Bitcoin SegWit, Tron, Solana.
        assertFalse(DogeWallet.estAdresseValide("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"))
        assertFalse(DogeWallet.estAdresseValide("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"))
        assertFalse(DogeWallet.estAdresseValide("TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"))
        assertFalse(DogeWallet.estAdresseValide("Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB"))
        assertFalse(DogeWallet.estAdresseValide(""))
        assertFalse(DogeWallet.estAdresseValide("pas une adresse"))
    }

    /*
    LES DEUX OCTETS DE VERSION, ÉPINGLÉS. Ils ne se devinent pas et ne se
    recalculent pas : ils viennent de la configuration du réseau. Les
    figer ici fait qu'une « simplification » qui les alignerait sur ceux
    de Bitcoin casse un test au lieu de casser des portefeuilles.
    */
    @Test
    fun `les octets de version sont ceux de Dogecoin et pas ceux de Bitcoin`() {
        assertEquals(0x1E, DogeWallet.VERSION_ADRESSE)
        assertEquals(0x9E, DogeWallet.VERSION_WIF)
        assertEquals(3, DogeWallet.TYPE_PIECE)
        assertEquals(8, DogeWallet.DECIMALES)
    }
}
