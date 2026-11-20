package com.vaultex.core

import com.vaultex.core.crypto.WalletManager
import com.vaultex.core.market.CoinIds
import com.vaultex.ui.viewmodel.PortfolioViewModel
import com.vaultex.ui.viewmodel.adressePourSymbole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ═══════════════════════════════════════════════════════════════════════
 * LES LISTES ÉCRITES À LA MAIN DOIVENT S'ACCORDER
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Brancher le Dogecoin a demandé d'ajouter une ligne dans HUIT endroits
 * différents. Cinq ont été oubliés, et chacun s'est manifesté séparément,
 * sur l'appareil, des heures après :
 *
 *   1. `COIN_IDS`              → « ≈ $0 » sur 53,98 DOGE bien lus
 *   2. le même, conséquence    → absent de « Mes actifs » et du camembert
 *   3. `syncBlockchainHistory` → aucun dépôt dans l'historique
 *   4. `DepositCheckWorker`    → ni ligne « Récent » ni cloche
 *   5. `adressePourSymbole`    → « Adresse DOGE » au-dessus d'un 0x…
 *   6. `CoinIds.BY_SYMBOL`     → ni cours, ni courbe, ni capitalisation
 *
 * AUCUN N'A LEVÉ D'ERREUR. Pas un seul. Une liste à laquelle il manque
 * une entrée ne se plaint jamais : elle rend `null`, ou elle tombe dans
 * un `else`, et l'écran s'affiche en disant quelque chose de faux.
 *
 * ─── CE QUE CE FICHIER FAIT, ET CE QU'IL NE FAIT PAS ───────────────────
 *
 * Il ne supprime pas les listes — elles ont chacune leur raison d'être et
 * leur propre forme. Il les force à s'accorder sur UN point d'ancrage :
 * [CHAINES_NATIVES] ci-dessous.
 *
 * Ajouter une chaîne, c'est désormais l'ajouter ICI d'abord. Le test dit
 * alors, une par une, toutes les autres places où elle manque — avant le
 * build, pas trois jours plus tard sur un téléphone.
 *
 * Une liste de plus, oui. Mais c'est la seule dont le métier est de faire
 * tomber les autres.
 * ═══════════════════════════════════════════════════════════════════════
 */
class ChainesCoherentesTest {

    private companion object {
        /**
         * Les monnaies NATIVES que le portefeuille dérive.
         *
         * Pas les jetons : eux vivent sur la chaîne de leur contrat et
         * n'ont ni adresse propre ni ligne dans les tables de chaînes.
         *
         * Le Pi en fait partie : il a sa chaîne et son adresse. Qu'il soit
         * en pause derrière un drapeau ne change rien aux tables — c'est
         * justement pour ça que le drapeau peut s'ouvrir sans rien casser.
         */
        val CHAINES_NATIVES = listOf("BTC", "ETH", "BNB", "SOL", "TRX", "PI", "DOGE")

        const val ABANDON =
            "abandon abandon abandon abandon abandon abandon abandon abandon " +
            "abandon abandon abandon about"
    }

    private val adresses = WalletManager.deriveAddresses(ABANDON, "")

    /*
    DÉFAUT N° 6. Sans identifiant de cotation, la fiche perd son cours, sa
    courbe, sa capitalisation, son volume et jusqu'à son NOM — elle retombe
    sur le symbole. Rien n'échoue ; l'écran est simplement plus pauvre que
    ses voisins, et il faut comparer deux fiches côte à côte pour le voir.
    */
    @Test
    fun `chaque chaine native a un identifiant de cotation`() {
        for (symbole in CHAINES_NATIVES) {
            val id = CoinIds.BY_SYMBOL[symbole]
            assertTrue(
                "« $symbole » n'est pas dans CoinIds.BY_SYMBOL : sa fiche " +
                    "n'aura ni cours, ni courbe, ni capitalisation, et portera " +
                    "son symbole au lieu de son nom.",
                !id.isNullOrBlank()
            )
        }
    }

    /*
    DÉFAUTS N° 1 ET 2. L'identifiant doit être dans la requête de cours de
    l'accueil, sinon la valeur vaut zéro — et l'accueil filtre ses lignes
    sur `valueUsd > 0`, donc la monnaie disparaît aussi de « Mes actifs »
    et de la répartition. Un oubli, deux écrans faux.
    */
    @Test
    fun `chaque chaine native est cotee dans l appel de l accueil`() {
        for (symbole in CHAINES_NATIVES) {
            val id = CoinIds.BY_SYMBOL[symbole] ?: continue
            assertTrue(
                "« $symbole » (id « $id ») manque à PortfolioViewModel.COIN_IDS : " +
                    "sa valeur vaudra 0 \$, et le filtre « valueUsd > 0 » de " +
                    "l'accueil l'écartera de « Mes actifs » et du camembert.",
                id in PortfolioViewModel.COIN_IDS
            )
        }
    }

    /*
    ═══════════════════════════════════════════════════════════════════
    DÉFAUT N° 5 — CELUI QUI COÛTE DE L'ARGENT
    ═══════════════════════════════════════════════════════════════════

    Les cinq autres font un écran faux. Celui-ci fait perdre des fonds :
    « Adresse DOGE » affichée au-dessus de l'adresse Ethereum. Valide,
    copiable, et sans retour possible.

    Le contrôle est formulé comme le défaut et non comme le symptôme :
    chaque chaîne rend SA propre adresse, et deux chaînes différentes
    n'en partagent pas — sauf ETH et BNB, qui partagent le coin_type 60
    par construction.
    ═══════════════════════════════════════════════════════════════════
    */
    @Test
    fun `chaque chaine native rend sa propre adresse`() {
        val attendu = mapOf(
            "BTC" to adresses.btc,
            "ETH" to adresses.eth,
            "BNB" to adresses.bnb,
            "SOL" to adresses.sol,
            "TRX" to adresses.trx,
            "PI" to adresses.pi,
            "DOGE" to adresses.doge
        )
        assertEquals(
            "CHAINES_NATIVES et la table de ce test ont divergé",
            CHAINES_NATIVES.toSet(), attendu.keys
        )
        for ((symbole, adresse) in attendu) {
            assertEquals(
                "« $symbole » ne rend pas son adresse",
                adresse, adressePourSymbole(adresses, symbole)
            )
        }
    }

    @Test
    fun `aucune chaine hors EVM ne rend l adresse Ethereum`() {
        for (symbole in CHAINES_NATIVES - setOf("ETH", "BNB")) {
            assertNotEquals(
                "« $symbole » rend l'adresse Ethereum. Affichée sous " +
                    "« Adresse $symbole », elle fait perdre les fonds de " +
                    "quiconque la copie.",
                adresses.eth, adressePourSymbole(adresses, symbole)
            )
        }
    }

    /*
    ETH ET BNB PARTAGENT UNE ADRESSE, par le coin_type 60. Ce n'est pas un
    défaut, c'est le standard — et c'est épinglé ici pour que le test du
    dessus ne soit pas « corrigé » en les séparant. Les leur séparer
    rendrait inatteignables les soldes BEP-20 déjà reçus.
    */
    @Test
    fun `ETH et BNB partagent leur adresse, par conception`() {
        assertEquals(
            adressePourSymbole(adresses, "ETH"),
            adressePourSymbole(adresses, "BNB")
        )
    }
}
