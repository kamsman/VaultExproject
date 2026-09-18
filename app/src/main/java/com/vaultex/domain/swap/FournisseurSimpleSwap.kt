package com.vaultex.domain.swap

import com.vaultex.core.config.ApiKeys
import com.vaultex.data.remote.api.SimpleSwapApi
import com.vaultex.data.remote.dto.SimpleSwapCreateBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SimpleSwap — l'échangeur qui permet d'atteindre 1,5 %.
 *
 * La commission n'est PAS appliquée ici. Elle est portée par la clé d'API,
 * réglée entre 0,4 et 5 % dans l'espace partenaire. Le code se contente de
 * demander un devis et de créer l'échange ; le taux rendu tient déjà compte
 * de la part VaultEx.
 *
 * C'est précisément ce qui rend l'opération viable : aucune transaction
 * supplémentaire, donc aucun frais réseau supplémentaire.
 */
@Singleton
class FournisseurSimpleSwap @Inject constructor(
    private val api: SimpleSwapApi
) : FournisseurSwap {

    override val nom = "SimpleSwap"

    override val commissionPourcent: Double = ApiKeys.SIMPLESWAP_COMMISSION

    override suspend fun devis(de: String, vers: String, montant: Double): DevisSwap {
        val brut = api.getEstimated(
            apiKey = ApiKeys.SIMPLESWAP,
            from = ticker(de),
            to = ticker(vers),
            amount = montantApi(montant)
        )
        /*
        get_estimated rend un NOMBRE NU — « "0.0123" » — et non un objet.
        On lit donc l'élément brut plutôt que de parier sur une forme : un
        littéral texte, un nombre, ou le cas échéant un objet contenant le
        montant. Une réponse qu'on ne sait pas lire devient une exception,
        jamais un zéro silencieux qui ferait afficher « vous recevez 0 ».
        */
        val estime = when {
            brut.isJsonPrimitive -> brut.asString
            brut.isJsonObject -> brut.asJsonObject.let { o ->
                o.get("estimated_amount")?.asString ?: o.get("amount")?.asString
            }
            else -> null
        }
        if (estime.isNullOrBlank() || estime == "null") {
            throw IllegalStateException("Devis indisponible pour ${de}→${vers}")
        }
        return DevisSwap(montantEstime = estime)
    }

    override suspend fun minimum(de: String, vers: String): Double? = try {
        api.getRanges(
            apiKey = ApiKeys.SIMPLESWAP,
            from = ticker(de),
            to = ticker(vers)
        ).min?.toDoubleOrNull()
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
        val r = api.createExchange(
            apiKey = ApiKeys.SIMPLESWAP,
            body = SimpleSwapCreateBody(
                currency_from = ticker(de),
                currency_to = ticker(vers),
                amount = montantApi(montant),
                address_to = adresseReception,
                user_refund_address = adresseRemboursement.orEmpty()
            )
        )
        /*
        DEUX VÉRIFICATIONS QUI NE SONT PAS DÉCORATIVES.

        L'adresse de dépôt est celle vers laquelle l'application va VIRER LES
        FONDS de l'utilisateur. Une réponse incomplète — champ renommé,
        erreur rendue avec un code 200, monnaie retirée entre le devis et la
        création — ne doit surtout pas se traduire par un envoi vers une
        chaîne vide.

        On échoue donc bruyamment. L'utilisateur voit un message ; il ne perd
        rien.
        */
        val id = r.id
        val depot = r.addressFrom
        if (id.isNullOrBlank()) {
            throw IllegalStateException("SimpleSwap n'a pas rendu d'identifiant d'échange")
        }
        if (depot.isNullOrBlank()) {
            throw IllegalStateException("SimpleSwap n'a pas rendu d'adresse de dépôt")
        }
        return EchangeCree(
            id = id,
            adresseDepot = depot,
            memoDepot = r.extraIdFrom?.takeIf { it.isNotBlank() },
            montantAttendu = r.expectedAmount ?: r.amountTo
        )
    }

    override suspend fun statut(id: String): StatutSwap? = try {
        val r = api.getExchange(ApiKeys.SIMPLESWAP, id)
        StatutSwap(
            id = r.id ?: id,
            statut = r.status.orEmpty(),
            hashDepot = r.txFrom,
            hashSortie = r.txTo,
            montantRecu = r.amountTo
        )
    } catch (_: Exception) {
        null
    }

    /*
    ═══════════════════════════════════════════════════════════════════════
    LES TICKERS SIMPLESWAP NE SE DEVINENT PAS — ILS SE VÉRIFIENT
    ═══════════════════════════════════════════════════════════════════════

    Cette table était recopiée de celle de ChangeNOW en supposant que les deux
    maisons écrivent pareil. Elles n'écrivent pas pareil, et deux lignes
    étaient fausses :

        BNB      « bnbbsc »  n'existe pas → « bnb-bsc »
        USDT-BNB « usdtbsc » n'existe pas → « usdtbep20 »

    Constaté sur appareil : un échange ETH → BNB répondait « Not Found » SANS
    afficher de minimum. C'est la signature d'une monnaie inconnue et non d'un
    montant trop petit — quand la paire existe mais que le montant est
    insuffisant, get_ranges répond quand même et le minimum s'affiche. Ici
    get_ranges échouait aussi : le ticker lui-même était refusé.

    Conséquence directe : les USDT détenus sur BNB Chain — le seul solde réel
    du portefeuille — n'étaient tout simplement pas échangeables.

    IL N'Y A AUCUNE RÈGLE À APPLIQUER. Dans le même catalogue cohabitent
    « usdtbep20 », « usdcbep20 », « unibep20 » et « ethbsc », « linkbsc »,
    « solbsc », « trxbsc » — même chaîne, deux suffixes. « bnb-bsc » prend un
    trait d'union que personne d'autre ne prend. Et « usdt » tout court désigne
    l'Omni Layer, pas Ethereum : concaténer un symbole et un réseau produit
    tôt ou tard un ticker qui existe et qui désigne autre chose.

    D'où une table EXHAUSTIVE, vérifiée ligne à ligne contre get_all_currencies
    (symbole, réseau et adresse de contrat). Ajouter un actif au registre sans
    l'ajouter ici le fait retomber sur le repli, qui ne vaut que pour les
    monnaies natives portant leur nom — et c'est justement là que le piège
    « usdt = Omni » se referme.

    Se tromper ici n'expose à aucune perte : un ticker inconnu fait échouer le
    devis, donc l'échange n'est jamais créé. Ce qu'on perd, c'est la
    fonctionnalité — silencieusement.
    */
    private fun ticker(token: String): String = when (token.uppercase()) {
        // Natives
        "BTC"      -> "btc"
        "ETH"      -> "eth"
        "BNB"      -> "bnb-bsc"    // trait d'union ; « bnb » seul = Beacon Chain
        "SOL"      -> "sol"
        "TRX"      -> "trx"
        // Tether — trois jetons distincts sur trois chaînes
        "USDT"     -> "usdttrc20"  // notre USDT = Tron
        "USDT-ETH" -> "usdterc20"
        "USDT-BNB" -> "usdtbep20"  // et non « usdtbsc », qui n'existe pas
        // Jetons Ethereum
        "USDC"     -> "usdc"
        "DAI"      -> "dai"
        "LINK"     -> "link"
        "SHIB"     -> "shib"
        "PEPE"     -> "pepe"
        "UNI"      -> "uni"
        "AAVE"     -> "aave"
        "WBTC"     -> "wbtc"
        // Jeton BNB Chain
        "CAKE"     -> "cake"
        else       -> token.lowercase()
    }

    /** Notation décimale simple : la notation scientifique est refusée. */
    private fun montantApi(v: Double): String =
        java.math.BigDecimal.valueOf(v).stripTrailingZeros().toPlainString()
}
