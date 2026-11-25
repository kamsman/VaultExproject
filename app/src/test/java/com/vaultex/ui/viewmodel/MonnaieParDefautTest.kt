package com.vaultex.ui.viewmodel

import com.vaultex.domain.fiat.ParametresChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ═══════════════════════════════════════════════════════════════════════
 * L'ÉCRAN D'ACHAT S'OUVRE SUR LE RÉSEAU LE MOINS CHER
 * ═══════════════════════════════════════════════════════════════════════
 *
 * VU SUR L'APPAREIL : l'écran d'achat s'ouvrait sur « USDT · TRC20 ».
 * Pas par choix — `p.monnaies.firstOrNull()` prenait le premier de la
 * liste du relais, c'est-à-dire l'ordre des clés dans `CHANGE_ADRESSES`.
 *
 * MESURÉ au cours du 25 novembre 2026, sur un achat de dix dollars :
 *
 *     TRC20    2,00 $   1 195 FCFA    18 % de la commande
 *     ERC20    0,60 $     358 FCFA     5 %
 *     BEP20    0,01 $       6 FCFA   0,1 %
 *
 * Deux cents fois l'écart, pour le même USDT reçu. Quelqu'un qui tapait
 * son montant sans toucher à la liste déroulante payait 1 195 francs là
 * où 6 suffisaient.
 *
 * ─── CE QUE CE FICHIER TIENT ───────────────────────────────────────────
 *
 * Une propriété, et c'est celle qui coûte de l'argent : **l'écran s'ouvre
 * sur le réseau le moins cher offert**. Pas « sur BEP20 » — le nom n'est
 * écrit nulle part dans le code, et ne doit pas l'être ici non plus,
 * sinon le test figerait ce que le relais est censé pouvoir changer.
 * ═══════════════════════════════════════════════════════════════════════
 */
class MonnaieParDefautTest {

    private fun parametres(
        monnaies: List<String>,
        frais: Map<String, Double> = emptyMap(),
        forfait: Double = 0.0
    ) = ParametresChange(
        actif = true,
        margeFcfaParDollar = 63.0,
        fraisUsdAchat = forfait,
        fraisUsdAchatParMonnaie = frais,
        minimumFcfa = 6_000.0,
        plafondFcfa = 500_000.0,
        numeroMobileMoney = "05759716",
        nomChangeur = "ACCEPTEUR",
        operateur = "Orange Money",
        delaiMinutes = 10,
        monnaies = monnaies
    )

    /*
    LES VRAIS CHIFFRES, ET LE VRAI ORDRE DU RELAIS — celui qui faisait
    tomber l'écran sur TRON. Un test écrit avec BEP20 en tête de liste
    serait passé avant la correction.
    */
    private val reel = parametres(
        monnaies = listOf("USDT", "USDT-ETH", "USDT-BNB"),
        frais = mapOf("USDT" to 2.00, "USDT-ETH" to 0.60, "USDT-BNB" to 0.01)
    )

    @Test
    fun `l ecran s ouvre sur le reseau le moins cher, pas sur le premier`() {
        assertEquals(
            "L'ecran s'ouvrirait sur un reseau plus cher que necessaire : " +
                "c'est 1 195 FCFA de frais au lieu de 6 sur un achat de dix dollars.",
            "USDT-BNB",
            monnaieParDefaut(reel)
        )
    }

    /*
    LE NOM N'EST PAS FIGÉ. Les tarifs viennent du relais et se changent
    sans reconstruire l'application. Si BNB Chain devenait cher, le choix
    doit suivre — sinon la correction d'aujourd'hui devient le defaut de
    demain.
    */
    @Test
    fun `si les tarifs changent, le choix suit`() {
        val renverse = parametres(
            monnaies = listOf("USDT", "USDT-ETH", "USDT-BNB"),
            frais = mapOf("USDT" to 0.01, "USDT-ETH" to 0.60, "USDT-BNB" to 3.00)
        )
        assertEquals("USDT", monnaieParDefaut(renverse))
    }

    /*
    SANS TARIF PAR MONNAIE, LE COMPORTEMENT D'AVANT. Tous les frais valent
    alors le forfait, `minByOrNull` rend le premier minimum, donc le
    premier de la liste. C'est la propriete qui garantit que ce changement
    ne bouge rien chez un relais qui ne tarifie pas par monnaie.
    */
    @Test
    fun `sans tarif par monnaie, c est le premier de la liste`() {
        val sansTarif = parametres(
            monnaies = listOf("USDT", "USDT-ETH", "USDT-BNB"),
            forfait = 1.0
        )
        assertEquals("USDT", monnaieParDefaut(sansTarif))
    }

    /*
    UNE EGALITE GARDE L'ORDRE DU RELAIS. Sans cette propriete, deux
    reseaux au meme prix feraient sauter l'ecran de l'un a l'autre selon
    l'humeur du tri — et le changeur ne saurait jamais sur lequel ses
    clients arrivent.
    */
    @Test
    fun `a prix egal, l ordre du relais tranche`() {
        val egalite = parametres(
            monnaies = listOf("USDT-ETH", "USDT-BNB"),
            frais = mapOf("USDT-ETH" to 0.50, "USDT-BNB" to 0.50)
        )
        assertEquals("USDT-ETH", monnaieParDefaut(egalite))
    }

    /*
    AUCUNE MONNAIE OFFERTE : on rend null, et l'appelant garde ce qu'il
    avait. Rendre une chaine vide ferait afficher un selecteur sans nom.
    */
    @Test
    fun `sans monnaie offerte, rien n est choisi`() {
        assertNull(monnaieParDefaut(parametres(monnaies = emptyList())))
    }
}
