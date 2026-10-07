package com.vaultex.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Test
import org.web3j.crypto.MnemonicUtils

/**
 * ═══════════════════════════════════════════════════════════════════════
 * LA DÉRIVATION PI, CONFRONTÉE À LA SPÉCIFICATION
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Le Pi ne publie aucun vecteur de test. Stellar, si — et Pi partage avec
 * Stellar la TOTALITÉ de la mécanique de dérivation : BIP-39 pour la
 * graine, SLIP-0010 sur Ed25519 pour le chemin, StrKey pour l'adresse,
 * CRC16-XMODEM pour la somme de contrôle, base32 RFC 4648 pour l'écriture.
 * Seul le numéro de type de pièce diffère : 314159 contre 148.
 *
 * Ces vecteurs font donc passer le code de production — le même, pas une
 * copie — par la seule référence qui existe. Ils viennent de :
 *
 *   https://github.com/stellar/stellar-protocol
 *     /blob/master/ecosystem/sep-0005.md   (cas de test 1)
 *
 * CE QU'ILS PROUVENT : qu'une erreur dans SLIP-0010, dans l'ordre des
 * octets de la somme de contrôle, dans l'alphabet base32 ou dans le nombre
 * de niveaux du chemin serait attrapée ici. Ce sont exactement les fautes
 * qui ne lèvent aucune exception et produisent une adresse valide mais
 * fausse — donc des fonds envoyés dans le vide.
 *
 * CE QU'ILS NE PROUVENT PAS : que 314159 soit le bon type de pièce pour le
 * Pi. Aucun test ne peut le prouver. Cela se vérifie en envoyant un Pi
 * depuis le Pi Wallet vers l'adresse produite, et en le voyant arriver.
 * ═══════════════════════════════════════════════════════════════════════
 */
class PiWalletSep5Test {

    private companion object {
        /** Phrase de référence du SEP-0005, cas de test 1. */
        const val PHRASE =
            "illness spike retreat truth genius clock brain pass fit cave bargain toe"

        /** Type de pièce de Stellar. Celui du Pi est 314159. */
        const val TYPE_PIECE_STELLAR = 148

        /** Les dix adresses publiées pour cette phrase. */
        val ADRESSES = listOf(
            "GDRXE2BQUC3AZNPVFSCEZ76NJ3WWL25FYFK6RGZGIEKWE4SOOHSUJUJ6",
            "GBAW5XGWORWVFE2XTJYDTLDHXTY2Q2MO73HYCGB3XMFMQ562Q2W2GJQX",
            "GAY5PRAHJ2HIYBYCLZXTHID6SPVELOOYH2LBPH3LD4RUMXUW3DOYTLXW",
            "GAOD5NRAEORFE34G5D4EOSKIJB6V4Z2FGPBCJNQI6MNICVITE6CSYIAE",
            "GBCUXLFLSL2JE3NWLHAWXQZN6SQC6577YMAU3M3BEMWKYPFWXBSRCWV4",
            "GBRQY5JFN5UBG5PGOSUOL4M6D7VRMAYU6WW2ZWXBMCKB7GPT3YCBU2XZ",
            "GBY27SJVFEWR3DUACNBSMJB6T4ZPR4C7ZXSTHT6GMZUDL23LAM5S2PQX",
            "GAY7T23Z34DWLSTEAUKVBPHHBUE4E3EMZBAQSLV6ZHS764U3TKUSNJOF",
            "GDJTCF62UUYSAFAVIXHPRBR4AUZV6NYJR75INVDXLLRZLZQ62S44443R",
            "GBTVYYDIYWGUQUTKX6ZMLGSZGMTESJYJKJWAATGZGITA25ZB6T5REF44"
        )
    }

    /**
     * La graine BIP-39 elle-même, avant toute dérivation.
     *
     * Ce test isole le premier maillon. Sans lui, une erreur de PBKDF2 — de
     * sel, de nombre d'itérations, de normalisation Unicode — ferait échouer
     * les dix suivants sans dire lequel des maillons a lâché.
     */
    @Test fun `la graine BIP-39 est celle du SEP-0005`() {
        val attendue =
            "e4a5a632e70943ae7f07659df1332160937fad82587216a4c64315a0fb39497e" +
                "e4a01f76ddab4cba68147977f3a147b6ad584c41808e8238a07f6cc4b582f186"
        assertEquals(attendue, MnemonicUtils.generateSeed(PHRASE, "").toHex())
    }

    @Test fun `les dix adresses du SEP-0005 sont reproduites`() {
        val seed = MnemonicUtils.generateSeed(PHRASE, "")
        ADRESSES.forEachIndexed { compte, attendue ->
            val obtenue = PiWallet
                .derivePaireAvecTypePiece(seed, TYPE_PIECE_STELLAR, compte)
                .adresse
            assertEquals("m/44'/$TYPE_PIECE_STELLAR'/$compte'", attendue, obtenue)
        }
    }

    /**
     * Les adresses produites par le SEP-0005 passent notre propre
     * validation.
     *
     * Les deux chemins sont indépendants : l'un construit la somme de
     * contrôle, l'autre la relit. Un CRC16 faux des DEUX côtés de la même
     * façon se validerait lui-même en silence — c'est le défaut que seul un
     * vecteur externe peut révéler, et c'est ce que fait le test précédent.
     * Celui-ci vérifie en plus que les deux côtés sont d'accord.
     */
    @Test fun `la validation accepte les adresses de reference`() {
        ADRESSES.forEach {
            org.junit.Assert.assertTrue(it, PiWallet.adresseValide(it))
        }
    }

    /** La clé publique relue d'une adresse est celle qui l'a produite. */
    @Test fun `l'adresse et la cle publique sont reversibles`() {
        val seed = MnemonicUtils.generateSeed(PHRASE, "")
        val paire = PiWallet.derivePaireAvecTypePiece(seed, TYPE_PIECE_STELLAR, 0)
        assertEquals(
            paire.clePublique.toHex(),
            PiWallet.clePubliqueDeLAdresse(paire.adresse).toHex()
        )
    }

    /**
     * Le type de pièce du Pi ne donne PAS les adresses de Stellar.
     *
     * Ce test a l'air trivial. Il attrape pourtant la faute la plus
     * silencieuse possible : un `COIN_TYPE` resté à 148 par copier-coller
     * depuis le code Solana ou Stellar. L'application dériverait alors des
     * adresses Stellar valides, les afficherait comme des adresses Pi, et
     * tous les Pi envoyés dessus seraient perdus — sur une chaîne où ce
     * compte n'existe pas.
     */
    @Test fun `le type de piece Pi donne d'autres adresses que Stellar`() {
        val seed = MnemonicUtils.generateSeed(PHRASE, "")
        val pi = PiWallet.deriveAddress(seed)
        org.junit.Assert.assertFalse(
            "le type de pièce Pi est resté sur celui de Stellar",
            ADRESSES.contains(pi)
        )
        org.junit.Assert.assertTrue(PiWallet.adresseValide(pi))
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}
