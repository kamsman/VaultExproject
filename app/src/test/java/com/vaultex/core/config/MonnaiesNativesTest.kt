package com.vaultex.core.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ═══════════════════════════════════════════════════════════════════════
 * UNE MONNAIE OUBLIÉE ICI FAIT MENTIR LE TOTAL D'UN ENVOI
 * ═══════════════════════════════════════════════════════════════════════
 *
 * VU SUR L'APPAREIL le 26 novembre 2026, écran « Envoyer DOGE » :
 *
 *     Solde           53,06554788 DOGE
 *     Montant         52
 *     Frais réseau     0,00455816 DOGE
 *     Total débité    52 DOGE          ← les frais n'y étaient pas
 *
 * `isNative` était une liste écrite à la main dans `SendScreen`, et
 * Dogecoin a été ajouté à l'application après qu'elle a été écrite.
 *
 * UNE LISTE À QUI IL MANQUE UNE ENTRÉE NE SE PLAINT JAMAIS : elle rend
 * `false`, ce qui est une réponse parfaitement valide pour le
 * compilateur. Rien ne lève, rien n'avertit — seul le chiffre à l'écran
 * est faux, sur la ligne où l'on appuie « Continuer ».
 *
 * ─── CE QUE CE FICHIER TIENT ───────────────────────────────────────────
 *
 * Que chaque monnaie dont VaultEx sait ENVOYER paie bien ses frais dans
 * la bonne unité. La liste ci-dessous est à tenir à jour au même moment
 * que celle du code : c'est le seul couple d'endroits qui reste, et ce
 * test est ce qui les attache l'un à l'autre.
 * ═══════════════════════════════════════════════════════════════════════
 */
class MonnaiesNativesTest {

    /*
    LES MONNAIES QUE L'APPLICATION SAIT ENVOYER, chacune sur sa propre
    chaîne. Ce sont celles dont les frais se paient dans la monnaie
    elle-même — il n'existe pas d'autre jeton pour les payer.
    */
    private val natives = listOf("BTC", "ETH", "BNB", "SOL", "TRX", "DOGE")

    @Test
    fun `chaque monnaie de chaine paie ses propres frais`() {
        for (m in natives) {
            assertTrue(
                "« $m » manque de MonnaiesNatives : le total d'un envoi n'y " +
                    "ajouterait pas les frais reseau, et l'ecran annoncerait " +
                    "un chiffre inferieur a ce qui quitte le portefeuille.",
                MonnaiesNatives.estNative(m)
            )
        }
    }

    /*
    LE CAS QUI A CASSÉ, NOMMÉ À PART. Les tests en boucle se lisent mal
    quand ils tombent : celui-ci dit tout de suite de quoi il s'agit.
    */
    @Test
    fun `DOGE est native — c'est l'oubli qui a fausse le total`() {
        assertTrue(MonnaiesNatives.estNative("DOGE"))
    }

    /*
    ET L'INVERSE COMPTE AUTANT. Un jeton déclaré natif par erreur ferait
    ajouter ses frais au mauvais solde : l'écran retrancherait des USDT
    pour payer du gaz qui se paie en BNB, et le total serait faux dans
    l'autre sens.
    */
    @Test
    fun `un jeton pose sur une chaine n est pas natif`() {
        for (m in listOf("USDT", "USDT-ETH", "USDT-BNB", "USDT-SOL", "USDC", "PI")) {
            assertFalse(
                "« $m » ne paie pas ses frais dans sa propre unite.",
                MonnaiesNatives.estNative(m)
            )
        }
    }

    /*
    LA CASSE ET LES ESPACES. Les symboles arrivent tantôt d'un réglage
    distant, tantôt d'un instantané de portefeuille, et deux chemins qui
    ne normalisent pas pareil finissent par diverger un jour — ce jour-là
    le total redeviendrait faux, pour une espace.
    */
    @Test
    fun `la casse et les espaces ne changent rien`() {
        for (m in listOf("doge", " DOGE", "Doge ", "bTc")) {
            assertTrue("« $m » devrait etre reconnue", MonnaiesNatives.estNative(m))
        }
    }

    @Test
    fun `une monnaie absente ou nulle n est pas native`() {
        assertFalse(MonnaiesNatives.estNative(null))
        assertFalse(MonnaiesNatives.estNative(""))
        assertFalse(MonnaiesNatives.estNative("   "))
    }
}
