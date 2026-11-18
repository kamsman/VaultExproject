package com.vaultex.domain.fiat

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ═══════════════════════════════════════════════════════════════════════
 * LE FRAIS D'ENVOI N'EST PLUS UN FORFAIT, ET ZÉRO EST UNE VALEUR
 * ═══════════════════════════════════════════════════════════════════════
 *
 * D'OÙ VIENT CE FICHIER. Une mesure faite sur l'app, le 18 novembre, en
 * demandant deux devis d'achat d'USDT-SOL et en résolvant les deux
 * inconnues :
 *
 *     7 078 FCFA →  10,000657 USDT
 *    70 780 FCFA → 108,473946 USDT
 *
 *    taux  = (70780 − 7078) / (108,473946 − 10,000657) = 646,90 FCFA/$
 *    frais = 7078 − 10,000657 × 646,90                 = 608,61 FCFA
 *
 * Les deux points donnent le MÊME frais au centime — donc le modèle
 * « affine avec décalage » est exact, ce n'est pas un ajustement
 * approximatif. Ce frais valait 1,00 $, servi à l'identique pour toutes
 * les monnaies.
 *
 * ─── POURQUOI UN SEUL NOMBRE NE POUVAIT PAS CONVENIR ───────────────────
 *
 * Un dollar est à peu près le coût d'un envoi sur Ethereum. Sur Solana,
 * l'envoi coûte quelques millièmes de dollar — les replis de
 * `ReserveFrais` le disent : SOL 0,00001 contre ETH 0,0003.
 *
 * Et comme le frais ne suit pas le montant, il frappait le plus fort les
 * plus petits achats : 12 % de l'opération au plancher de 5 000 FCFA,
 * contre 1,2 % au plafond de 50 000. C'est-à-dire ceux qui comparent.
 *
 * ─── CE QUE CE TEST PROTÈGE ────────────────────────────────────────────
 *
 * Une seule règle, et c'est celle qu'une relecture distraite casserait en
 * l'alignant sur ses voisines : `minimumPour` filtre `> 0`, à juste
 * titre — un minimum de zéro ne borne rien. Ici un frais de zéro veut
 * dire « cette chaîne ne coûte rien à servir ». Le filtrer renverrait la
 * monnaie au forfait d'un dollar.
 *
 * Et ça ne se verrait pas : le prix resterait simplement celui d'avant.
 * C'est exactement le genre de réglage qu'on croit avoir posé — le worker
 * en a déjà fait les frais avec `CHANGE_MARGE`, lu des semaines durant
 * sous un nom qui n'existait pas.
 * ═══════════════════════════════════════════════════════════════════════
 */
class FraisParMonnaieTest {

    private fun parametres(
        frais: Double = 1.0,
        parMonnaie: Map<String, Double> = emptyMap()
    ) = ParametresChange(
        actif = true,
        margeFcfaParDollar = 38.3,
        fraisUsdAchat = frais,
        fraisUsdAchatParMonnaie = parMonnaie,
        minimumFcfa = 5_000.0,
        plafondFcfa = 50_000.0,
        numeroMobileMoney = "70000000",
        nomChangeur = "essai",
        operateur = "Orange Money",
        delaiMinutes = 30
    )

    /*
    LE CONTRÔLE CENTRAL. Si quelqu'un ajoutait `takeIf { it > 0.0 }` à
    `fraisUsdAchatPour` — par symétrie avec `minimumPour`, qui est juste
    à deux lignes de là — ce test tomberait, et lui seul.
    */
    @Test
    fun `un frais regle a zero reste zero et ne retombe pas sur le forfait`() {
        val p = parametres(frais = 1.0, parMonnaie = mapOf("USDT-SOL" to 0.0))
        assertEquals(
            "un frais de zéro doit rester zéro — le renvoyer au forfait " +
                "d'un dollar annulerait le réglage sans que rien ne le dise",
            0.0, p.fraisUsdAchatPour("USDT-SOL"), 0.0
        )
    }

    @Test
    fun `une monnaie absente de la table retombe sur le forfait`() {
        val p = parametres(frais = 1.0, parMonnaie = mapOf("USDT-SOL" to 0.0))
        assertEquals(1.0, p.fraisUsdAchatPour("USDT-ETH"), 0.0)
        assertEquals(1.0, p.fraisUsdAchatPour("USDT"), 0.0)
    }

    /*
    UN RELAIS PLUS ANCIEN QUE L'APK ne sert pas ce champ : la table est
    vide, et le tarif doit être celui d'avant, à l'identique. C'est la
    condition pour que ce changement puisse partir sans rien casser pour
    les installations déjà en place.
    */
    @Test
    fun `une table vide laisse le tarif inchange`() {
        val p = parametres(frais = 1.0)
        for (monnaie in listOf("USDT", "USDT-SOL", "USDT-ETH", "USDT-BNB", "BTC")) {
            assertEquals(1.0, p.fraisUsdAchatPour(monnaie), 0.0)
        }
    }

    /*
    LA CLÉ EST LA MONNAIE ENTIÈRE, PAS SA CHAÎNE. Même piège que les
    minimums : « SOL » ne tarife pas « USDT-SOL ». Un réglage posé sous la
    mauvaise clé n'a aucun effet et ne se plaint pas.
    */
    @Test
    fun `la cle est la monnaie entiere et non sa chaine`() {
        val p = parametres(frais = 1.0, parMonnaie = mapOf("SOL" to 0.0))
        assertEquals(
            "« SOL » ne doit pas tarifer « USDT-SOL »",
            1.0, p.fraisUsdAchatPour("USDT-SOL"), 0.0
        )
        assertEquals(0.0, p.fraisUsdAchatPour("SOL"), 0.0)
    }

    @Test
    fun `la casse de la monnaie demandee ne compte pas`() {
        val p = parametres(frais = 1.0, parMonnaie = mapOf("USDT-SOL" to 0.01))
        assertEquals(0.01, p.fraisUsdAchatPour("usdt-sol"), 0.0)
        assertEquals(0.01, p.fraisUsdAchatPour("Usdt-Sol"), 0.0)
    }

    /*
    ═══════════════════════════════════════════════════════════════════
    L'EFFET SUR LE PRIX, AVEC LES NOMBRES MESURÉS
    ═══════════════════════════════════════════════════════════════════

    Les contrôles du dessus portent sur une recherche dans une table.
    Celui-ci refait le calcul complet de l'écran, pour que le gain soit
    un chiffre et non une intention.

    DEUX TAUX, ET NON UN. Je les ai d'abord confondus en vérifiant ces
    chiffres, et c'est une confusion que le code évite exprès :

        le FRAIS se convertit au cours du MARCHÉ — [ChangeState.fcfaParDollar],
        mesuré ici à 608,61 — parce que c'est un coût répercuté, et que
        prendre la marge dessus serait prendre un bénéfice sur un frais ;

        la CRYPTO se convertit au PRIX D'ACHAT — 646,90 — marge comprise.

    Avec ces deux-là, le calcul reproduit les devis observés à cinq
    millionièmes d'USDT près. C'est ce qui prouve que les constantes
    ci-dessous sont les bonnes et non un ajustement commode.

    Au plancher de 5 000 FCFA, sur Solana : le forfait d'un dollar laisse
    6,7884 USDT, un frais à un centime en laisse 7,7198. **13,7 % de
    plus**, pour le client qui change le moins.
    ═══════════════════════════════════════════════════════════════════
    */
    @Test
    fun `sur Solana au plancher le client recoit nettement plus`() {
        val base = 608.61            // cours du marché — convertit le FRAIS
        val achat = 646.8962         // prix d'achat   — convertit la CRYPTO

        fun recu(p: ParametresChange, monnaie: String, saisi: Double): Double =
            (saisi - p.fraisUsdAchatPour(monnaie) * base) / achat

        val avant = parametres(frais = 1.0)
        val apres = parametres(frais = 1.0, parMonnaie = mapOf("USDT-SOL" to 0.01))

        /*
        D'ABORD : LE MODÈLE REPRODUIT-IL LA RÉALITÉ ? Les deux devis lus
        sur l'écran. Sans ce contrôle, les deux suivants compareraient
        deux fictions entre elles.
        */
        assertEquals(10.000657, recu(avant, "USDT-SOL", 7_078.0), 0.0001)
        assertEquals(108.473946, recu(avant, "USDT-SOL", 70_780.0), 0.0001)

        // Puis le gain, au plancher où le frais pèse le plus lourd.
        assertEquals(6.7884, recu(avant, "USDT-SOL", 5_000.0), 0.001)
        assertEquals(7.7198, recu(apres, "USDT-SOL", 5_000.0), 0.001)

        // Et au plafond, où il ne pèse presque plus : 1,2 % seulement.
        assertEquals(76.3513, recu(avant, "USDT-SOL", 50_000.0), 0.001)
        assertEquals(77.2827, recu(apres, "USDT-SOL", 50_000.0), 0.001)

        // L'USDT de Tron, lui, n'est pas tarifé : rien ne doit bouger.
        assertEquals(
            recu(avant, "USDT", 5_000.0),
            recu(apres, "USDT", 5_000.0),
            0.0
        )
    }
}
