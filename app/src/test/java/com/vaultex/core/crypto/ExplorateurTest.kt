package com.vaultex.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ═══════════════════════════════════════════════════════════════════════
 * UN HASH DOGECOIN OUVRAIT L'EXPLORATEUR D'ETHEREUM
 * ═══════════════════════════════════════════════════════════════════════
 *
 * TROIS COPIES de la table des explorateurs vivaient dans le dépôt :
 * `TransactionDetailScreen`, `SendScreen`, et l'enum [Blockchain] — dont
 * le commentaire disait lui-même que `explorerUrl` « n'est consulté par
 * aucun code ». Une source de vérité dormante, pendant que deux écrans
 * maintenaient des copies à la main.
 *
 * ELLES AVAIENT DÉJÀ DIVERGÉ, et pas seulement sur le Dogecoin :
 *
 *   `TransactionDetailScreen`  else -> null         le bouton disparaît
 *   `SendScreen`               else -> etherscan    le bouton MENT
 *
 * Le second est le plus coûteux. Un bouton absent dit « je ne sais pas ».
 * Un bouton qui ouvre Etherscan avec un hash Dogecoin affirme une chaîne
 * qui n'est pas la bonne, et la page répond « introuvable » — ce qui fait
 * croire à l'utilisateur que sa transaction n'existe pas.
 *
 * ─── CE QUE CE FICHIER TIENT ───────────────────────────────────────────
 *
 * Que chaque chaîne native rende l'explorateur de SA chaîne, et jamais
 * celui d'une autre. L'adresse du Dogecoin est vérifiée sur une
 * transaction réelle avant d'être écrite : une adresse d'explorateur ne
 * se devine pas.
 * ═══════════════════════════════════════════════════════════════════════
 */
class ExplorateurTest {

    private companion object {
        /** Une transaction Dogecoin réelle, confirmée au bloc 6 465 609. */
        const val HASH =
            "2a7b0b441338b90e378ec9d004c31075844127301ea59c87bf9adf45c3c92385"
    }

    @Test
    fun `chaque chaine native ouvre son propre explorateur`() {
        val attendu = mapOf(
            "BTC" to "https://blockstream.info/tx/",
            "ETH" to "https://etherscan.io/tx/",
            "BNB" to "https://bscscan.com/tx/",
            "SOL" to "https://solscan.io/tx/",
            "TRX" to "https://tronscan.org/#/transaction/",
            "DOGE" to "https://dogechain.com/tx/"
        )
        for ((symbole, base) in attendu) {
            assertEquals(
                "« $symbole » n'ouvre pas son explorateur",
                base + HASH, Blockchain.explorateurPour(symbole, HASH)
            )
        }
    }

    /*
    LA FORMULATION DU DÉFAUT, et non celle du symptôme : aucune chaîne qui
    n'est pas Ethereum ne doit mener à Etherscan. C'est ce contrôle qui
    aurait fait tomber le build avant que le bouton ne mente.
    */
    @Test
    fun `aucune chaine hors EVM ne mene a Etherscan`() {
        for (symbole in listOf("BTC", "SOL", "TRX", "DOGE")) {
            val url = Blockchain.explorateurPour(symbole, HASH)
            assertTrue(
                "« $symbole » ouvre Etherscan — la page dira « introuvable », " +
                    "et l'utilisateur croira sa transaction perdue.",
                url != null && !url.contains("etherscan.io")
            )
        }
    }

    /*
    LES VARIANTES D'USDT SUIVENT LEUR CHAÎNE, pas leur symbole. Un USDT
    d'Ethereum se lit chez Etherscan, celui de Tron chez Tronscan : c'est
    la chaîne qui porte la transaction, jamais le jeton.
    */
    @Test
    fun `chaque variante d USDT ouvre l explorateur de sa chaine`() {
        assertEquals("https://tronscan.org/#/transaction/$HASH", Blockchain.explorateurPour("USDT", HASH))
        assertEquals("https://etherscan.io/tx/$HASH", Blockchain.explorateurPour("USDT-ETH", HASH))
        assertEquals("https://bscscan.com/tx/$HASH", Blockchain.explorateurPour("USDT-BNB", HASH))
        assertEquals("https://solscan.io/tx/$HASH", Blockchain.explorateurPour("USDT-SOL", HASH))
    }

    /*
    UN JETON IMPORTÉ PAR CONTRAT vit sur une chaîne EVM, et c'est elle qui
    porte sa transaction. Le préfixe nomme la chaîne d'accueil.
    */
    @Test
    fun `un jeton importe ouvre l explorateur de sa chaine d accueil`() {
        assertEquals(
            "https://etherscan.io/tx/$HASH",
            Blockchain.explorateurPour("ERC20:ETH:0xdAC17F958D2ee523a2206206994597C13D831ec7:6", HASH)
        )
        assertEquals(
            "https://bscscan.com/tx/$HASH",
            Blockchain.explorateurPour("ERC20:BNB:0x55d398326f99059fF775485246999027B3197955:18", HASH)
        )
    }

    /*
    ET NULL QUAND ON NE SAIT PAS. L'appelant masque alors le bouton. Le
    repli silencieux vers une chaîne au hasard est précisément ce qui a
    produit le défaut — il ne doit pas revenir par la porte de derrière.
    */
    @Test
    fun `une chaine inconnue ne rend aucune adresse`() {
        for (inconnue in listOf("", "LTC", "MATIC", "XRP")) {
            assertNull(
                "« $inconnue » devrait rendre null, pas un explorateur au hasard",
                Blockchain.explorateurPour(inconnue, HASH)
            )
        }
    }
}
