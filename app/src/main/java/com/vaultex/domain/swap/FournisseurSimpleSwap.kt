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

    companion object {
        /**
         * Statut rendu quand le fournisseur ne connaît pas l'identifiant.
         *
         * Ce n'est pas un état de l'échange chez lui : c'est notre façon de
         * dire « cette référence ne mène nulle part ». Le suivi s'en sert pour
         * arrêter de la poursuivre — voir SwapTrackingWorker.
         */
        const val STATUT_INCONNU = "not_found"
    }

    override val commissionPourcent: Double = ApiKeys.SIMPLESWAP_COMMISSION

    /*
    ═══════════════════════════════════════════════════════════════════════
    UNE SEULE COPIE DU MODE, LUE PAR LES TROIS APPELS
    ═══════════════════════════════════════════════════════════════════════

    `fixed` apparaît dans get_estimated, get_ranges ET create_exchange. Les
    trois DOIVENT dire la même chose, et rien dans l'API ne le vérifie.

    Deviser en fixe et créer en flottant afficherait un montant et en
    livrerait un autre — sans aucune erreur nulle part, puisque chaque appel
    pris isolément serait valide. C'est exactement la classe de défaut qu'on
    ne voit qu'en comptant ce qu'on a reçu.

    Et les BORNES diffèrent entre les deux modes : un minimum lu en
    flottant, affiché, puis un échange créé en fixe qui le refuse, c'est un
    utilisateur qui saisit le montant qu'on lui a dit et qu'on rejette.

    D'où cette seule ligne, que les trois lisent. Il n'y a pas d'endroit où
    se tromper.
    ═══════════════════════════════════════════════════════════════════════
    */
    private val tauxFixe: Boolean = ApiKeys.SIMPLESWAP_TAUX_FIXE

    override suspend fun devis(de: String, vers: String, montant: Double): DevisSwap {
        val brut = api.getEstimated(
            apiKey = ApiKeys.SIMPLESWAP,
            from = ticker(de),
            to = ticker(vers),
            amount = montantApi(montant),
            fixed = tauxFixe
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
            to = ticker(vers),
            fixed = tauxFixe
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
                fixed = tauxFixe,
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
        val r = api.getExchange(ApiKeys.SIMPLESWAP, id)
        StatutSwap(
            id = r.id ?: id,
            statut = r.status.orEmpty(),
            hashDepot = r.txFrom,
            hashSortie = r.txTo,
            montantRecu = r.amountTo
        )
    } catch (e: retrofit2.HttpException) {
        /*
        ═══════════════════════════════════════════════════════════════════
        UN 404 N'EST PAS UNE PANNE, C'EST UNE RÉPONSE
        ═══════════════════════════════════════════════════════════════════

        « Not Found » sur get_exchange signifie que le fournisseur ne connaît
        pas cet identifiant. Ce n'est ni un service indisponible, ni une
        situation qui s'arrangera : réinterroger la même référence donnera le
        même résultat, indéfiniment.

        Le suivi repassant toutes les quinze minutes pendant sept jours, une
        seule référence perdue produisait environ six cent soixante alertes
        « Service indisponible : statut SimpleSwap — HTTP 404 ». Constaté sur
        le canal d'administration, où elles noyaient tout le reste. Une alerte
        qui crie au loup finit par faire ignorer les vraies.

        LA CAUSE LA PLUS PROBABLE EST UN CHANGEMENT DE FOURNISSEUR. Les
        identifiants de ChangeNOW et de SimpleSwap ne se ressemblent pas et ne
        sont pas interchangeables : basculer `swap.provider` laisse les
        échanges en cours de l'ancien service, que le nouveau ne connaîtra
        jamais. Viennent ensuite un échange purgé après des semaines, ou une
        création qui a échoué après avoir été enregistrée ici.

        On rend donc un statut NOMMÉ plutôt que null. L'appelant peut alors
        cesser de poursuivre une référence morte, au lieu de confondre « je
        n'ai pas pu demander » avec « la réponse est : inconnu ».
        */
        if (e.code() == 404) StatutSwap(id = id, statut = STATUT_INCONNU)
        else {
            com.vaultex.core.monitoring.reportUnlessCancelled("statut SimpleSwap", e)
            null
        }
    } catch (e: Exception) {
        com.vaultex.core.monitoring.reportUnlessCancelled("statut SimpleSwap", e)
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
