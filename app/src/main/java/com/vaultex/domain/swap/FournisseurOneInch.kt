package com.vaultex.domain.swap

import javax.inject.Inject

/*
═══════════════════════════════════════════════════════════════════════════
1INCH — LE DEVIS SEUL
═══════════════════════════════════════════════════════════════════════════

1inch interroge plusieurs réserves de liquidité et rend le meilleur chemin.
À l'étape 1, on ne lui demande qu'un PRIX : le point de terminaison `quote`
ne construit aucune transaction et ne connaît aucune adresse d'utilisateur.

─── SANS CLÉ, ON SE TAIT ────────────────────────────────────────────────

L'API exige une clé. Sans elle on rend null, et le courtier garde la main :
c'est exactement le comportement d'aujourd'hui. Une compilation sur une
machine qui ignore ce réglage reste donc pleinement fonctionnelle — même
règle que pour le relais de cours.

─── DEUX NOMS POUR LE MÊME CHAMP ────────────────────────────────────────

La version 6 rend `dstAmount`, la version 5 rendait `toTokenAmount`. On lit
les deux. Ce n'est pas de la superstition : la documentation n'a pas pu être
consultée depuis l'environnement de développement (domaine bloqué), et un
champ renommé donnerait un devis TOUJOURS absent, sans la moindre erreur —
le genre de panne qu'on met une semaine à voir parce qu'elle ressemble à
« la paire n'est pas supportée ».

─── CE QUI N'EST PAS ENCORE LÀ ──────────────────────────────────────────

La commission. 1inch la prélève DANS l'échange via un paramètre dédié, sans
transaction supplémentaire — c'est ce qui rend le modèle viable sur BNB
Chain, là où une commission maison coûterait plus de frais qu'elle ne
rapporte. Elle n'a rien à faire dans un devis : elle appartient à l'étape 3,
celle qui signe.
═══════════════════════════════════════════════════════════════════════════
*/
class FournisseurOneInch @Inject constructor(
    private val api: com.vaultex.data.remote.api.OneInchApi
) : FournisseurSurPlace {

    override suspend fun devis(de: String, vers: String, montant: Double): DevisSurPlace? {
        if (com.vaultex.core.config.ApiKeys.ONEINCH.isBlank()) return null
        if (montant <= 0.0) return null
        if (de.equals(vers, ignoreCase = true)) return null

        val source = ActifsBnbChain.de(de) ?: return null
        val cible = ActifsBnbChain.de(vers) ?: return null

        /*
        Le montant part en unité ENTIÈRE de la chaîne, jamais en décimal :
        « 0.5 » n'a aucun sens pour un contrat. BigDecimal et non Double —
        un Double perd des sous-unités au-delà de quinze chiffres
        significatifs, et dix-huit décimales en font dix-neuf.
        */
        val brut = try {
            java.math.BigDecimal.valueOf(montant)
                .multiply(java.math.BigDecimal.TEN.pow(source.decimales))
                .toBigInteger()
                .toString()
        } catch (_: Exception) { return null }

        val rep = try {
            api.quote(
                chainId = ActifsBnbChain.CHAIN_ID,
                src = source.adresse,
                dst = cible.adresse,
                amount = brut,
                authorization = "Bearer " + com.vaultex.core.config.ApiKeys.ONEINCH
            )
        } catch (e: Exception) {
            /*
            Un devis absent n'est pas une panne pour l'utilisateur : le
            courtier reste affiché, et l'écran ne perd rien. Mais un quota
            épuisé ou une clé révoquée rendraient ce chemin muet pour
            TOUT LE MONDE, en silence et pour toujours. On le signale — le
            filtre écarte déjà l'appareil simplement hors ligne.
            */
            com.vaultex.core.monitoring.reportUnlessCancelled("devis sur place", e)
            return null
        }

        val sortie = rep.dstAmount ?: rep.toTokenAmount ?: return null
        val montantRecu = try {
            java.math.BigDecimal(sortie)
                .divide(java.math.BigDecimal.TEN.pow(cible.decimales))
                .toDouble()
        } catch (_: Exception) { return null }

        return if (montantRecu > 0.0) DevisSurPlace(montantRecu, "1inch") else null
    }
}
