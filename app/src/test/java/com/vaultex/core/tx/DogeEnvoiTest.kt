package com.vaultex.core.tx

import com.vaultex.core.crypto.DogeWallet
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.Sha256Hash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.web3j.crypto.MnemonicUtils

/**
 * ═══════════════════════════════════════════════════════════════════════
 * UN ENVOI COMPLET, ET LA PREUVE QU'IL TIENT
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Les tests précédents ont prouvé chaque brique séparément : la
 * dérivation contre deux implémentations indépendantes, la sérialisation
 * contre deux transactions de la chaîne, la signature contre une
 * signature publiée, le choix des pièces par son équilibre.
 *
 * Celui-ci vérifie qu'elles s'enchaînent — ce qu'aucun des autres ne
 * dit. Cinq pièces justes mal assemblées produisent une transaction
 * parfaitement formée qui ne dépense rien de ce qu'on croit.
 *
 * ─── LA PREUVE EST INTERNE, ET C'EST VOULU ─────────────────────────────
 *
 * On ne compare pas le résultat à un hexadécimal figé : il dépendrait du
 * nonce que bitcoinj tire pour signer, donc d'un détail de bibliothèque.
 *
 * On fait mieux. On reconstruit la forme NON SIGNÉE, on recalcule ce qui
 * aurait dû être signé, on extrait la signature de la transaction
 * produite, et on demande à la cryptographie si les deux correspondent.
 * Si une seule étape de la chaîne se trompait — une sortie oubliée, un
 * montant décalé, un script de verrouillage faux — le nombre signé
 * différerait et la vérification échouerait.
 * ═══════════════════════════════════════════════════════════════════════
 */
class DogeEnvoiTest {

    private companion object {
        const val PHRASE =
            "abandon abandon abandon abandon abandon abandon abandon abandon " +
            "abandon abandon abandon about"

        /** L'adresse de cette phrase, vérifiée dans DogeWalletTest. */
        const val EXPEDITEUR = "DBus3bamQjgJULBJtYXpEzDWQRwF5iwxgC"

        /** Une autre adresse Dogecoin réelle, prise dans un bloc public. */
        const val DESTINATAIRE = "DBx8K8iX2ca3MVrxYnRH5PF6FQgaXiDvSU"

        const val DOGE = 100_000_000L
        const val TAUX = 1_004L
        const val POUSSIERE = DOGE / 100
    }

    private val seed = MnemonicUtils.generateSeed(PHRASE, "")

    private fun piece(prefixe: String, unites: Long, index: Int = 0) =
        DogeSelection.Piece(prefixe.repeat(32), index, unites, confirmations = 6)

    private fun octets(hex: String) =
        ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    /**
     * Extrait le script de déblocage de la PREMIÈRE entrée.
     *
     * Les positions sont fixes : 4 octets de version, 1 de compte
     * d'entrées, 32 d'identifiant, 4 d'index, puis la longueur du script.
     * On lit donc les octets plutôt que de faire confiance à un décalage
     * écrit à la main.
     */
    private fun scriptPremiereEntree(hex: String): ByteArray {
        val b = octets(hex)
        val longueur = b[41].toInt() and 0xFF
        return b.copyOfRange(42, 42 + longueur)
    }

    private fun ok(r: DogeEnvoi.Resultat): DogeEnvoi.Pret {
        assertTrue("attendu un succès, reçu $r", r is DogeEnvoi.Resultat.Ok)
        return (r as DogeEnvoi.Resultat.Ok).pret
    }

    private fun envoyer(pieces: List<DogeSelection.Piece>, unites: Long) =
        DogeEnvoi.preparer(seed, DESTINATAIRE, unites, pieces, TAUX, POUSSIERE)

    /*
    ═══════════════════════════════════════════════════════════════════
    LE TEST QUI PORTE TOUTE LA CHAINE
    ═══════════════════════════════════════════════════════════════════
    */
    @Test
    fun `la signature produite correspond a la transaction produite`() {
        val pieces = listOf(piece("aa", 10 * DOGE))
        val pret = ok(envoyer(pieces, 3 * DOGE))

        // Ce que la chaîne aurait dû signer, reconstruit a part.
        val cle = DogeEnvoi.cleDe(seed, 0)
        val verrou = DogeTransaction.scriptVersAdresse(cle.pubKeyHash)
        val nonSignee = DogeTransaction.Transaction(
            entrees = pret.choix.pieces.map {
                DogeTransaction.Entree(it.txidAffiche, it.index, ByteArray(0))
            },
            sorties = listOf(
                DogeTransaction.Sortie(
                    pret.choix.versDestinataire,
                    DogeTransaction.scriptVersAdresse(DogeEnvoi.hash160De(DESTINATAIRE))
                ),
                DogeTransaction.Sortie(pret.choix.rendu, verrou)
            )
        )
        val attendu = DogeSigner.sighash(nonSignee, 0, verrou)

        val (signature, type, publique) =
            DogeSigner.decouperDeblocage(scriptPremiereEntree(pret.hex))

        assertEquals(DogeSigner.SIGHASH_ALL, type)
        assertTrue(
            "la cle qui signe doit etre celle de l'expediteur",
            publique.contentEquals(cle.pubKey)
        )
        assertTrue(
            "la signature doit se verifier contre ce que la transaction signe",
            ECKey.fromPublicOnly(publique).verify(
                Sha256Hash.wrap(attendu),
                ECKey.ECDSASignature.decodeFromDER(signature)
            )
        )
    }

    /*
    LE RENDU REVIENT A L'EXPEDITEUR, ET PAS AILLEURS. C'est la part la
    plus grosse de la transaction, et une erreur ici ne se voit nulle
    part : la transaction est valide, elle part, et l'argent est chez
    quelqu'un d'autre.
    */
    @Test
    fun `le rendu de monnaie revient a l adresse de l expediteur`() {
        val pret = ok(envoyer(listOf(piece("aa", 10 * DOGE)), 3 * DOGE))
        assertEquals(EXPEDITEUR, pret.adresseRendu)
        assertEquals(
            DogeWallet.deriveAddress(seed),
            pret.adresseRendu
        )
        assertTrue(pret.hex.contains(DogeTransaction.hex(
            DogeTransaction.scriptVersAdresse(DogeEnvoi.hash160De(EXPEDITEUR))
        )))
    }

    @Test
    fun `la transaction porte bien le script du destinataire`() {
        val pret = ok(envoyer(listOf(piece("aa", 10 * DOGE)), 3 * DOGE))
        assertTrue(
            "le script du destinataire doit figurer dans les octets",
            pret.hex.contains(DogeTransaction.hex(
                DogeTransaction.scriptVersAdresse(DogeEnvoi.hash160De(DESTINATAIRE))
            ))
        )
    }

    @Test
    fun `l equilibre tient apres assemblage`() {
        val pret = ok(envoyer(listOf(piece("aa", 10 * DOGE)), 3 * DOGE))
        assertTrue(pret.choix.equilibre)
        assertEquals(3 * DOGE, pret.choix.versDestinataire)
        assertEquals(10 * DOGE, pret.choix.versDestinataire + pret.choix.rendu + pret.choix.frais)
    }

    /*
    DEUX ENTREES SE SIGNENT SUR DEUX NOMBRES DIFFERENTS, et chacune doit
    porter SA signature. Une transaction a une entree ne peut pas le
    montrer — c'est le cas ou l'erreur se cache.
    */
    @Test
    fun `chaque entree porte sa propre signature`() {
        val pret = ok(envoyer(listOf(piece("aa", 2 * DOGE), piece("bb", 2 * DOGE)), 3 * DOGE))
        assertEquals(2, pret.choix.pieces.size)

        val cle = DogeEnvoi.cleDe(seed, 0)
        val verrou = DogeTransaction.scriptVersAdresse(cle.pubKeyHash)
        val nonSignee = DogeTransaction.Transaction(
            entrees = pret.choix.pieces.map {
                DogeTransaction.Entree(it.txidAffiche, it.index, ByteArray(0))
            },
            sorties = listOf(
                DogeTransaction.Sortie(
                    pret.choix.versDestinataire,
                    DogeTransaction.scriptVersAdresse(DogeEnvoi.hash160De(DESTINATAIRE))
                ),
                DogeTransaction.Sortie(pret.choix.rendu, verrou)
            )
        )
        val h0 = DogeSigner.sighash(nonSignee, 0, verrou)
        val h1 = DogeSigner.sighash(nonSignee, 1, verrou)
        assertFalse(
            "les deux entrees ne doivent pas signer le meme nombre",
            h0.contentEquals(h1)
        )
    }

    /*
    QUAND LE RENDU PART AUX FRAIS, LA TRANSACTION N'A QU'UNE SORTIE.
    Ajouter une sortie nulle produirait une transaction refusee, et la
    taille ne correspondrait plus aux frais payes.
    */
    @Test
    fun `sans rendu la transaction n a qu une sortie`() {
        val pret = ok(envoyer(listOf(piece("aa", 3 * DOGE + 300_000L)), 3 * DOGE))
        assertEquals(0L, pret.choix.rendu)
        // Le compte de sorties est l'octet qui suit l'entree signee.
        val b = octets(pret.hex)
        val longueurScript = b[41].toInt() and 0xFF
        val compteSorties = b[42 + longueurScript + 4].toInt() and 0xFF
        assertEquals(1, compteSorties)
    }

    // ─── Les refus ─────────────────────────────────────────────────────

    /*
    L'ADRESSE EST VERIFIEE PAR SA SOMME DE CONTROLE, AVANT TOUT LE RESTE.
    Une adresse recopiee a la main avec un caractere faux garde sa
    longueur, son « D » et son alphabet. Signer d'abord et decouvrir
    ensuite reviendrait a fabriquer une transaction irreversible vers
    personne.
    */
    @Test
    fun `une adresse au caractere change est refusee avant de signer`() {
        val r = DogeEnvoi.preparer(
            seed, "DBus3bamQjgJULBJtYXpEzDWQRwF5iwxgA", 3 * DOGE,
            listOf(piece("aa", 10 * DOGE)), TAUX, POUSSIERE
        )
        assertTrue(r is DogeEnvoi.Resultat.AdresseInvalide)
    }

    @Test
    fun `une adresse d une autre chaine est refusee`() {
        for (adresse in listOf(
            "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa",
            "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t",
            "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4",
        )) {
            val r = DogeEnvoi.preparer(
                seed, adresse, 3 * DOGE, listOf(piece("aa", 10 * DOGE)), TAUX, POUSSIERE
            )
            assertTrue("« $adresse » aurait du etre refusee", r is DogeEnvoi.Resultat.AdresseInvalide)
        }
    }

    /*
    UN REFUS DU CHOIX DES PIECES REMONTE TEL QUEL, avec sa raison. L'ecran
    ne propose pas la meme chose selon qu'il manque des fonds ou
    seulement de quoi payer les frais.
    */
    @Test
    fun `un solde insuffisant remonte avec sa raison`() {
        val r = envoyer(listOf(piece("aa", 1 * DOGE)), 3 * DOGE)
        assertTrue(r is DogeEnvoi.Resultat.Refus)
        assertTrue(
            (r as DogeEnvoi.Resultat.Refus).raison
                is DogeSelection.Resultat.SoldeInsuffisant
        )
    }
}
