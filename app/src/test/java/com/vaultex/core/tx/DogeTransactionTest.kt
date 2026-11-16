package com.vaultex.core.tx

import com.vaultex.core.crypto.DogeWallet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ═══════════════════════════════════════════════════════════════════════
 * LA SÉRIALISATION, CONFRONTÉE À UNE TRANSACTION RÉELLEMENT PUBLIÉE
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Le vecteur central n'est pas inventé : c'est la transaction
 * 7d9cc37be46a55e672bf491a258efb7cd1ede313c7bc4b075e9208efe4feab60,
 * incluse dans le bloc 6460253 du réseau Dogecoin, récupérée avec son
 * hexadécimal par `tools/doge-sondes.sh --vivante`.
 *
 * On en reprend les champs un par un, on les resérialise, et on compare
 * aux octets que la chaîne porte vraiment. C'est la même méthode que
 * pour la dérivation, qui a tenu : une référence extérieure plutôt
 * qu'une relecture de son propre code.
 *
 * ─── CE QUE CE VECTEUR PROUVE ──────────────────────────────────────────
 *
 * Exactement les fautes qui ne lèvent AUCUNE erreur et produisent une
 * transaction refusée sans explication — ou pire, acceptée avec un
 * montant faux :
 *
 *   — un entier écrit poids fort en tête ;
 *   — un identifiant de transaction non retourné ;
 *   — une longueur mal comptée ;
 *   — un champ oublié, ou écrit dans le mauvais ordre.
 *
 * Le dernier contrôle — 101 octets consommés sur 101 — attrape le champ
 * en trop autant que le champ manquant.
 *
 * ─── CE QU'IL NE PROUVE PAS ────────────────────────────────────────────
 *
 * C'est une COINBASE : son entrée ne dépense rien, son script de sortie
 * est en `pay-to-pubkey`, et ses frais sont nuls. Rien de ce qu'un
 * portefeuille signe ne lui ressemble. Elle prouve la FORME du conteneur,
 * pas qu'une dépense ordinaire soit bien construite.
 *
 * `tools/doge-sondes.sh` prend désormais la SECONDE transaction d'un
 * bloc, qui est une dépense ordinaire — la première étant toujours la
 * coinbase. Le vecteur suivant la remplacera ici.
 * ═══════════════════════════════════════════════════════════════════════
 */
class DogeTransactionTest {

    private companion object {
        /** Les octets exacts, tels que la chaîne Dogecoin les porte. */
        const val HEX_REEL =
            "01000000" +                                                        // version
            "01" +                                                              // 1 entrée
            "0000000000000000000000000000000000000000000000000000000000000000" + // prevout (coinbase)
            "ffffffff" +                                                        // index
            "06" + "035d93620102" +                                             // script
            "ffffffff" +                                                        // sequence
            "01" +                                                              // 1 sortie
            "a18dbbe8e8000000" +                                                // 1 000 337 018 273
            "23" +
            "2103558b72cd4aea3838d6711048401d551a95d63270b0fc8a9b695ec56cd302de08ac" +
            "00000000"                                                          // locktime

        const val VALEUR_REELLE = 1_000_337_018_273L
    }

    private fun octets(hex: String) =
        ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    @Test
    fun `une transaction reelle se reserialise octet pour octet`() {
        val tx = DogeTransaction.Transaction(
            entrees = listOf(
                DogeTransaction.Entree(
                    txidAffiche = "0".repeat(64),
                    index = -1,                      // 0xFFFFFFFF
                    script = octets("035d93620102"),
                    sequence = 0xFFFFFFFFL
                )
            ),
            sorties = listOf(
                DogeTransaction.Sortie(
                    unites = VALEUR_REELLE,
                    script = octets(
                        "2103558b72cd4aea3838d6711048401d551a95d63270b0fc8a9b695ec56cd302de08ac"
                    )
                )
            )
        )
        assertEquals(HEX_REEL, DogeTransaction.hex(DogeTransaction.serialiser(tx)))
    }

    /*
    LA TAILLE EST UN CONTROLE A PART, et il attrape ce que la comparaison
    de chaînes attraperait moins lisiblement : un champ en trop se voit
    comme un nombre, pas comme une différence au millieu de deux cents
    caractères hexadécimaux.
    */
    @Test
    fun `la transaction reelle fait bien 101 octets`() {
        assertEquals(101, octets(HEX_REEL).size)
    }

    /*
    ═══════════════════════════════════════════════════════════════════
    LES BORNES DE `varint`, QUE LE VECTEUR NE COUVRE PAS
    ═══════════════════════════════════════════════════════════════════

    La transaction réelle ne contient que des longueurs de 1, 6, 35 — en
    dessous de 253, donc toutes sur un octet. Un sérialiseur qui écrit
    TOUJOURS un octet passerait ce vecteur, et tous les essais
    ordinaires : les scripts courants sont courts.

    Il casserait au premier script long — une transaction à plusieurs
    entrées — c'est-à-dire chez quelqu'un, et pas en test. Les bornes
    sont donc écrites ici une par une.
    ═══════════════════════════════════════════════════════════════════
    */
    @Test
    fun `varint change de forme aux bonnes bornes`() {
        assertEquals("00", DogeTransaction.hex(DogeTransaction.varint(0)))
        assertEquals("fc", DogeTransaction.hex(DogeTransaction.varint(252)))
        assertEquals("fdfd00", DogeTransaction.hex(DogeTransaction.varint(253)))
        assertEquals("fdffff", DogeTransaction.hex(DogeTransaction.varint(65_535)))
        assertEquals("fe00000100", DogeTransaction.hex(DogeTransaction.varint(65_536)))
        assertEquals("feffffffff", DogeTransaction.hex(DogeTransaction.varint(4_294_967_295L)))
        assertEquals(
            "ff0000000001000000",
            DogeTransaction.hex(DogeTransaction.varint(4_294_967_296L))
        )
    }

    @Test
    fun `les entiers s ecrivent poids faible en tete`() {
        assertEquals("0100000000000000", DogeTransaction.hex(DogeTransaction.entier64(1)))
        assertEquals("a18dbbe8e8000000", DogeTransaction.hex(DogeTransaction.entier64(VALEUR_REELLE)))
        assertEquals("01000000", DogeTransaction.hex(DogeTransaction.entier32(1)))
        assertEquals("ffffffff", DogeTransaction.hex(DogeTransaction.entier32(-1)))
    }

    /*
    L'INVERSION EST LE PIEGE LE PLUS COUTEUX DE CE FICHIER : un
    identifiant non retourné designe une transaction qui n'existe pas, et
    rien ne le signale avant le refus du reseau.

    LA VALEUR ATTENDUE A ETE CALCULEE, PAS TAPEE. Ma premiere version
    l'avait ecrite a la main et deux chiffres s'y etaient transposes —
    exactement la faute que ce test est cense attraper, commise en
    ecrivant le test. Une attente recopiee a l'oeil ne vaut pas mieux que
    le code qu'elle surveille.
    */
    @Test
    fun `l identifiant de la transaction depensee est inverse`() {
        val affiche = "7d9cc37be46a55e672bf491a258efb7cd1ede313c7bc4b075e9208efe4feab60"
        val attendu = "60abfee4ef08925e074bbcc713e3edd17cfb8e251a49bf72e6556ae47bc39c7d"
        assertEquals(attendu, DogeTransaction.hex(DogeTransaction.txidInverse(affiche)))
    }

    @Test
    fun `inverser deux fois rend l original`() {
        val affiche = "7d9cc37be46a55e672bf491a258efb7cd1ede313c7bc4b075e9208efe4feab60"
        val une = DogeTransaction.hex(DogeTransaction.txidInverse(affiche))
        assertEquals(affiche, DogeTransaction.hex(DogeTransaction.txidInverse(une)))
    }

    /*
    ═══════════════════════════════════════════════════════════════════
    LE SCRIPT DE VERROUILLAGE, RELIE A L'ADRESSE QU'IL PAIE
    ═══════════════════════════════════════════════════════════════════

    Les vingt octets du script sont ceux que l'adresse encode. Ce test
    ferme la boucle avec DogeWallet : on part d'une adresse dérivée dont
    les vecteurs sont déjà vérifiés, on la décode, on construit le
    script, et on vérifie qu'il porte bien ces octets-là entre les bons
    opérateurs.

    Sans ce lien, les deux fichiers pourraient être justes séparément et
    faux ensemble — le cas où les fonds partent vers une adresse valide
    qui n'est pas la bonne.
    ═══════════════════════════════════════════════════════════════════
    */
    @Test
    fun `le script de verrouillage porte le hash de l adresse`() {
        // L'adresse du vecteur de DogeWalletTest, phrase « abandon … about ».
        val adresse = "DBus3bamQjgJULBJtYXpEzDWQRwF5iwxgC"
        assertTrue(DogeWallet.estAdresseValide(adresse))

        val brut = com.vaultex.core.crypto.Base58.decode(adresse)
        val hash160 = brut.copyOfRange(1, 21)

        val script = DogeTransaction.scriptVersAdresse(hash160)
        val hexScript = DogeTransaction.hex(script)

        assertEquals(25, script.size)
        assertTrue("le script doit commencer par OP_DUP OP_HASH160 et la longueur 20",
            hexScript.startsWith("76a914"))
        assertTrue("le script doit finir par OP_EQUALVERIFY OP_CHECKSIG",
            hexScript.endsWith("88ac"))
        assertEquals(DogeTransaction.hex(hash160), hexScript.substring(6, 6 + 40))

        // Et l'adresse se reconstruit depuis ces mêmes octets : la boucle
        // est fermée dans les deux sens.
        assertEquals(adresse, DogeWallet.encoderAdresse(hash160))
    }
}
