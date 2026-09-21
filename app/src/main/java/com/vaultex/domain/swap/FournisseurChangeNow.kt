package com.vaultex.domain.swap

import com.vaultex.core.config.ApiKeys
import com.vaultex.data.remote.api.ChangeNowApi
import com.vaultex.data.remote.dto.ChangeNowTransactionBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ChangeNOW — le fournisseur historique, conservé.
 *
 * Il reste en place pour deux raisons. D'abord parce qu'il faut pouvoir
 * revenir en arrière d'une ligne si SimpleSwap déçoit. Ensuite parce que les
 * échanges DÉJÀ EN COURS chez lui doivent continuer d'être suivis : basculer
 * de fournisseur ne doit pas rendre invisible un swap ouvert la veille.
 *
 * Sa commission partenaire est de 0,4 %, non négociable sans accord
 * particulier — c'est ce qui a motivé l'ajout d'un second fournisseur.
 */
@Singleton
class FournisseurChangeNow @Inject constructor(
    private val api: ChangeNowApi
) : FournisseurSwap {

    override val nom = "ChangeNOW"

    override val commissionPourcent: Double = 0.4

    override suspend fun devis(de: String, vers: String, montant: Double): DevisSwap {
        val r = api.getEstimatedAmount(
            amount = montantApi(montant),
            fromTo = paire(de, vers),
            apiKey = ApiKeys.CHANGENOW
        )
        return DevisSwap(montantEstime = r.estimatedAmount, avertissement = r.warning)
    }

    override suspend fun minimum(de: String, vers: String): Double? = try {
        api.getMinAmount(paire(de, vers), ApiKeys.CHANGENOW).minAmount
    } catch (_: Exception) {
        null
    }

    override suspend fun creerEchange(
        de: String,
        vers: String,
        montant: Double,
        adresseReception: String,
        adresseRemboursement: String?
    ): EchangeCree {
        val r = api.createTransaction(
            apiKey = ApiKeys.CHANGENOW,
            body = ChangeNowTransactionBody(
                from = ticker(de),
                to = ticker(vers),
                address = adresseReception,
                amount = montantApi(montant),
                refundAddress = adresseRemboursement
            )
        )
        // Même exigence que pour SimpleSwap : sans adresse de dépôt, on
        // n'envoie rien. Le type est non-nullable ici, la vérification porte
        // donc sur le contenu.
        if (r.payinAddress.isBlank()) {
            throw IllegalStateException("ChangeNOW n'a pas rendu d'adresse de dépôt")
        }
        return EchangeCree(
            id = r.id,
            adresseDepot = r.payinAddress,
            memoDepot = r.payinExtraId?.takeIf { it.isNotBlank() },
            montantAttendu = r.amount
        )
    }

    /*
    UN STATUT QU'ON NE SAIT PAS LIRE NE DOIT PAS DISPARAÎTRE EN SILENCE.

    Ce bloc rendait null sur n'importe quelle exception. Or le suivi ne
    dispose d'aucun autre moyen de conclure : sans réponse, la ligne reste
    « pending », l'accueil continue d'annoncer un échange en cours, et la
    notification de fin ne part jamais — pour un échange pourtant abouti
    chez le fournisseur.

    C'est un silence coûteux, parce qu'il est INDISTINGUABLE d'un échange
    réellement en cours. Une clé révoquée, un identifiant refusé, une
    réponse dont la forme a changé : tout cela ressemble, vu de
    l'application, à « ce n'est pas encore fini ».

    On rend toujours null — l'appelant doit pouvoir réessayer — mais
    l'incident part au diagnostic administrateur. Une annulation de
    coroutine, elle, n'est pas un incident : reportUnlessCancelled l'écarte.
    */
    override suspend fun statut(id: String): StatutSwap? = try {
        val r = api.getTransactionStatus(id, ApiKeys.CHANGENOW)
        StatutSwap(
            id = r.id,
            statut = r.status,
            hashDepot = r.hash,
            // payoutHash : le versement SORTANT vers le portefeuille de
            // l'utilisateur. C'est lui qui déclenche le badge « En attente »
            // sur la monnaie reçue ; l'oublier ferait disparaître le suivi.
            hashSortie = r.payoutHash,
            montantRecu = r.amountTo
        )
    } catch (e: Exception) {
        com.vaultex.core.monitoring.reportUnlessCancelled("statut ChangeNOW", e)
        null
    }

    private fun paire(de: String, vers: String) = "${ticker(de)}_${ticker(vers)}"

    /**
     * Tickers ChangeNOW — une monnaie par RÉSEAU.
     *
     * Identiques à ceux de SwapUseCase.cnTicker, dont cette classe reprend le
     * rôle. Notre « USDT » est du TRC20 ; sans cette table, la paire est
     * invalide et l'appel échoue.
     */
    private fun ticker(token: String): String = when (token.uppercase()) {
        "USDT"     -> "usdttrc20"
        "USDT-ETH" -> "usdterc20"
        "USDT-BNB" -> "usdtbsc"
        "BNB"      -> "bnbbsc"
        else       -> token.lowercase()
    }

    private fun montantApi(v: Double): String =
        java.math.BigDecimal.valueOf(v).stripTrailingZeros().toPlainString()
}
