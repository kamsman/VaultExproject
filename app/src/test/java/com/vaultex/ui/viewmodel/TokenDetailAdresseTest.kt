package com.vaultex.ui.viewmodel

import com.vaultex.core.crypto.WalletManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ═══════════════════════════════════════════════════════════════════════
 * « ADRESSE DOGE » AU-DESSUS D'UN 0x… — LE DÉFAUT QUE CE FICHIER FERME
 * ═══════════════════════════════════════════════════════════════════════
 *
 * CONSTATÉ SUR APPAREIL, fiche Dogecoin :
 *
 *     Adresse DOGE
 *     0xbde137a9684f0bc437294b2cf2ab7295831e327e
 *
 * C'est l'adresse Ethereum du portefeuille. Parfaitement valide, et pour
 * une autre chaîne. Quiconque la copie et y envoie des DOGE les perd : il
 * n'existe aucun moyen de récupérer des Dogecoin envoyés à une adresse
 * Ethereum.
 *
 * ─── CE N'EST PAS LA PREMIÈRE FOIS ─────────────────────────────────────
 *
 * Le même `else -> a.eth` avait déjà fait afficher une adresse Ethereum
 * sur la fiche Pi. Le commentaire écrit à cette occasion, resté dans le
 * fichier, dit exactement ce qui vient de se reproduire :
 *
 *   « Un `else` qui devine est sûr tant que toutes les chaînes se
 *     ressemblent. Le jour où l'une d'elles ne ressemble pas aux autres,
 *     il devient un piège. »
 *
 * Le piège était décrit, en toutes lettres, à trois lignes du code qui le
 * contient. Et il s'est refermé quand même.
 *
 * C'EST TOUT L'ÉCART ENTRE UN COMMENTAIRE ET UN TEST. Le commentaire
 * prévient celui qui le lit ; il n'empêche personne d'oublier une ligne
 * en ajoutant une chaîne. Ce fichier, lui, refuse de passer.
 *
 * ─── CE QU'IL VÉRIFIE ──────────────────────────────────────────────────
 *
 * Que chaque monnaie native rend SA propre adresse, et surtout qu'aucune
 * ne rend celle d'Ethereum par accident. Le `else` reste légitime pour un
 * jeton importé par contrat — il vit sur une chaîne EVM. Ce qui ne doit
 * plus arriver, c'est qu'une monnaie native y tombe.
 * ═══════════════════════════════════════════════════════════════════════
 */
class TokenDetailAdresseTest {

    private companion object {
        const val ABANDON =
            "abandon abandon abandon abandon abandon abandon abandon abandon " +
            "abandon abandon abandon about"
    }

    private val a = WalletManager.deriveAddresses(ABANDON, "")

    /*
    LE CONTRÔLE QUI AURAIT ATTRAPÉ LE DÉFAUT. Nommément, monnaie par
    monnaie, avec la vraie adresse attendue. Un test qui vérifierait
    seulement « non vide » aurait passé avec le 0x… à la place du DOGE.
    */
    @Test
    fun `chaque monnaie native rend sa propre adresse`() {
        val attendu = mapOf(
            "BTC" to a.btc,
            "ETH" to a.eth,
            "BNB" to a.bnb,
            "SOL" to a.sol,
            "TRX" to a.trx,
            "PI" to a.pi,
            "DOGE" to a.doge
        )
        for ((symbole, adresse) in attendu) {
            assertEquals(
                "« $symbole » ne rend pas son adresse",
                adresse, adressePourSymbole(a, symbole)
            )
        }
    }

    /*
    ═══════════════════════════════════════════════════════════════════
    AUCUNE MONNAIE NON-EVM NE DOIT RENDRE L'ADRESSE ETHEREUM
    ═══════════════════════════════════════════════════════════════════

    C'est la formulation exacte du défaut, et elle est plus forte que la
    précédente : elle tomberait même si quelqu'un ajoutait une chaîne au
    portefeuille sans l'ajouter à la table.

    Le message nomme le risque plutôt que le symptôme — celui qui le lit
    dans six mois doit comprendre en une ligne ce qu'il perd.
    */
    @Test
    fun `aucune monnaie hors EVM ne rend l adresse Ethereum`() {
        for (symbole in listOf("BTC", "SOL", "TRX", "PI", "DOGE")) {
            assertNotEquals(
                "« $symbole » rend l'adresse Ethereum. Affichée sous le " +
                    "libellé « Adresse $symbole », elle fait perdre les fonds " +
                    "de quiconque la copie.",
                a.eth, adressePourSymbole(a, symbole)
            )
        }
    }

    /*
    LES USDT RENDENT L'ADRESSE DE LEUR CHAINE, pas celle du symbole de
    base. Quatre variantes, trois chaines : c'est exactement le genre de
    table ou une ligne se recopie de travers.
    */
    @Test
    fun `chaque variante d USDT rend l adresse de sa chaine`() {
        assertEquals(a.trx, adressePourSymbole(a, "USDT"))
        assertEquals(a.eth, adressePourSymbole(a, "USDT-ETH"))
        assertEquals(a.bnb, adressePourSymbole(a, "USDT-BNB"))
        assertEquals(a.sol, adressePourSymbole(a, "USDT-SOL"))
    }

    /*
    LE REPLI RESTE, ET IL RESTE JUSTE. Un jeton importé par adresse de
    contrat vit sur une chaîne EVM : lui donner l'adresse Ethereum est la
    bonne réponse, pas un accident. Ce test l'épingle pour qu'on ne le
    supprime pas en croyant corriger le défaut du dessus.
    */
    @Test
    fun `un jeton importe retombe sur l adresse EVM`() {
        assertEquals(a.eth, adressePourSymbole(a, "SHIB"))
        assertEquals(a.eth, adressePourSymbole(a, "UN-JETON-INCONNU"))
    }

    /*
    L'ADRESSE DOGECOIN COMMENCE PAR « D ». Un contrôle grossier, et
    c'est voulu : il attrape la confusion de chaîne sans dépendre de la
    valeur exacte, donc il survivra à un changement de phrase d'essai.
    */
    @Test
    fun `l adresse DOGE a bien la forme d une adresse Dogecoin`() {
        val adresse = adressePourSymbole(a, "DOGE")
        assertTrue("« $adresse » ne commence pas par D", adresse.startsWith("D"))
        assertEquals("DBus3bamQjgJULBJtYXpEzDWQRwF5iwxgC", adresse)
    }
}
