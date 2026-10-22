package com.vaultex.domain.fiat

import javax.inject.Inject
import javax.inject.Singleton

/*
═══════════════════════════════════════════════════════════════════════════
LE CHANGE FCFA — CE QUI PARLE AU RELAIS
═══════════════════════════════════════════════════════════════════════════

Deux opérations, et aucune ne déplace d'argent : lire les réglages, et
transmettre une demande.

─── RIEN NE TRANSITE PAR VAULTEX ────────────────────────────────────────

L'utilisateur envoie ses francs au changeur par Orange Money ; le changeur
lui envoie la crypto depuis son propre portefeuille. VaultEx affiche,
calcule et garde la trace. Elle ne reçoit jamais de fonds, et c'est ce qui
la tient à l'écart de l'activité que la BCEAO encadre.

─── POURQUOI PASSER PAR LE RELAIS ───────────────────────────────────────

Le jeton du bot Telegram est compilé dans l'APK. Quiconque décompile
l'application le récupère. Tant qu'il ne sert qu'à des alertes
d'administration, c'est une nuisance ; pour des ORDRES sur lesquels un
changeur agit, ce serait un vol — on forge un résumé crédible, le changeur
envoie la crypto, personne n'a jamais payé.

Le jeton vit donc dans le Worker, et ce fichier ne le voit jamais.

─── CE QUE CELA NE PROTÈGE PAS ──────────────────────────────────────────

L'adresse du relais est publique : n'importe qui peut y poster un faux
ordre. C'est inévitable, et aucun secret porté par une application
distribuée n'y changerait rien.

La protection réelle n'est pas ici. Elle est dans la règle du changeur :
il n'envoie qu'après avoir vu les fonds sur son propre compte. Le message
Telegram la répète à chaque ordre, et c'est voulu — c'est au centième, pas
au premier, qu'on envoie sur une capture d'écran sans vérifier.
═══════════════════════════════════════════════════════════════════════════
*/

/**
 * Réglages du change, servis par le relais.
 *
 * Tout est modifiable sans republier l'application : la marge suit le
 * marché, le plafond suit la confiance, et [actif] ferme le service en une
 * seconde le jour où le changeur n'est pas joignable.
 */
data class ParametresChange(
    val actif: Boolean,
    val margeFcfaParDollar: Double,
    val minimumFcfa: Double,
    val plafondFcfa: Double,
    val numeroMobileMoney: String,
    val nomChangeur: String,
    val operateur: String,
    val delaiMinutes: Int,
    /** Monnaies acceptees a l'achat. */
    val monnaies: List<String> = emptyList(),
    /** Adresses du changeur pour les ventes, par monnaie. */
    val adresses: Map<String, String> = emptyMap(),
    /**
     * Modele de code USSD a pre-remplir sur un achat, ou chaine vide.
     *
     * Regle a distance plutot que compile dans l'application : une syntaxe
     * USSD change sans preavis, et corriger une variable Cloudflare prend
     * dix secondes la ou republier un APK prend une semaine.
     */
    val ussdModele: String = "",
    /** Minimum par monnaie. Voir [minimumPour]. */
    val minimumsParMonnaie: Map<String, Double> = emptyMap(),
    /*
    ═══════════════════════════════════════════════════════════════════════
    LE PLAFOND PAR MONNAIE, EN DOLLARS
    ═══════════════════════════════════════════════════════════════════════

    `plafondFcfa` borne ce que le changeur accepte d'encaisser : une
    limite de tresorerie, qui se pense en francs.

    Celui-ci borne ce qu'il est RAISONNABLE d'acheter d'une monnaie
    donnee. Le BNB ne sert qu'a payer le gaz d'une chaine ; dix dollars
    en couvrent des centaines de transferts, et personne n'en a besoin de
    plus. Cette limite suit la valeur du bien, pas la caisse — donc le
    dollar, converti au cours du jour.
    ═══════════════════════════════════════════════════════════════════════
    */
    val plafondsUsdParMonnaie: Map<String, Double> = emptyMap(),
    /**
     * Le minimum par monnaie, en dollars — symetrique du plafond.
     *
     * `minimumsParMonnaie` est en francs : le seuil sous lequel une
     * operation ne vaut pas la peine d'etre traitee, et il suit le cout
     * local. Celui-ci est en dollars : le seuil sous lequel une quantite
     * de crypto n'a pas de sens, frais de reseau compris.
     */
    val minimumsUsdParMonnaie: Map<String, Double> = emptyMap()
) {

    /**
     * Le minimum applicable a cette monnaie, en francs.
     *
     * ═══════════════════════════════════════════════════════════════════
     * POURQUOI UN SEUIL UNIQUE NE TIENT PAS AVEC PLUSIEURS CHAINES
     * ═══════════════════════════════════════════════════════════════════
     *
     * Les frais de reseau n'ont rien a voir d'une chaine a l'autre :
     *
     *   TRON        13 a 27 TRX par transfert
     *   BNB Chain   environ 0,20 $
     *   Ethereum    1 a 10 $
     *
     * Sur une vente de 8 USDT — cinq mille francs — Ethereum prend entre
     * douze et cent vingt-cinq pour cent de l'operation. Quelqu'un
     * choisirait « USDT-ETH » dans la liste PARCE QU'IL Y EST, et ne
     * decouvrirait le cout qu'au moment de signer dans son portefeuille,
     * bien apres avoir lu « Tu recois 4 800 FCFA » sur l'ecran.
     *
     * L'application aurait dit la verite sur le prix et menti par omission
     * sur le cout.
     *
     * UNE MONNAIE ABSENTE RETOMBE SUR LE SEUIL GENERAL. Ajouter une chaine
     * sans penser a son seuil n'ouvre donc aucun trou : ca applique le
     * comportement d'avant.
     * ═══════════════════════════════════════════════════════════════════
     */
    /**
     * Le plafond applicable, en francs.
     *
     * LE PLUS BAS DES DEUX gagne. Le plafond general protege la caisse du
     * changeur ; celui par monnaie dit ce qu'il est raisonnable d'acheter
     * de cette monnaie-la. Prendre le plus eleve viderait l'un des deux de
     * son sens, et on ne saurait plus lequel.
     *
     * SANS COURS, LE PLAFOND PAR MONNAIE NE S'APPLIQUE PAS. Il est en
     * dollars : sans le taux, le convertir donnerait un chiffre invente,
     * et c'est sur ce chiffre qu'un achat serait refuse. Mieux vaut ne
     * freiner que sur le plafond general, qui est deja en francs.
     */
    fun plafondPour(monnaie: String, fcfaParDollar: Double?): Double {
        val usd = plafondsUsdParMonnaie[monnaie.uppercase()]?.takeIf { it > 0.0 }
        val taux = fcfaParDollar?.takeIf { it > 0.0 }
        if (usd == null || taux == null) return plafondFcfa
        return minOf(plafondFcfa, usd * taux)
    }

    /**
     * Le minimum applicable, en francs.
     *
     * LE PLUS HAUT DES DEUX GAGNE, la ou [plafondPour] prend le plus bas.
     * Les deux bornes se resserrent ; aucune des deux ne doit pouvoir
     * ouvrir ce que l'autre ferme.
     *
     * SANS COURS, LE SEUIL EN DOLLARS NE S'APPLIQUE PAS : le convertir
     * sans taux donnerait un chiffre invente, et c'est sur lui qu'une
     * operation serait refusee.
     */
    fun minimumPour(monnaie: String, fcfaParDollar: Double? = null): Double {
        val enFrancs = minimumsParMonnaie[monnaie.uppercase()]?.takeIf { it > 0.0 }
            ?: minimumFcfa
        val usd = minimumsUsdParMonnaie[monnaie.uppercase()]?.takeIf { it > 0.0 }
        val taux = fcfaParDollar?.takeIf { it > 0.0 }
        if (usd == null || taux == null) return enFrancs
        return maxOf(enFrancs, usd * taux)
    }

    /**
     * Un service annoncé actif mais sans numéro n'est pas utilisable.
     *
     * L'utilisateur verrait un écran complet, cliquerait, et n'aurait
     * personne à payer. On préfère ne rien proposer.
     */
    val utilisable: Boolean
        get() = actif && numeroMobileMoney.isNotBlank() &&
            plafondFcfa > minimumFcfa && monnaies.isNotEmpty()

    /**
     * Vrai si cette monnaie peut etre VENDUE.
     *
     * Acheter ne demande qu'un numero Mobile Money ; vendre demande une
     * adresse ou envoyer la crypto. Sans elle, l'ecran ouvrirait une vente
     * vers personne — et les fonds partiraient sans retour. Les deux sens
     * se decident donc separement.
     */
    fun rachetable(monnaie: String): Boolean =
        adresses[monnaie.uppercase()]?.isNotBlank() == true
}

/** Ce qu'il advient d'une demande transmise. */
sealed class ResultatOrdre {
    data class Transmis(
        val reference: String,
        /** Etat de la verification on-chain, tel que le changeur l'a lu. */
        val verification: EtatVerification = EtatVerification.INCONNUE,
        val txid: String = ""
    ) : ResultatOrdre()
    data class Echec(val raison: String) : ResultatOrdre()
}

/*
═══════════════════════════════════════════════════════════════════════════
LA VERIFICATION ON-CHAIN D'UNE VENTE
═══════════════════════════════════════════════════════════════════════════

Dans une vente, l'utilisateur envoie de la crypto a l'adresse du changeur.
Ca va sur une chaine publique : personne n'a besoin de le croire, il suffit
de regarder. Le relais regarde.

─── CE QUE CET ETAT N'EST PAS ───────────────────────────────────────────

Ce n'est pas une verification faite par le telephone. Elle n'aurait aucune
valeur : l'APK se decompile, on remplace « le transfert existe » par
« oui », et le changeur recoit un ordre verifie qui ne l'est pas. Une preuve
calculee par la partie qu'elle doit convaincre n'est pas une preuve.

Ce qui arrive ici est le VERDICT DU RELAIS, que le telephone ne controle
pas. L'application l'affiche ; elle ne le produit pas, et elle ne peut pas
le changer.

─── POURQUOI L'AFFICHER, ALORS ──────────────────────────────────────────

Parce que l'utilisateur attend, apres avoir envoye des fonds de facon
irreversible. « Le relais voit ton versement : 8 USDT, arrives a 18 h 52 »
est la seule phrase qui reponde a ce moment-la. Et c'est le MEME code qui
decidera du marquage de son ordre : si c'est vert ici, ce sera vert la.
═══════════════════════════════════════════════════════════════════════════
*/
enum class EtatVerification {
    /** La chaine montre le versement. */
    CONFIRME,

    /** On a regarde : il n'y est pas. Pas la meme chose que [INDISPONIBLE]. */
    ABSENT,

    /** Le versement existe, mais il a deja paye une autre demande. */
    DEJA_SERVI,

    /**
     * On n'a PAS pu regarder : noeud muet, adresse non reglee, monnaie dont
     * on ne sait pas lire la chaine.
     *
     * LA DISTINCTION AVEC [ABSENT] EST TOUT L'INTERET DE CET ENUM. Les
     * confondre, c'est soit soupconner des ventes honnetes chaque fois
     * qu'un noeud public tousse, soit annoncer « verifie » sans avoir rien
     * verifie. La deuxieme est la pire.
     */
    INDISPONIBLE,

    /** Rien n'a encore ete demande, ou la reponse est illisible. */
    INCONNUE;

    companion object {
        fun depuisTexte(v: String?): EtatVerification = when (v?.trim()?.lowercase()) {
            "confirme" -> CONFIRME
            "absent" -> ABSENT
            "deja_servi" -> DEJA_SERVI
            "indisponible", "inconnue" -> INDISPONIBLE
            else -> INCONNUE
        }
    }
}

/** Ce que le relais a lu sur la chaine, tel que l'ecran l'affiche. */
data class Verification(
    val etat: EtatVerification,
    val txid: String = "",
    /** Montant LU SUR LA CHAINE, jamais celui qui a ete annonce. */
    val montant: Double? = null,
    val quand: Long = 0L,
    /** Faux seulement sur Bitcoin : vu, pas encore mine. */
    val confirme: Boolean = true,
    /** Adresse qui a envoye, lue sur la chaine. Voir VerificationDto.de. */
    val de: String = "",
    val raison: String = "",
    val explorateur: String = ""
) {
    val estVert: Boolean get() = etat == EtatVerification.CONFIRME

    /**
     * Vrai quand il n'y a rien a attendre : insister ne changera rien.
     *
     * Un noeud muet ou une monnaie dont on ne lit pas la chaine ne
     * deviendra pas lisible en reessayant quinze secondes plus tard. Un
     * versement absent, si — c'est le cas d'un retrait encore en file chez
     * une plateforme d'echange.
     */
    val sansEspoir: Boolean
        get() = etat == EtatVerification.INDISPONIBLE || etat == EtatVerification.DEJA_SERVI
}

/*
═══════════════════════════════════════════════════════════════════════════
POURQUOI LE RÉSULTAT DIT AUSSI POURQUOI
═══════════════════════════════════════════════════════════════════════════

`parametres()` rendait `null` pour tout, et l'écran affichait « Service
indisponible pour le moment. Réessaie dans un instant. »

VU SUR UN VRAI TÉLÉPHONE, SANS DONNÉES MOBILES. L'écran accusait le
service d'être en panne alors que c'était la connexion — et la phrase
qu'il affichait, « réessaie dans un instant », disait exactement la chose
à ne pas faire : attendre. Quelqu'un peut rester devant cet écran
indéfiniment, à réessayer, sans jamais penser à activer ses données.

C'est le même défaut que « Vérifie ta connexion » affiché par
l'assistance sur un réseau qui marchait, déjà corrigé — à l'envers. Dans
les deux cas, une seule phrase servait pour deux causes qui appellent deux
gestes opposés.

─── COMMENT ON DISTINGUE, ET C'EST MESURABLE ───────────────────────────

Par le TYPE de l'échec, pas par une devinette. Retrofit lève une
`IOException` quand l'appel n'a pas pu aboutir — DNS muet, connexion
refusée, délai dépassé. Il lève une `HttpException` quand le serveur a
répondu, mais mal. Et une erreur d'analyse du corps n'est ni l'une ni
l'autre.

La première veut dire « le téléphone n'est pas sorti ». Les deux autres
veulent dire « le relais a parlé et ce qu'il a dit ne sert à rien ». Ce
sont deux situations, deux messages, deux gestes.

On ne demande rien à `ConnectivityManager` : ce qu'il pense de la
connexion est une opinion, l'exception est un fait.
═══════════════════════════════════════════════════════════════════════════
*/
sealed interface ResultatParametres {

    data class Ok(val parametres: ParametresChange) : ResultatParametres

    /**
     * Le téléphone n'a pas pu sortir.
     *
     * Données mobiles coupées, Wi-Fi sans Internet, DNS muet. L'écran doit
     * parler de la CONNEXION, et surtout pas du service.
     *
     * [detail] porte le nom de l'exception — `UnknownHostException`,
     * `ConnectException`… Il s'affiche en petit sous le message : sans
     * lui, une capture d'écran ne permet pas de distinguer les trois
     * causes, et on en est réduit à deviner.
     */
    data class HorsLigne(val detail: String = "") : ResultatParametres

    /*
    ═══════════════════════════════════════════════════════════════════════
    LA CONNEXION SÉCURISÉE A ÉTÉ REFUSÉE — ET CE N'EST PAS « HORS LIGNE »
    ═══════════════════════════════════════════════════════════════════════

    `SSLException` HÉRITE D'`IOException`. Tout ce qui touche au TLS
    tombait donc dans [HorsLigne], et l'écran disait « vérifie tes données
    mobiles » — à quelqu'un dont les données marchaient parfaitement.

    C'est la faute que cet écran venait d'être corrigé pour ne plus
    commettre, refaite une branche plus loin : une seule phrase pour deux
    causes qui appellent deux gestes opposés.

    ─── LES TROIS CAUSES RÉELLES, ET LEUR ORDRE ────────────────────────

    LA DATE DU TÉLÉPHONE, de loin la plus fréquente. Un certificat n'est
    valable qu'entre deux dates ; une horloge qui a dérivé de quelques
    jours les rend toutes les deux fausses. L'utilisateur peut le corriger
    seul, en dix secondes, et il n'y penserait jamais.

    UN PORTAIL WI-FI qui intercepte la connexion avant d'avoir été
    accepté — hôtel, cybercafé.

    UNE EMPREINTE ÉPINGLÉE qui ne correspond plus. Là, l'utilisateur ne
    peut rien : c'est à nous de republier. Mais le relais n'est pas
    épinglé, donc ce cas-là ne devrait pas toucher cet écran — et s'il le
    touche, `detail` le dira.
    ═══════════════════════════════════════════════════════════════════════
    */
    data class Securise(val detail: String = "") : ResultatParametres

    /**
     * Le relais a répondu, et sa réponse n'est pas utilisable.
     *
     * Service fermé côté serveur, réglage manquant, corps illisible. Là,
     * « réessaie dans un instant » est la bonne phrase.
     */
    object Indisponible : ResultatParametres
}

@Singleton
class ChangeService @Inject constructor(
    private val api: com.vaultex.data.remote.api.ChangeApi
) {

    private var cache: ParametresChange? = null
    private var horodatage = 0L

    /**
     * Réglages du change, ou la raison pour laquelle il n'y en a pas.
     *
     * PAS DE REPLI SUR DES RÉGLAGES DEVINÉS, et c'est le contraire du choix
     * fait ailleurs dans ce dépôt. Un cours manquant se remplace par le
     * dernier connu ; une marge manquante, non. Afficher un prix calculé
     * avec une marge devinée, sur un écran où quelqu'un valide un envoi
     * d'argent, serait lui faire payer un chiffre que personne n'a décidé.
     *
     * Sans réglages, l'écran ne propose rien. C'est désagréable et c'est
     * honnête — mais il doit dire POURQUOI, voir [ResultatParametres].
     */
    suspend fun parametres(): ResultatParametres {
        val maintenant = System.currentTimeMillis()
        cache?.let {
            if (maintenant - horodatage < DUREE_CACHE) return ResultatParametres.Ok(it)
        }
        return try {
            val dto = api.parametres()
            val p = ParametresChange(
                actif = dto.actif ?: false,
                // Le relais a parlé, mais sans ces deux chiffres on ne peut
                // calculer aucun prix : c'est « indisponible », pas « hors
                // ligne ».
                margeFcfaParDollar = dto.margeFcfaParDollar
                    ?: return ResultatParametres.Indisponible,
                minimumFcfa = dto.minimumFcfa ?: 0.0,
                plafondFcfa = dto.plafondFcfa ?: return ResultatParametres.Indisponible,
                numeroMobileMoney = dto.numeroMobileMoney.orEmpty(),
                nomChangeur = dto.nomChangeur.orEmpty(),
                operateur = dto.operateur ?: "Mobile Money",
                delaiMinutes = dto.delaiMinutes ?: 30,
                monnaies = dto.monnaies?.map { m -> m.uppercase() }.orEmpty(),
                adresses = dto.adresses?.mapKeys { (k, _) -> k.uppercase() }.orEmpty(),
                ussdModele = dto.ussdModele.orEmpty(),
                minimumsParMonnaie = dto.minimumsParMonnaie
                    ?.mapKeys { (k, _) -> k.uppercase() }
                    ?.filterValues { v -> v > 0.0 }
                    .orEmpty(),
                // Meme nettoyage que les minimums : clefs en capitales, et
                // une valeur nulle ou negative n'est pas un plafond.
                plafondsUsdParMonnaie = dto.plafondsUsdParMonnaie
                    ?.mapKeys { (k, _) -> k.uppercase() }
                    ?.filterValues { v -> v > 0.0 }
                    .orEmpty(),
                minimumsUsdParMonnaie = dto.minimumsUsdParMonnaie
                    ?.mapKeys { (k, _) -> k.uppercase() }
                    ?.filterValues { v -> v > 0.0 }
                    .orEmpty()
            )
            cache = p
            horodatage = maintenant
            ResultatParametres.Ok(p)
        } catch (e: java.io.IOException) {
            /*
            L'APPEL N'A PAS ABOUTI : le téléphone n'est pas sorti.

            `UnknownHostException` quand le DNS ne répond pas — le cas
            exact des données coupées — `ConnectException` quand rien
            n'écoute, `SocketTimeoutException` quand ça traîne. Toutes
            héritent d'`IOException`, et aucune n'est une panne du
            service.

            `HttpException` de Retrofit n'en hérite PAS : une réponse 500
            tombe donc dans le `catch` suivant, ce qui est correct — le
            relais a parlé.
            */
            com.vaultex.core.monitoring.reportUnlessCancelled("parametres change hors ligne", e)
            /*
            ON SÉPARE LE TLS DU RESTE, parce que `SSLException` hérite
            d'`IOException` et que les deux appellent des gestes opposés :
            l'un fait regarder ses données mobiles, l'autre la date du
            téléphone.
            */
            val nom = e::class.java.simpleName
            if (e is javax.net.ssl.SSLException) ResultatParametres.Securise(nom)
            else ResultatParametres.HorsLigne(nom)
        } catch (e: Exception) {
            com.vaultex.core.monitoring.reportUnlessCancelled("parametres change", e)
            ResultatParametres.Indisponible
        }
    }

    /**
     * Transmet une demande au changeur.
     *
     * LA RÉFÉRENCE EST FOURNIE PAR L'APPELANT, et c'est important : elle est
     * calculée avant l'appel, affichée à l'utilisateur, et renvoyée telle
     * quelle si l'appel se répète. Une coupure réseau suivie d'un second
     * essai ne crée donc pas deux demandes — le relais reconnaît la
     * référence et ne poste qu'une fois.
     *
     * C'est le même raisonnement que pour l'envoi de Pi : connaître
     * l'identifiant AVANT de transmettre est ce qui rend un échec ambigu
     * récupérable.
     */
    suspend fun transmettre(ordre: OrdreChange): ResultatOrdre = try {
        val rep = api.ordre(ordre.versCorps())
        if (rep.ok == true) ResultatOrdre.Transmis(
            reference = rep.reference ?: ordre.reference,
            verification = EtatVerification.depuisTexte(rep.verification),
            txid = rep.txid.orEmpty()
        )
        else ResultatOrdre.Echec(rep.raison ?: "Demande refusée, sans raison donnée.")
    } catch (e: retrofit2.HttpException) {
        /*
        ═══════════════════════════════════════════════════════════════════
        UN SEUL MESSAGE POUR TOUTES LES CAUSES NE SERT À PERSONNE
        ═══════════════════════════════════════════════════════════════════

        Tout échouait sur « Le changeur n'a pas pu être joint ». Or les
        causes n'ont rien à voir, et surtout : elles n'appellent pas la
        même action.

          503 « canal non configuré » → le jeton Telegram ou l'identifiant
          du groupe manque dans le Worker. Réessayer ne servira JAMAIS à
          rien, il faut poser le réglage.

          400 « ordre incomplet » → un champ vide dans la demande. Un
          défaut de l'application, à corriger.

          502 « telegram injoignable » → là, oui, réessayer a du sens.

        Dire « réessaie dans un instant » devant un réglage absent, c'est
        envoyer quelqu'un recommencer indéfiniment une opération qui ne
        peut pas aboutir. On lit donc la raison que le relais donne, et on
        la transmet telle quelle.
        ═══════════════════════════════════════════════════════════════════
        */
        val corps = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
        val raison = runCatching {
            com.google.gson.JsonParser.parseString(corps).asJsonObject
                .get("raison")?.takeIf { j -> !j.isJsonNull }?.asString
        }.getOrNull()
        com.vaultex.core.monitoring.reportUnlessCancelled("ordre change", e)
        ResultatOrdre.Echec(
            when {
                raison == "canal non configure" ->
                    "Le canal du changeur n'est pas configuré côté serveur. " +
                        "Ce n'est pas un problème de réseau : réessayer n'y changera rien."
                raison == "ordre incomplet" ->
                    "La demande est incomplète. Reviens au calcul et recommence."
                raison != null -> "Demande refusée : $raison"
                else -> "Le serveur a répondu ${e.code()}. Réessaie dans un instant."
            }
        )
    } catch (e: Exception) {
        com.vaultex.core.monitoring.reportUnlessCancelled("ordre change", e)
        ResultatOrdre.Echec("Le changeur n'a pas pu être joint. Vérifie ta connexion.")
    }

    /**
     * Demande au relais s'il voit le versement sur la chaine.
     *
     * ═══════════════════════════════════════════════════════════════════
     * AUCUN CACHE ICI, CONTRAIREMENT AUX REGLAGES
     * ═══════════════════════════════════════════════════════════════════
     *
     * On appelle cette methode toutes les quinze secondes pendant que
     * l'utilisateur attend qu'un versement apparaisse. Une reponse gardee
     * une minute repondrait « pas encore » pendant une minute apres
     * l'arrivee des fonds — soit precisement le contraire de ce qu'on
     * cherche.
     *
     * ═══════════════════════════════════════════════════════════════════
     * UN ECHEC RESEAU N'EST PAS UNE ABSENCE
     * ═══════════════════════════════════════════════════════════════════
     *
     * Il rend INDISPONIBLE, et surtout pas ABSENT. Dire « aucun versement
     * trouve » a quelqu'un dont le telephone a juste perdu la 3G, apres
     * qu'il a envoye des fonds de facon irreversible, serait lui annoncer
     * une perte qui n'a pas eu lieu.
     */
    suspend fun verifier(
        monnaie: String,
        montant: String,
        txid: String = "",
        reference: String = ""
    ): Verification = try {
        val dto = api.verifier(monnaie.uppercase(), montant, txid.trim(), reference.trim())
        Verification(
            etat = EtatVerification.depuisTexte(dto.etat),
            txid = dto.txid.orEmpty(),
            montant = dto.montant?.takeIf { it > 0.0 },
            quand = dto.quand ?: 0L,
            confirme = dto.confirme ?: true,
            de = dto.de.orEmpty(),
            raison = dto.raison.orEmpty(),
            explorateur = dto.explorateur.orEmpty()
        )
    } catch (e: Exception) {
        com.vaultex.core.monitoring.reportUnlessCancelled("verifier vente", e)
        Verification(
            etat = EtatVerification.INDISPONIBLE,
            raison = "le relais n'a pas répondu"
        )
    }

    /**
     * Demande au relais s'il voit le paiement Orange Money.
     *
     * ═══════════════════════════════════════════════════════════════════
     * CE QU'ELLE CORRIGE
     * ═══════════════════════════════════════════════════════════════════
     *
     * L'achat etait deja verifie — mais seulement au depot de l'ordre, et
     * le verdict n'allait qu'au changeur. Quelqu'un qui recopiait mal sa
     * reference voyait « demande transmise » et attendait ; c'est le
     * changeur qui decouvrait le ❌, dans son canal, parmi d'autres
     * messages.
     *
     * L'erreur se corrige maintenant la ou elle est commise : dans le
     * champ qu'on vient de remplir, par celui qui l'a rempli.
     *
     * ═══════════════════════════════════════════════════════════════════
     * LE MONTANT EST EN FRANCS, ET IL EST RECOMPARE PAR LE RELAIS
     * ═══════════════════════════════════════════════════════════════════
     *
     * C'est le montant LU DANS LE SMS de l'operateur qui tranche, jamais
     * celui que le telephone annonce. Recopier la reference d'un vrai
     * paiement de mille francs pour une demande de quarante mille ne rend
     * donc pas vert.
     *
     * ═══════════════════════════════════════════════════════════════════
     * UN ECHEC RESEAU N'EST PAS UNE ABSENCE — MEME RAISON QU'A LA VENTE
     * ═══════════════════════════════════════════════════════════════════
     *
     * INDISPONIBLE, et surtout pas ABSENT. Dire « aucun paiement trouve »
     * a quelqu'un qui vient de payer, parce que SON telephone a perdu la
     * 3G, serait lui annoncer une perte qui n'a pas eu lieu — et le
     * pousser a payer une seconde fois.
     */
    suspend fun verifierAchat(
        montantFcfa: String,
        telephone: String = "",
        referencePaiement: String = ""
    ): Verification = try {
        val dto = api.verifierAchat(montantFcfa, telephone.trim(), referencePaiement.trim())
        Verification(
            etat = EtatVerification.depuisTexte(dto.etat),
            txid = dto.txid.orEmpty(),
            montant = dto.montant?.takeIf { it > 0.0 },
            quand = dto.quand ?: 0L,
            confirme = dto.confirme ?: true,
            de = dto.de.orEmpty(),
            raison = dto.raison.orEmpty(),
            explorateur = dto.explorateur.orEmpty()
        )
    } catch (e: Exception) {
        com.vaultex.core.monitoring.reportUnlessCancelled("verifier achat", e)
        Verification(
            etat = EtatVerification.INDISPONIBLE,
            raison = "le relais n'a pas répondu"
        )
    }

    /**
     * Le montant exact a envoyer, ou null si le relais n'a pas repondu.
     *
     * ═══════════════════════════════════════════════════════════════════
     * NULL DOIT ARRETER L'OPERATION, PAS LA LAISSER CONTINUER
     * ═══════════════════════════════════════════════════════════════════
     *
     * Si le relais marque les montants et qu'on n'arrive pas a lui
     * demander lequel, laisser l'utilisateur envoyer un montant rond le
     * conduirait droit au mur : le relais chercherait 2,004731, il aurait
     * envoye 2, et son versement ne serait jamais reconnu. Irreversible.
     *
     * L'ecran doit donc refuser d'avancer plutot que d'avancer avec le
     * mauvais chiffre. C'est desagreable et c'est la seule option sure.
     * ═══════════════════════════════════════════════════════════════════
     */
    suspend fun consigne(monnaie: String, montant: String, reference: String): String? = try {
        val dto = api.consigne(monnaie.uppercase(), montant, reference)
        dto.montantExact?.trim()?.takeIf { it.isNotBlank() && dto.ok == true }
    } catch (e: Exception) {
        com.vaultex.core.monitoring.reportUnlessCancelled("consigne change", e)
        null
    }

    private companion object {
        /**
         * Une minute, comme le cache du relais.
         *
         * Au-delà, une fermeture du service mettrait du temps à se voir sur
         * les téléphones déjà ouverts — ce qui annulerait l'intérêt d'un
         * interrupteur distant.
         */
        const val DUREE_CACHE = 60_000L
    }
}

/**
 * Une demande de change, telle qu'elle part vers le changeur.
 *
 * Tous les champs sont du TEXTE DÉJÀ MIS EN FORME. Le message Telegram est
 * lu par un humain qui doit décider vite : il ne doit avoir ni calcul à
 * faire, ni unité à deviner. La mise en forme a lieu là où les chiffres
 * sont connus, pas dans un gabarit distant.
 */
data class OrdreChange(
    val reference: String,
    /** « achat » (francs vers crypto) ou « vente ». */
    val sens: String,
    val monnaie: String,
    val montantFcfa: String,
    val montantCrypto: String,
    val taux: String,
    val marge: String,
    /** Adresse de réception, pour un achat. */
    val adresse: String = "",
    /** Numéro depuis lequel l'utilisateur a payé, pour retrouver l'opération. */
    val telephone: String = "",
    /**
     * Référence du paiement Mobile Money.
     *
     * Elle ne PROUVE rien — une référence se recopie depuis l'opération de
     * quelqu'un d'autre, et une capture d'écran se fabrique. Elle sert au
     * changeur à RETROUVER la ligne dans son relevé. C'est son relevé qui
     * fait foi, et lui seul.
     */
    val referencePaiement: String = "",
    /**
     * Hash de la transaction, pour une vente.
     *
     * Facultatif, et c'est important : sur les chaines ou les versements
     * s'enumerent — TRC-20, Bitcoin, jetons ERC-20 — le relais trouve sans
     * lui. On ne demande donc pas a quelqu'un de recopier soixante-quatre
     * caracteres hexadecimaux, ce qu'il rate une fois sur deux.
     *
     * Il reste utile dans deux cas : les versements natifs (BNB, ETH)
     * n'emettent aucun journal et ne s'enumerent pas, et un hash fourni
     * rend la recherche immediate au lieu d'attendre le prochain sondage.
     */
    val txid: String = ""
) {
    fun versCorps() = com.vaultex.data.remote.dto.OrdreChangeBody(
        reference = reference,
        sens = sens,
        monnaie = monnaie,
        montantFcfa = montantFcfa,
        montantCrypto = montantCrypto,
        taux = taux,
        marge = marge,
        adresse = adresse,
        telephone = telephone,
        referencePaiement = referencePaiement,
        txid = txid
    )

    companion object {
        /**
         * Une référence courte, lisible au téléphone, et sans ambiguïté.
         *
         * Pas de caractères qui se confondent à l'oral ou à l'écrit — ni O
         * ni 0, ni I ni 1. Elle sera dictée, recopiée à la main et cherchée
         * dans un fil Telegram ; une confusion coûterait un rapprochement
         * raté entre un paiement et une demande.
         */
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        /**
         * Reconnaitre une reference de DEMANDE la ou l'on attend celle
         * d'un PAIEMENT.
         *
         * Les deux sont affichees sur l'ecran de paiement et portent le
         * meme mot. Celle-ci se met en motif du virement ; l'autre se lit
         * dans le SMS de l'operateur. Les intervertir ne produit qu'un
         * « aucun encaissement ne porte cette reference », qui accuse le
         * paiement au lieu du champ.
         *
         * IL EST DERIVE DE L'ALPHABET, et non recopie : changer celui-ci
         * sans toucher au motif ferait cesser la reconnaissance en
         * silence. L'alphabet ne contient ni metacaractere ni tiret, donc
         * son insertion dans une classe est sure.
         *
         * S'applique a la forme NORMALISEE — sans tiret, en capitales —
         * la meme que le relais range.
         */
        val MOTIF_REFERENCE_DEMANDE: Regex = Regex("^VX[$ALPHABET]{8}$")

        /**
         * HUIT CARACTÈRES, ET NON SIX.
         *
         * Le relais refuse une référence déjà vue pendant une heure — c'est
         * ce qui empêche un renvoi après coupure réseau de créer un
         * doublon. Mais la même protection se retourne si DEUX demandes
         * différentes tirent la même référence : la seconde est avalée en
         * silence, et l'application annonce « transmis » à quelqu'un dont
         * la demande n'est jamais arrivée.
         *
         * Sur six caractères, 32^6 fait un milliard de combinaisons : sur
         * cinq cents demandes dans l'heure, une répétition survient environ
         * une fois sur 8 600. Rare, et certain à l'échelle d'une année.
         *
         * Sur huit, 32^8 fait mille milliards, et la même probabilité tombe
         * à une sur dix millions. Deux caractères de plus se dictent aussi
         * bien, et ferment le cas.
         */
        fun nouvelleReference(): String {
            val alea = java.security.SecureRandom()
            return "VX-" + (1..8).map { ALPHABET[alea.nextInt(ALPHABET.length)] }.joinToString("")
        }
    }
}
