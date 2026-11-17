package com.vaultex.ui.viewmodel

import com.vaultex.core.crypto.WalletManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ═══════════════════════════════════════════════════════════════════════
 * LE REGISTRE D'ÉCHANGE EST UNE LISTE ÉCRITE À LA MAIN
 * ═══════════════════════════════════════════════════════════════════════
 *
 * `SWAP_ASSETS` est la SEULE liste que l'écran de change consulte, et elle
 * se tient à jour à la main. Son mode de panne est documenté deux fois
 * dans le fichier, parce qu'il s'est produit deux fois :
 *
 *   — l'USDT de Solana, branché partout ailleurs, absent du registre ;
 *   — le Dogecoin, idem, quatre semaines plus tard.
 *
 * Une liste écrite à la main ne se plaint pas de ce qui lui manque. Ce
 * fichier ne peut pas deviner ce qui manque non plus — aucun test ne
 * peut — mais il peut vérifier que ce qui EST là tient debout.
 *
 * ─── CE QU'IL ATTRAPE VRAIMENT ─────────────────────────────────────────
 *
 * Un actif dont la `chain` n'a pas d'adresse dans notre portefeuille.
 * Avant, `addrFor` rendait l'adresse ETHEREUM pour toute chaîne non
 * prévue — un « 0x… » donné au courtier comme adresse de versement
 * Dogecoin, et comme adresse de remboursement. Bien formé, faux, muet.
 *
 * C'est le genre de défaut qu'aucun écran ne montre : il ne se voit que
 * dans la réponse du courtier, ou pas du tout.
 * ═══════════════════════════════════════════════════════════════════════
 */
class SwapRegistreTest {

    private companion object {
        /** La phrase des vecteurs BIP-39, la même que les autres tests. */
        const val ABANDON =
            "abandon abandon abandon abandon abandon abandon abandon abandon " +
            "abandon abandon abandon about"
    }

    private val adresses = WalletManager.deriveAddresses(ABANDON, "")

    /*
    L'INVARIANT CENTRAL. Il n'énumère rien : il parcourt le registre tel
    qu'il est, donc un dix-neuvième actif ajouté demain est couvert sans
    que personne pense à revenir ici.
    */
    @Test
    fun `chaque actif du registre a une adresse de reception`() {
        for (actif in SwapViewModel.SWAP_ASSETS) {
            val adresse = SwapViewModel.adressePour(adresses, actif.chain)
            assertNotNull(
                "« ${actif.key} » déclare la chaîne « ${actif.chain} », " +
                    "qui n'a pas d'adresse. Les fonds échangés seraient versés " +
                    "à une adresse d'une autre chaîne.",
                adresse
            )
            assertTrue(
                "« ${actif.key} » : adresse vide pour « ${actif.chain} »",
                adresse!!.isNotBlank()
            )
        }
    }

    /*
    LE REPLI DOIT REFUSER, ET NON INVENTER. C'est précisément ce qui a
    changé : `else -> adresses.eth` est devenu `else -> null`. Si quelqu'un
    le « réparait » en remettant un repli, ce test tombe.
    */
    @Test
    fun `une chaine inconnue ne rend aucune adresse`() {
        for (inconnue in listOf("", "DOGECOIN", "LTC", "eth", "MATIC", "0x")) {
            assertNull(
                "« $inconnue » ne devrait rendre aucune adresse",
                SwapViewModel.adressePour(adresses, inconnue)
            )
        }
    }

    /*
    ═══════════════════════════════════════════════════════════════════
    LE DOGECOIN, MÊME QUAND L'INTERRUPTEUR EST OUVERT
    ═══════════════════════════════════════════════════════════════════

    Son entrée au registre est conditionnée à `DOGE_ACTIF`. Donc tant que
    le drapeau vaut false, le test du dessus ne le voit PAS — et c'est
    justement pendant cette période que le câblage se casse sans bruit.

    Celui-ci interroge le résolveur directement, sans passer par le
    registre : il tient dans les deux états du drapeau.
    ═══════════════════════════════════════════════════════════════════
    */
    @Test
    fun `la chaine DOGE rend bien l adresse Dogecoin`() {
        val adresse = SwapViewModel.adressePour(adresses, "DOGE")
        assertEquals(adresses.doge, adresse)
        assertEquals("DBus3bamQjgJULBJtYXpEzDWQRwF5iwxgC", adresse)
        assertTrue("une adresse Dogecoin commence par D", adresse!!.startsWith("D"))
    }

    /*
    ═══════════════════════════════════════════════════════════════════
    L'ERREUR QU'ON A FAILLI FAIRE : L'ADRESSE ETHEREUM POUR LE DOGECOIN
    ═══════════════════════════════════════════════════════════════════

    C'est ce que faisait l'ancien `else -> addresses.eth`. Les deux
    adresses sont non vides et bien formées ; seule leur DIFFÉRENCE prouve
    que le branchement est le bon. Un test qui vérifierait « non vide »
    passerait aussi avec le défaut.

    ─── CE TEST A D'ABORD ÉTÉ ÉCRIT FAUX ──────────────────────────────

    Il exigeait SIX adresses distinctes pour six chaînes. Il y en a cinq,
    et c'est correct : `deriveAddresses` appelle
    `deriveEvmAddress(seed, coinType = 60)` pour ETH **et** pour BNB.
    BNB Chain partage le coin_type d'Ethereum, donc la même phrase donne
    la même adresse sur les deux — ce n'est pas un défaut, c'est le
    standard, et c'est aussi pour cela que l'USDT-BNB et l'USDT-ETH se
    reçoivent au même endroit.

    J'avais écrit « six » sans le vérifier. Le fichier qui dérive les
    adresses le dit en commentaire, à la ligne même.

    D'où la forme ci-dessous : l'égalité ETH = BNB est ÉPINGLÉE au lieu
    d'être contournée. Si quelqu'un donnait un jour à BNB un chemin
    distinct, ce test tomberait — et il devrait, parce que les soldes
    BEP-20 déjà reçus seraient devenus inatteignables.
    ═══════════════════════════════════════════════════════════════════
    */
    @Test
    fun `chaque chaine rend sa propre adresse et pas celle d une autre`() {
        val parChaine = listOf("BTC", "ETH", "BNB", "SOL", "TRX", "DOGE")
            .associateWith { SwapViewModel.adressePour(adresses, it) }
        for ((chaine, adresse) in parChaine) {
            assertNotNull("« $chaine » sans adresse", adresse)
        }

        // ETH et BNB : même coin_type EVM, donc une seule adresse pour deux.
        assertEquals(
            "BNB Chain partage le coin_type 60 d'Ethereum",
            parChaine["ETH"], parChaine["BNB"]
        )

        // Tout le reste doit être deux à deux différent — cinq valeurs.
        assertEquals(
            "des chaînes distinctes partagent une adresse : $parChaine",
            5, parChaine.values.toSet().size
        )

        /*
        ET LE DOGECOIN EN PARTICULIER, puisque c'est lui que l'ancien repli
        aurait confondu. Nommément, pour que l'échec dise quoi chercher.
        */
        for (autre in listOf("BTC", "ETH", "BNB", "SOL", "TRX")) {
            assertFalse(
                "l'adresse DOGE est identique à celle de « $autre » — " +
                    "c'est exactement ce que faisait « else -> addresses.eth »",
                parChaine["DOGE"] == parChaine[autre]
            )
        }
    }

    /*
    ═══════════════════════════════════════════════════════════════════
    LE TICKER, PARCE QUE LE MAUVAIS A L'AIR PLUS JUSTE QUE LE BON
    ═══════════════════════════════════════════════════════════════════

    Le catalogue de SimpleSwap porte les deux :

        doge       Dogecoin        doge  (aucun contrat)
        dogecoin   Buff Doge Coin  bsc   0x23125108...

    D'habitude un mauvais ticker a l'air faux. Ici c'est le plus explicite
    qui est faux, et c'est vers lui que va la main de quelqu'un qui trouve
    « doge » trop court.

    Se tromper ne perd rien — un ticker inconnu fait échouer le devis, et
    sans devis il n'y a pas d'adresse de dépôt. Ce qu'on perdrait, c'est la
    fonctionnalité, silencieusement.
    */
    @Test
    fun `le champ cn du Dogecoin ne porte pas le memecoin BSC`() {
        val doge = SwapViewModel.SWAP_ASSETS.firstOrNull { it.key == "DOGE" }
            // Drapeau fermé : rien à vérifier, et c'est un état légitime.
            ?: return
        assertEquals("doge", doge.cn)
        assertFalse(
            "« dogecoin » est Buff Doge Coin, un jeton de BNB Chain",
            doge.cn == "dogecoin"
        )
        assertEquals("DOGE", doge.chain)
        assertEquals("DOGE", doge.sendChain)
        assertNull("une monnaie native n'a pas de contrat", doge.contract)
    }

    /*
    DEUX ACTIFS NE PEUVENT PAS PARTAGER UNE CLÉ : `assetOf` rend le
    PREMIER trouvé, donc un doublon rendrait le second inatteignable sans
    qu'aucun message ne le dise. Le registre est passé de 12 à 19 lignes
    en recopiant des lignes voisines ; c'est comme ça qu'on crée un
    doublon.
    */
    @Test
    fun `les cles du registre sont uniques`() {
        val cles = SwapViewModel.SWAP_ASSETS.map { it.key }
        val doublons = cles.groupingBy { it }.eachCount().filterValues { it > 1 }
        assertTrue("clés en double : $doublons", doublons.isEmpty())
    }
}
