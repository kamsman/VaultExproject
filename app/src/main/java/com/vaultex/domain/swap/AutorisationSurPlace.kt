package com.vaultex.domain.swap

import com.vaultex.data.remote.api.EvmRpcApi
import com.vaultex.data.remote.dto.JsonRpcRequest
import javax.inject.Inject
import javax.inject.Named

/*
═══════════════════════════════════════════════════════════════════════════
ÉCHANGE SUR PLACE — ÉTAPE 2a : LIRE L'AUTORISATION, SANS RIEN SIGNER
═══════════════════════════════════════════════════════════════════════════

Un jeton ne se dépense pas tout seul. Avant qu'un routeur puisse en prélever,
son propriétaire doit l'y AUTORISER — une transaction à part, distincte de
l'échange.

Ce fichier ne fait que LIRE cette autorisation. Aucune transaction n'est
construite, aucune clé touchée. Comme l'étape 1, il ne peut coûter un franc
à personne.

Pourquoi le séparer de la signature : si l'adresse du routeur est fausse ou
le décodage erroné, on le découvre ici, sur une lecture, et non en ayant
autorisé un contrat inconnu à prélever des fonds.

─── LA MONNAIE NATIVE N'A RIEN À AUTORISER ──────────────────────────────

Le BNB n'est pas un jeton : il se dépense directement, sans intermédiaire.
Demander une autorisation pour lui n'aurait aucun sens — et l'adresse
conventionnelle qui le désigne chez 1inch n'est pas un contrat. On rend donc
« rien à autoriser » sans interroger personne.
═══════════════════════════════════════════════════════════════════════════
*/

/** Ce qu'un routeur a le droit de prélever aujourd'hui, et ce qu'il faudrait. */
data class EtatAutorisation(
    /** Quantité déjà autorisée, en unité humaine. */
    val autorise: Double,
    /** Vrai si le montant voulu dépasse l'autorisation en place. */
    val insuffisante: Boolean,
    /** Contrat à autoriser — null quand il n'y a rien à autoriser. */
    val routeur: String?
)

class AutorisationSurPlace @Inject constructor(
    private val api: com.vaultex.data.remote.api.OneInchApi,
    @Named("bnb") private val rpc: EvmRpcApi
) {

    /**
     * État de l'autorisation pour échanger [montant] de [token] depuis
     * [proprietaire]. Null quand la question ne se pose pas : monnaie
     * native, jeton inconnu, clé absente, ou service muet.
     */
    suspend fun etat(token: String, proprietaire: String, montant: Double): EtatAutorisation? {
        if (com.vaultex.core.config.ApiKeys.ONEINCH.isBlank()) return null
        val actif = ActifsBnbChain.de(token) ?: return null
        // Monnaie native : rien à autoriser, et c'est une réponse, pas un échec.
        if (!actif.adresse.startsWith("0x") || actif.adresse.length != 42) {
            return EtatAutorisation(autorise = Double.MAX_VALUE, insuffisante = false, routeur = null)
        }

        val routeur = try {
            api.spender(
                chainId = ActifsBnbChain.CHAIN_ID,
                authorization = "Bearer " + com.vaultex.core.config.ApiKeys.ONEINCH
            ).address
        } catch (e: Exception) {
            com.vaultex.core.monitoring.reportUnlessCancelled("routeur sur place", e)
            null
        } ?: return null

        /*
        allowance(address propriétaire, address bénéficiaire).

        Sélecteur 0xdd62ed3e, puis les deux adresses complétées à 32 octets.
        Même forme que le balanceOf déjà utilisé ailleurs dans le dépôt —
        0x70a08231 suivi d'une adresse complétée.
        */
        val a = proprietaire.removePrefix("0x").lowercase().padStart(64, '0')
        val b = routeur.removePrefix("0x").lowercase().padStart(64, '0')
        val data = "0xdd62ed3e$a$b"

        val brut = try {
            val res = rpc.rpcCall(
                JsonRpcRequest(
                    "eth_call",
                    mutableListOf(mapOf("to" to actif.adresse, "data" to data) as Any, "latest" as Any)
                )
            )
            val hex = res.result as? String
            if (res.error != null || hex == null) return null
            java.math.BigInteger(hex.removePrefix("0x").ifEmpty { "0" }, 16)
        } catch (e: Exception) {
            com.vaultex.core.monitoring.reportUnlessCancelled("autorisation sur place", e)
            return null
        }

        val autorise = try {
            brut.toBigDecimal()
                .divide(java.math.BigDecimal.TEN.pow(actif.decimales))
                .toDouble()
        } catch (_: Exception) { return null }

        return EtatAutorisation(
            autorise = autorise,
            insuffisante = autorise < montant,
            routeur = routeur
        )
    }
}
