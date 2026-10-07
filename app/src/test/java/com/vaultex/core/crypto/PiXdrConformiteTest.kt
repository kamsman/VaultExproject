package com.vaultex.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ═══════════════════════════════════════════════════════════════════════
 * L'ENCODEUR DE TRANSACTIONS, CONFRONTÉ À LA RÉFÉRENCE
 * ═══════════════════════════════════════════════════════════════════════
 *
 * PiXdr construit les octets qu'on signe pour déplacer de l'argent. Une
 * erreur d'un seul octet y a deux issues possibles, et la seconde est la
 * mauvaise : soit le réseau refuse — bénin, visible tout de suite — soit
 * il accepte une transaction qui n'est pas celle qu'on croyait écrire.
 *
 * Ces vecteurs viennent du SDK Stellar officiel, que Pi partage :
 *
 *   https://github.com/stellar/go/blob/master/txnbuild/transaction_test.go
 *     TestHashHex (création de compte) et TestPayment (paiement)
 *
 * La clé de test est celle, PUBLIQUE, du dépôt Stellar — publiée pour
 * cet usage précis. Elle ne protège rien et n'a jamais rien protégé.
 *
 * ON COMPARE L'ENVELOPPE COMPLÈTE, SIGNATURE INCLUSE. C'est ce qui rend
 * ces tests concluants : l'enveloppe ne tombe juste que si la structure,
 * la somme de contrôle, l'identifiant de réseau, le choix de signer
 * l'empreinte plutôt que la charge, et l'indice de signataire sont TOUS
 * corrects. Comparer la seule structure laisserait passer une empreinte
 * calculée sur la mauvaise chose.
 * ═══════════════════════════════════════════════════════════════════════
 */
class PiXdrConformiteTest {

    private companion object {
        /** Phrase du réseau d'essai Stellar, celle des vecteurs. */
        const val PHRASE_ESSAI = "Test SDF Network ; September 2015"

        /** Clé de test publiée par Stellar (txnbuild/helpers_test.go). */
        const val CLE_DE_TEST_PUBLIEE =
            "5f0a67c6e172db14eaa585d093162fa3dd137223ff1d9ad8933579a8c5eadb3b"

        const val SOURCE = "GDQNY3PBOJOKYZSRMK2S7LHHGWZIUISD4QORETLMXEWXBI7KFZZMKTL3"
        const val DEST_CREATION = "GCCOBXW2XQNUSL467IEILE6MMCNRR66SSVL4YQADUNYYNUVREF3FIV2Z"
        const val DEST_PAIEMENT = "GB7BDSZU2Y27LYNLALKKALB52WS2IZWYBDGY6EQBLEED3TJOCVMZRH7H"

        /** Dix Pi, en stroops. */
        const val DIX = 100_000_000L
    }

    private val clePrivee: ByteArray = CLE_DE_TEST_PUBLIEE.deHex()
    private val clePublique: ByteArray = Ed25519Utils.publicKeyFromPrivate(clePrivee)

    /**
     * La clé de test correspond bien à l'adresse annoncée par Stellar.
     *
     * Premier maillon : si celui-ci lâche, tous les suivants échouent sans
     * dire pourquoi. On le vérifie dans le sens adresse → clé, le seul que
     * l'application expose — et c'est le sens qui compte, puisque c'est
     * celui qu'emprunte toute destination de paiement.
     */
    @Test fun `la cle de test correspond a l'adresse attendue`() {
        assertEquals(
            clePublique.enHex(),
            PiWallet.clePubliqueDeLAdresse(SOURCE).enHex()
        )
    }

    @Test fun `creation de compte identique au SDK Stellar`() {
        val tx = PiXdr.transaction(
            source = SOURCE,
            fraisStroops = 100L,
            numeroSequence = 9_605_939_170_639_898L,
            finValiditeEpochSec = 0L,          // « infini », comme le vecteur
            memo = PiXdr.Memo.Aucun,
            operations = listOf(PiXdr.Operation.CreationCompte(DEST_CREATION, DIX))
        )

        assertEquals(
            "1b3905ba8c3c0ecc68ae812f2d77f27c697195e8daf568740fc0f5662f65f759",
            PiXdr.empreinte(tx, PiXdr.idReseau(PHRASE_ESSAI)).enHex()
        )
        assertEquals(
            "AAAAAgAAAADg3G3hclysZlFitS+s5zWyiiJD5B0STWy5LXCj6i5yxQAAAGQAIiCNAAAAGgAA" +
                "AAEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAACE4N7avBtJL576CIWTzGCb" +
                "GPvSlVfMQAOjcYbSsSF2VAAAAAAF9eEAAAAAAAAAAAHqLnLFAAAAQB7MjKIwNEOTIjbEeV+Q" +
                "IjaQp/ZpV5qpbkbDaU54gkfdTOFOUxZq66lTS5FOfP5fmPIVD8InQ00Usy2SmzFC/wc=",
            PiXdr.enveloppeSignee(tx, PiXdr.idReseau(PHRASE_ESSAI), clePrivee, clePublique)
        )
    }

    @Test fun `paiement identique au SDK Stellar`() {
        val tx = PiXdr.transaction(
            source = SOURCE,
            fraisStroops = 100L,
            numeroSequence = 9_605_939_170_639_899L,
            finValiditeEpochSec = 0L,
            memo = PiXdr.Memo.Aucun,
            operations = listOf(PiXdr.Operation.Paiement(DEST_PAIEMENT, DIX))
        )

        assertEquals(
            "890197fa91a2142913c6d5c1a3e077cb74bfb97d28302f11971b7520a3267de8",
            PiXdr.empreinte(tx, PiXdr.idReseau(PHRASE_ESSAI)).enHex()
        )
        assertEquals(
            "AAAAAgAAAADg3G3hclysZlFitS+s5zWyiiJD5B0STWy5LXCj6i5yxQAAAGQAIiCNAAAAGwAA" +
                "AAEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAAQAAAAB+Ecs01jX14asC1KAsPdWl" +
                "pGbYCM2PEgFZCD3NLhVZmAAAAAAAAAAABfXhAAAAAAAAAAAB6i5yxQAAAEDXBkKYzThQi3/X" +
                "hJqGzfh/EjaAx/4zK3xBT1/JDNtdkk/kxn4qxHVx++xiV72lqZXxiphNwflA8C7mC8Dvim0E",
            PiXdr.enveloppeSignee(tx, PiXdr.idReseau(PHRASE_ESSAI), clePrivee, clePublique)
        )
    }

    /*
    ───────────────────────────────────────────────────────────────────────
    LES CHEMINS QUE LES VECTEURS OFFICIELS NE COUVRENT PAS
    ───────────────────────────────────────────────────────────────────────

    Les deux vecteurs ci-dessus n'ont ni mémo ni date limite : ce sont
    exactement les deux champs dont un envoi réel a besoin, et ils ne sont
    donc pas couverts.

    Les trois vecteurs suivants ont été produits par une implémentation
    indépendante, en Python, elle-même d'abord validée sur les deux
    vecteurs officiels ci-dessus — octet pour octet, signature comprise.
    Ce n'est pas la même preuve : elle n'atteste pas de la spécification,
    elle atteste que le portage Kotlin dit la même chose que l'original.
    C'est précisément le risque qui reste à ce stade.

    Le script est dans tools/pi-vecteurs.py, pour qu'on puisse les
    recalculer plutôt que de les croire.
    ───────────────────────────────────────────────────────────────────────
    */

    @Test fun `memo texte et date limite`() {
        assertEquals(
            "AAAAAgAAAADg3G3hclysZlFitS+s5zWyiiJD5B0STWy5LXCj6i5yxQAAAMgAIiCNAAAAGwAA" +
                "AAEAAAAAAAAAAAAAAABlU/EAAAAAAQAAAAdWYXVsdEV4AAAAAAEAAAAAAAAAAQAAAAB+Ecs0" +
                "1jX14asC1KAsPdWlpGbYCM2PEgFZCD3NLhVZmAAAAAAAAAAAALxhTgAAAAAAAAAB6i5yxQAA" +
                "AEARcMIUWLl0l9ARYLrDO1BfVCe3M6kgVEBG/r8z43LQdQolr6TPalj8RejO+mNzJ27hiv0K" +
                "jPmjBlWX4vFgRMYP",
            enveloppePi(PiXdr.Memo.Texte("VaultEx"))
        )
    }

    @Test fun `memo identifiant`() {
        assertEquals(
            "AAAAAgAAAADg3G3hclysZlFitS+s5zWyiiJD5B0STWy5LXCj6i5yxQAAAMgAIiCNAAAAGwAA" +
                "AAEAAAAAAAAAAAAAAABlU/EAAAAAAgAAAAAABMsvAAAAAQAAAAAAAAABAAAAAH4RyzTWNfXh" +
                "qwLUoCw91aWkZtgIzY8SAVkIPc0uFVmYAAAAAAAAAAAAvGFOAAAAAAAAAAHqLnLFAAAAQJha" +
                "a0+5qvyaWw+vAaHoKqJ0CWLmvn/7Tc9dgxOwQ67TT8JBGoMIJfbjivdP3sgm+xtdeo6JGCZh" +
                "vXigaFOdqgk=",
            enveloppePi(PiXdr.Memo.Identifiant(314_159L))
        )
    }

    /**
     * UN MÉMO ACCENTUÉ, parce que c'est là que le compte d'octets se
     * distingue du compte de caractères.
     *
     * « reçu café » fait neuf caractères et onze octets : le champ de
     * longueur doit dire onze, et le remplissage ajouter un octet nul pour
     * atteindre douze. Compter les caractères produirait une longueur de
     * neuf, et tout ce qui suit dans la transaction serait décalé — y
     * compris le montant.
     */
    @Test fun `memo accentue compte en octets et non en caracteres`() {
        assertEquals(9, "reçu café".length)
        assertEquals(11, "reçu café".toByteArray(Charsets.UTF_8).size)
        assertEquals(
            "AAAAAgAAAADg3G3hclysZlFitS+s5zWyiiJD5B0STWy5LXCj6i5yxQAAAMgAIiCNAAAAGwAA" +
                "AAEAAAAAAAAAAAAAAABlU/EAAAAAAQAAAAtyZcOndSBjYWbDqQAAAAABAAAAAAAAAAEAAAAA" +
                "fhHLNNY19eGrAtSgLD3VpaRm2AjNjxIBWQg9zS4VWZgAAAAAAAAAAAC8YU4AAAAAAAAAAeou" +
                "csUAAABA2RqNnKciv4GwznS8HJJWvrrYDKQXwunEWsZr/7w1knBMgy0xf5eobZVhQUaKsC4+" +
                "kkTQEqoXO25dd3agDpoqDQ==",
            enveloppePi(PiXdr.Memo.Texte("reçu café"))
        )
    }

    /** Vingt-huit octets passent ; vingt-neuf, non. */
    @Test fun `la limite du memo est de vingt-huit octets`() {
        assertTrue(PiXdr.memoTexteValide("a".repeat(28)))
        org.junit.Assert.assertFalse(PiXdr.memoTexteValide("a".repeat(29)))
        // Quatorze « é » font vingt-huit octets : à la limite exacte.
        assertTrue(PiXdr.memoTexteValide("é".repeat(14)))
        org.junit.Assert.assertFalse(PiXdr.memoTexteValide("é".repeat(15)))
    }

    /*
    ───────────────────────────────────────────────────────────────────────
    L'IDENTIFIANT DE RÉSEAU SÉPARE LES RÉSEAUX
    ───────────────────────────────────────────────────────────────────────

    C'est le seul mécanisme qui empêche une transaction signée ailleurs
    d'être rejouée sur le réseau principal du Pi. Sans lui, une signature
    produite sur un réseau d'essai — où l'on distribue des fonds
    gratuitement — vaudrait sur le réseau réel.
    */
    @Test fun `deux reseaux donnent deux empreintes`() {
        val tx = PiXdr.transaction(
            SOURCE, 100L, 42L, 1_700_000_000L, PiXdr.Memo.Aucun,
            listOf(PiXdr.Operation.Paiement(DEST_PAIEMENT, DIX))
        )
        assertNotEquals(
            PiXdr.empreinte(tx, PiXdr.idReseau("Pi Network")).enHex(),
            PiXdr.empreinte(tx, PiXdr.idReseau("Pi Testnet")).enHex()
        )
    }

    @Test fun `l'identifiant du reseau Pi est l'empreinte de sa phrase`() {
        assertEquals(
            "add10ed9108840bc4c8ee9db7eda08bf871e6b1f3383153f2f6b765ae83ee4cd",
            PiXdr.idReseau("Pi Network").enHex()
        )
    }

    /*
    ───────────────────────────────────────────────────────────────────────
    LA CONVERSION D'UN MONTANT ÉCRIT À LA MAIN
    ───────────────────────────────────────────────────────────────────────

    C'est le seul endroit où un chiffre tapé par quelqu'un devient un
    nombre d'unités indivisibles. Un arrondi silencieux ici envoie un
    montant que personne n'a écrit.
    */
    @Test fun `conversion des montants en stroops`() {
        assertEquals(10_000_000L, PiXdr.stroopsDepuisTexte("1"))
        assertEquals(10_000_000L, PiXdr.stroopsDepuisTexte("1.0"))
        assertEquals(1L, PiXdr.stroopsDepuisTexte("0.0000001"))
        assertEquals(12_345_678L, PiXdr.stroopsDepuisTexte("1.2345678"))
        // La virgule décimale française est acceptée, et l'espace ignoré.
        assertEquals(15_000_000L, PiXdr.stroopsDepuisTexte("1,5"))
        assertEquals(15_000_000L, PiXdr.stroopsDepuisTexte(" 1,5 "))
    }

    @Test fun `une huitieme decimale est refusee plutot qu'arrondie`() {
        // 0,00000005 Pi n'existe pas : le refus est la seule réponse honnête.
        assertNull(PiXdr.stroopsDepuisTexte("0.00000005"))
        assertNull(PiXdr.stroopsDepuisTexte("1.23456789"))
    }

    @Test fun `les montants absurdes sont refuses`() {
        assertNull(PiXdr.stroopsDepuisTexte(""))
        assertNull(PiXdr.stroopsDepuisTexte("   "))
        assertNull(PiXdr.stroopsDepuisTexte("0"))
        assertNull(PiXdr.stroopsDepuisTexte("-1"))
        assertNull(PiXdr.stroopsDepuisTexte("abc"))
        assertNull(PiXdr.stroopsDepuisTexte("1e9999"))
        // Au-delà de ce qu'un entier de 64 bits peut porter.
        assertNull(PiXdr.stroopsDepuisTexte("1000000000000"))
    }

    /** Une adresse invalide ne doit JAMAIS produire une destination. */
    @Test(expected = IllegalArgumentException::class)
    fun `une destination invalide est refusee a l'encodage`() {
        PiXdr.transaction(
            SOURCE, 100L, 1L, 1_700_000_000L, PiXdr.Memo.Aucun,
            listOf(PiXdr.Operation.Paiement("GBADRESSEQUINEXISTEPAS", DIX))
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `un montant nul est refuse a l'encodage`() {
        PiXdr.transaction(
            SOURCE, 100L, 1L, 1_700_000_000L, PiXdr.Memo.Aucun,
            listOf(PiXdr.Operation.Paiement(DEST_PAIEMENT, 0L))
        )
    }

    // ─── Outillage ───────────────────────────────────────────────────

    /** Enveloppe sur le réseau Pi, paramètres fixes, pour varier le mémo. */
    private fun enveloppePi(memo: PiXdr.Memo): String {
        val tx = PiXdr.transaction(
            source = SOURCE,
            fraisStroops = 200L,
            numeroSequence = 9_605_939_170_639_899L,
            finValiditeEpochSec = 1_700_000_000L,
            memo = memo,
            operations = listOf(PiXdr.Operation.Paiement(DEST_PAIEMENT, 12_345_678L))
        )
        return PiXdr.enveloppeSignee(
            tx, PiXdr.idReseau("Pi Network"), clePrivee, clePublique
        )
    }

    private fun ByteArray.enHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.deHex(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
