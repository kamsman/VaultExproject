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
    val adresses: Map<String, String> = emptyMap()
) {
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
    data class Transmis(val reference: String) : ResultatOrdre()
    data class Echec(val raison: String) : ResultatOrdre()
}

@Singleton
class ChangeService @Inject constructor(
    private val api: com.vaultex.data.remote.api.ChangeApi
) {

    private var cache: ParametresChange? = null
    private var horodatage = 0L

    /**
     * Réglages du change, ou null si le relais n'a pas répondu.
     *
     * NULL PLUTÔT QU'UN REPLI, et c'est le contraire du choix fait ailleurs
     * dans ce dépôt. Un cours manquant se remplace par le dernier connu ;
     * une marge manquante, non. Afficher un prix calculé avec une marge
     * devinée, sur un écran où quelqu'un valide un envoi d'argent, serait
     * lui faire payer un chiffre que personne n'a décidé.
     *
     * Sans réglages, l'écran ne propose rien. C'est désagréable et c'est
     * honnête.
     */
    suspend fun parametres(): ParametresChange? {
        val maintenant = System.currentTimeMillis()
        cache?.let { if (maintenant - horodatage < DUREE_CACHE) return it }
        return try {
            val dto = api.parametres()
            val p = ParametresChange(
                actif = dto.actif ?: false,
                margeFcfaParDollar = dto.margeFcfaParDollar ?: return null,
                minimumFcfa = dto.minimumFcfa ?: 0.0,
                plafondFcfa = dto.plafondFcfa ?: return null,
                numeroMobileMoney = dto.numeroMobileMoney.orEmpty(),
                nomChangeur = dto.nomChangeur.orEmpty(),
                operateur = dto.operateur ?: "Mobile Money",
                delaiMinutes = dto.delaiMinutes ?: 30,
                monnaies = dto.monnaies?.map { m -> m.uppercase() }.orEmpty(),
                adresses = dto.adresses?.mapKeys { (k, _) -> k.uppercase() }.orEmpty()
            )
            cache = p
            horodatage = maintenant
            p
        } catch (e: Exception) {
            com.vaultex.core.monitoring.reportUnlessCancelled("parametres change", e)
            null
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
        if (rep.ok == true) ResultatOrdre.Transmis(rep.reference ?: ordre.reference)
        else ResultatOrdre.Echec(rep.raison ?: "demande refusee")
    } catch (e: Exception) {
        com.vaultex.core.monitoring.reportUnlessCancelled("ordre change", e)
        ResultatOrdre.Echec("Le changeur n'a pas pu etre joint. Reessaie dans un instant.")
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
    val referencePaiement: String = ""
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
        referencePaiement = referencePaiement
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
