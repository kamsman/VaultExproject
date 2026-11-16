package com.vaultex.core.tx

import org.bitcoinj.core.ECKey
import org.bitcoinj.core.Sha256Hash
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ═══════════════════════════════════════════════════════════════════════
 * LA SIGNATURE, PROUVÉE CONTRE UNE TRANSACTION RÉELLE — SANS RÉSEAU
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Je croyais que signer était la partie qu'on ne pourrait vérifier qu'en
 * envoyant pour de vrai. C'est faux, et la méthode tient en une phrase :
 *
 *   une transaction déjà publiée porte sa propre signature ; si notre
 *   calcul de ce qui est signé est juste, cette signature se vérifie
 *   contre lui.
 *
 * S'il est faux, elle échoue. Il n'y a ni demi-mesure ni coïncidence
 * possible : une signature ECDSA ne se vérifie contre un nombre erroné
 * avec aucune probabilité utile.
 *
 * ─── LE VECTEUR ────────────────────────────────────────────────────────
 *
 * f4975a3e9e5f5f28cd084acc6fe32e747de1359018bc5d037f97965acf4e00fa,
 * bloc 6460312 du réseau Dogecoin, récupérée avec son hexadécimal par
 * `tools/doge-sondes.sh --vivante`. Une dépense ordinaire : une entrée
 * P2PKH, deux sorties P2PKH — dont le rendu de monnaie vers l'expéditeur
 * — et 91 200 000 unités de frais.
 *
 * ─── CE QUI N'A PAS EU BESOIN D'ÊTRE TÉLÉCHARGÉ ────────────────────────
 *
 * Le script de verrouillage de la sortie dépensée. Pour du P2PKH il se
 * reconstruit depuis la clé publique que porte le script de déblocage :
 * `76a914` + HASH160(clé) + `88ac`. Un appel réseau de moins, et surtout
 * un test qui ne dépend d'aucun service.
 *
 * ─── CE QUE CES VECTEURS ATTRAPENT ─────────────────────────────────────
 *
 * Les cinq fautes qui produisent une transaction parfaitement formée que
 * personne n'acceptera : signer la transaction telle quelle au lieu de la
 * copie modifiée, oublier de vider les autres entrées, omettre les quatre
 * octets de type ou les écrire sur un seul, ne hacher qu'une fois.
 *
 * ─── CE QU'ILS NE PROUVENT PAS ─────────────────────────────────────────
 *
 * Que le réseau Dogecoin accepte une transaction que NOUS fabriquons. Ils
 * prouvent que nous calculons le même nombre que le portefeuille qui a
 * signé celle-ci. C'est beaucoup, et ce n'est pas tout : il restera à
 * envoyer pour de vrai, avec un montant qu'on accepte de perdre.
 * ═══════════════════════════════════════════════════════════════════════
 */
class DogeSignerTest {

    private companion object {
        /** La transaction réelle, telle que la chaîne la porte. */
        const val HEX_REEL =
            "0100000001" +
            "54714f4371e7a24decf760a4d6778c1cf5741a307721e4c970844b8478e8d195" +
            "02000000" +
            "6a" +
            "47304402206fe04d44473fa22b31c9d0c9a077dd857b45c5a00d0c72b9f1cd24d0f5614ff0" +
            "02202b546eeba292b9911ee1e9ffe10cdb64d67ced3cd7fa389a817f14888a9e533d01" +
            "210310a832061effd93beace05a17633addcc17ad21e58d57868025530f1c0ea74f7" +
            "ffffffff" +
            "02" +
            "8a9f8f5a19000000" + "19" + "76a914f6494bf17e7d4f75dab3bc71eaf919d68281cf3988ac" +
            "8abd2625a6140000" + "19" + "76a9144ab5c91866ef9ac85bd0835345b86fceff230f6388ac" +
            "00000000"

        /** L'identifiant de la transaction dépensée, tel qu'un explorateur l'affiche. */
        const val PREVOUT_AFFICHE =
            "95d1e878844b8470c9e42177301a74f51c8c77d6a460f7ec4da2e771434f7154"

        /**
         * Le nombre que l'entrée 0 a signé, calculé hors de ce code par une
         * implémentation écrite de zéro, puis confirmé par le fait que la
         * signature publiée se vérifie contre lui.
         */
        const val SIGHASH_ATTENDU =
            "2cb9ac7a61ce32739acaee88878ef5b5eb1a9998bd293b9565f83402c2a07645"

        const val SCRIPT_DEBLOCAGE =
            "47304402206fe04d44473fa22b31c9d0c9a077dd857b45c5a00d0c72b9f1cd24d0f5614ff0" +
            "02202b546eeba292b9911ee1e9ffe10cdb64d67ced3cd7fa389a817f14888a9e533d01" +
            "210310a832061effd93beace05a17633addcc17ad21e58d57868025530f1c0ea74f7"

        const val CLE_PUBLIQUE =
            "0310a832061effd93beace05a17633addcc17ad21e58d57868025530f1c0ea74f7"

        const val SORTIE_1 = 108_893_544_330L
        const val SORTIE_2 = 22_703_820_422_538L
        const val VALEUR_ENTREE = 22_812_805_166_868L
        const val FRAIS = 91_200_000L
    }

    private fun octets(hex: String) =
        ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    /** La transaction réelle, reconstruite champ par champ. */
    private fun transactionReelle(scriptEntree: ByteArray) =
        DogeTransaction.Transaction(
            entrees = listOf(
                DogeTransaction.Entree(
                    txidAffiche = PREVOUT_AFFICHE,
                    index = 2,
                    script = scriptEntree,
                    sequence = 0xFFFFFFFFL
                )
            ),
            sorties = listOf(
                DogeTransaction.Sortie(
                    SORTIE_1, octets("76a914f6494bf17e7d4f75dab3bc71eaf919d68281cf3988ac")
                ),
                DogeTransaction.Sortie(
                    SORTIE_2, octets("76a9144ab5c91866ef9ac85bd0835345b86fceff230f6388ac")
                )
            )
        )

    @Test
    fun `la transaction signee se reserialise octet pour octet`() {
        val tx = transactionReelle(octets(SCRIPT_DEBLOCAGE))
        assertEquals(HEX_REEL, DogeTransaction.hex(DogeTransaction.serialiser(tx)))
        assertEquals(225, DogeTransaction.serialiser(tx).size)
    }

    @Test
    fun `le script de deblocage se decoupe en signature et cle`() {
        val (sig, type, cle) = DogeSigner.decouperDeblocage(octets(SCRIPT_DEBLOCAGE))
        assertEquals(DogeSigner.SIGHASH_ALL, type)
        assertEquals(CLE_PUBLIQUE, DogeTransaction.hex(cle))
        assertEquals(0x30, sig[0].toInt() and 0xFF)   // en-tête DER
    }

    /*
    ═══════════════════════════════════════════════════════════════════
    LE TEST QUI PORTE TOUT LE RESTE
    ═══════════════════════════════════════════════════════════════════

    Le script de verrouillage dépensé est reconstruit depuis la clé
    publique, la transaction est ramenée à sa forme « avant signature »,
    et le nombre calculé est comparé à celui qu'une implémentation
    indépendante a produit.

    Si une seule des cinq fautes citées en tête de DogeSigner était
    commise, ce nombre serait différent — et le test suivant, qui
    vérifie la vraie signature contre lui, échouerait aussi.
    ═══════════════════════════════════════════════════════════════════
    */
    @Test
    fun `le sighash calcule est celui que la transaction a signe`() {
        val (_, _, cle) = DogeSigner.decouperDeblocage(octets(SCRIPT_DEBLOCAGE))
        val hash160 = ECKey.fromPublicOnly(cle).pubKeyHash
        val verrou = DogeTransaction.scriptVersAdresse(hash160)

        // Avant signature, le script de l'entrée est vide.
        val tx = transactionReelle(ByteArray(0))
        val h = DogeSigner.sighash(tx, 0, verrou)

        assertEquals(SIGHASH_ATTENDU, DogeTransaction.hex(h))
    }

    /*
    LA PREUVE PROPREMENT DITE. On ne compare plus a une valeur notee : on
    demande a la cryptographie si la signature REELLE correspond au nombre
    que NOUS avons calcule. Elle ne peut dire oui que si le calcul est
    juste.
    */
    @Test
    fun `la signature publiee se verifie contre notre sighash`() {
        val (sig, _, cle) = DogeSigner.decouperDeblocage(octets(SCRIPT_DEBLOCAGE))
        val publique = ECKey.fromPublicOnly(cle)
        val verrou = DogeTransaction.scriptVersAdresse(publique.pubKeyHash)

        val h = DogeSigner.sighash(transactionReelle(ByteArray(0)), 0, verrou)

        assertTrue(
            "la signature de la transaction publiee doit se verifier contre notre sighash",
            publique.verify(Sha256Hash.wrap(h), ECKey.ECDSASignature.decodeFromDER(sig))
        )
    }

    /*
    ET ELLE NE SE VERIFIE PAS CONTRE N'IMPORTE QUOI. Sans ce
    contre-essai, un `verify` qui rendrait toujours vrai — cle mal
    chargee, bibliotheque mal appelee — ferait passer le test du dessus
    sans rien prouver.
    */
    @Test
    fun `la signature ne se verifie pas contre un autre nombre`() {
        val (sig, _, cle) = DogeSigner.decouperDeblocage(octets(SCRIPT_DEBLOCAGE))
        val publique = ECKey.fromPublicOnly(cle)
        val faux = octets(SIGHASH_ATTENDU).copyOf()
        faux[0] = (faux[0].toInt() xor 1).toByte()   // un seul bit change

        assertTrue(
            "un sighash faux doit etre refuse",
            !publique.verify(Sha256Hash.wrap(faux), ECKey.ECDSASignature.decodeFromDER(sig))
        )
    }

    /*
    LES AUTRES ENTREES DOIVENT ETRE VIDEES. Sur une transaction a UNE
    entree — celle du vecteur — l'oubli ne se voit jamais. Ce test
    fabrique donc une seconde entree porteuse d'un script, et verifie que
    sa presence ne change pas le nombre signe pour la premiere.
    */
    @Test
    fun `le script des autres entrees ne change pas le sighash`() {
        val verrou = DogeTransaction.scriptVersAdresse(
            ECKey.fromPublicOnly(octets(CLE_PUBLIQUE)).pubKeyHash
        )
        val base = transactionReelle(ByteArray(0))

        val deuxieme = { script: ByteArray ->
            base.copy(
                entrees = base.entrees + DogeTransaction.Entree(
                    txidAffiche = "a".repeat(64), index = 0, script = script
                )
            )
        }
        val sansScript = DogeSigner.sighash(deuxieme(ByteArray(0)), 0, verrou)
        val avecScript = DogeSigner.sighash(deuxieme(octets(SCRIPT_DEBLOCAGE)), 0, verrou)

        assertArrayEquals(
            "le script d'une AUTRE entree ne doit pas entrer dans le nombre signe",
            sansScript, avecScript
        )
    }

    /*
    LES FRAIS SE DEDUISENT, ILS NE S'ANNONCENT PAS. Aucun champ ne les
    porte : ce sont les unites qui entrent moins celles qui sortent. Une
    sortie oubliee devient donc des frais, et une transaction de dix mille
    DOGE dont on omet le rendu de monnaie offre tout au mineur.

    Le vecteur reel le chiffre : 91 200 000 unites, soit 0,912 DOGE, pour
    225 octets.
    */
    @Test
    fun `les frais sont la difference entre entrees et sorties`() {
        assertEquals(FRAIS, VALEUR_ENTREE - (SORTIE_1 + SORTIE_2))
    }

    /*
    UNE SIGNATURE QUE NOUS PRODUISONS DOIT ETRE CANONIQUE. Une signature
    au second nombre « haut » est mathematiquement valide et refusee par
    le reseau — elle passe donc toutes les verifications locales et
    echoue a la diffusion, ou plus rien n'explique pourquoi.
    */
    @Test
    fun `nos propres signatures sont canoniques`() {
        val cle = ECKey.fromPrivate(java.math.BigInteger.valueOf(0x0DECADE1L))
        val verrou = DogeTransaction.scriptVersAdresse(cle.pubKeyHash)
        val tx = transactionReelle(ByteArray(0))

        val script = DogeSigner.signerEntree(tx, 0, verrou, cle)
        val (sig, type, pub) = DogeSigner.decouperDeblocage(script)

        assertEquals(DogeSigner.SIGHASH_ALL, type)
        assertArrayEquals(cle.pubKey, pub)
        assertTrue(
            "la signature doit etre canonique",
            ECKey.ECDSASignature.decodeFromDER(sig).isCanonical
        )
        assertTrue(
            "notre signature doit se verifier contre notre propre sighash",
            cle.verify(
                Sha256Hash.wrap(DogeSigner.sighash(tx, 0, verrou)),
                ECKey.ECDSASignature.decodeFromDER(sig)
            )
        )
    }
}
