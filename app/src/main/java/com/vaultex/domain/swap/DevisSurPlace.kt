package com.vaultex.domain.swap

/*
═══════════════════════════════════════════════════════════════════════════
ÉCHANGE SUR PLACE — ÉTAPE 1 : LE DEVIS, ET RIEN D'AUTRE
═══════════════════════════════════════════════════════════════════════════

Quand les deux monnaies vivent sur la MÊME chaîne, l'échange n'a pas besoin
de courtier : les fonds vont de l'adresse de l'utilisateur à une réserve de
liquidité et en reviennent, dans une seule transaction qu'il signe lui-même.

Ce que ça change, et ce n'est pas qu'une question de coût : aujourd'hui la
plupart des paires imposent un minimum autour de 6 000 FCFA. Quelqu'un qui
détient 5 000 FCFA ne peut pas échanger DU TOUT. Une réserve de liquidité
n'a pas de minimum.

─── CE FICHIER NE SIGNE RIEN ────────────────────────────────────────────

Il demande un prix, il le rend. Aucune transaction n'est construite, aucune
clé n'est touchée, aucune autorisation n'est donnée. Il ne peut coûter un
franc à personne, et c'est délibéré : le plan de livraison met le devis en
premier précisément pour qu'on puisse MESURER l'écart réel avant d'écrire la
moindre ligne qui engage de l'argent.

Si l'écart ne vaut pas la suite, on s'arrête ici.

─── POURQUOI PAS UNE IMPLÉMENTATION DE FournisseurSwap ──────────────────

Ce contrat-là suppose une adresse de dépôt et un statut à interroger en
boucle. Un échange sur place n'a ni l'un ni l'autre : il n'y a rien à
déposer nulle part, et la transaction est confirmée en trois secondes. Lui
faire rendre une fausse adresse de dépôt serait un mensonge qui remonterait
jusqu'à l'écran. Deux mécaniques, deux contrats.

─── PÉRIMÈTRE ──────────────────────────────────────────────────────────

BNB Chain uniquement, et seulement les paires dont les deux côtés y vivent.
Tout le reste rend null, et le courtier garde la main — c'est le
comportement d'aujourd'hui, inchangé.
═══════════════════════════════════════════════════════════════════════════
*/

/** Ce qu'une réserve de liquidité rendrait, pour comparer au courtier. */
data class DevisSurPlace(
    /** Montant estimé en monnaie d'arrivée, unité humaine. */
    val montantEstime: Double,
    /** Nom de la source, affiché tel quel — « 1inch ». */
    val source: String
)

interface FournisseurSurPlace {
    /**
     * Prix de [montant] [de] en [vers], ou null si la paire ne se traite pas
     * sur place — chaînes différentes, monnaie inconnue, service muet.
     *
     * Null n'est pas une erreur : c'est « ce chemin ne s'applique pas ».
     * L'appelant garde alors le devis du courtier, comme avant.
     */
    suspend fun devis(de: String, vers: String, montant: Double): DevisSurPlace?
}

/**
 * Monnaies échangeables sur place, et ce qu'il faut savoir d'elles.
 *
 * L'adresse de contrat et le NOMBRE DE DÉCIMALES sont indissociables : une
 * erreur sur les secondes décale le montant d'un facteur mille milliards,
 * sans qu'aucune erreur ne soit levée. Les deux vivent donc sur la même
 * ligne, impossible d'en changer une en oubliant l'autre.
 *
 * L'USDT de BNB Chain a DIX-HUIT décimales, là où celui d'Ethereum et de
 * Tron en ont six. C'est un piège classique, et le reste du dépôt le connaît
 * déjà — voir SendCryptoUseCase, qui multiplie par 10^18 pour « USDT-BNB »
 * et par 10^6 pour « USDT-ETH ».
 */
internal data class ActifSurPlace(
    val adresse: String,
    val decimales: Int
)

internal object ActifsBnbChain {

    /** Adresse conventionnelle de la monnaie NATIVE chez 1inch. */
    private const val NATIF = "0xEeeeeEeeeEeEeeEeEeEeeEEEeeeeEeeeeeeeEEeE"

    /** Identifiant de BNB Chain. */
    const val CHAIN_ID = 56L

    private val table = mapOf(
        "BNB" to ActifSurPlace(NATIF, 18),
        // 18 décimales, et non 6 : particularité du BSC-USD de Binance.
        "USDT-BNB" to ActifSurPlace(
            com.vaultex.domain.usecase.SendCryptoUseCase.USDT_BEP20_CONTRACT, 18
        )
    )

    fun de(cle: String): ActifSurPlace? = table[cle.uppercase()]
}
