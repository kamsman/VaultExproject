package com.vaultex.core.validation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ═══════════════════════════════════════════════════════════════════════
 * CE QU'ON PROPOSE DOIT ÊTRE CE QU'ON SAIT VÉRIFIER
 * ═══════════════════════════════════════════════════════════════════════
 *
 * `AddressValidator.isValid` finit par `else -> address.length >= 26` :
 * pour une chaîne inconnue, il laisse passer. C'est le bon repli — il ne
 * bloque pas une monnaie nouvelle — mais il veut dire qu'un écran qui
 * PROPOSE une chaîne absente du `when` n'offre aucune vérification, tout
 * en ayant l'air d'en offrir une.
 *
 * VU LE 26 NOVEMBRE 2026 : le carnet d'adresses proposait six chaînes
 * écrites à la main, DOGE absent, alors que le validateur savait le
 * vérifier depuis le début. On offrait moins que ce qu'on savait faire.
 *
 * ─── CE QUE CE FICHIER TIENT ───────────────────────────────────────────
 *
 * Que chaque entrée de `CHAINES_VERIFIABLES` soit REELLEMENT reconnue par
 * `isValid` — c'est-à-dire qu'une adresse manifestement fausse y soit
 * refusée. Sans ce contrôle, ajouter un symbole à la liste suffirait à le
 * faire apparaître dans les écrans sans que personne ne vérifie rien.
 * ═══════════════════════════════════════════════════════════════════════
 */
class ChainesVerifiablesTest {

    /*
    UNE ADRESSE QUI N'EST D'AUCUNE CHAINE. Assez longue pour passer le
    repli `length >= 26` : c'est tout l'intérêt. Si une chaîne de la liste
    l'accepte, c'est qu'elle tombe dans le `else` — donc qu'elle n'est pas
    réellement vérifiée.
    */
    private val nImporteQuoi = "ceci-n-est-pas-une-adresse-de-chaine-connue-0000000000"

    @Test
    fun `chaque chaine proposee est reellement verifiee`() {
        for (chaine in AddressValidator.CHAINES_VERIFIABLES) {
            assertFalse(
                "« $chaine » est proposee a l'utilisateur mais tombe dans le repli " +
                    "de isValid : n'importe quelle chaine de 26 caracteres y passerait, " +
                    "et l'ecran laisserait enregistrer une adresse fausse.",
                AddressValidator.isValid(nImporteQuoi, chaine)
            )
        }
    }

    /*
    LE CAS QUI A MOTIVE LA LISTE, nomme a part pour qu'un echec se lise
    tout de suite.
    */
    @Test
    fun `DOGE est proposee, et une vraie adresse DOGE passe`() {
        assertTrue(
            "DOGE manque de CHAINES_VERIFIABLES : le carnet d'adresses ne la proposera pas.",
            "DOGE" in AddressValidator.CHAINES_VERIFIABLES
        )
        /*
        ADRESSE CALCULEE, PAS RECOPIEE. La tentation etait de reprendre
        celle de l'envoi reel du 26 novembre, lue sur une capture d'ecran.
        Elle y faisait 32 caracteres — tronquee par l'affichage, puisqu'une
        adresse Dogecoin en fait 34. Elle aurait fait echouer ce test pour
        une raison sans rapport avec le code.

        Celle-ci est construite selon la regle que `DogeWallet` applique :
        octet de version 0x1E, vingt octets de HASH160, puis les quatre
        premiers octets du double SHA-256 en somme de controle. Verifiee en
        la redecodant.
        */
        assertTrue(
            AddressValidator.isValid("DLebZqYFC7vG8HoLR7tyyWog78AC2N7MFm", "DOGE")
        )
    }

    /*
    ET LA LISTE NE DOIT PAS SE VIDER NI DOUBLONNER. Une liste reduite a
    rien par une refonte ferait disparaitre le selecteur sans que le test
    ci-dessus ne bronche : il passerait, faute de cas a examiner.
    */
    @Test
    fun `la liste est pleine et sans doublon`() {
        val l = AddressValidator.CHAINES_VERIFIABLES
        assertTrue("liste vide ou trop courte : ${l.size}", l.size >= 8)
        assertTrue("doublon dans la liste : $l", l.size == l.toSet().size)
    }
}
