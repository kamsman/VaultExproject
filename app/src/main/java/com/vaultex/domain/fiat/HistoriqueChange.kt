package com.vaultex.domain.fiat

import javax.inject.Inject
import javax.inject.Singleton

/*
═══════════════════════════════════════════════════════════════════════════
CE QUE L'UTILISATEUR PEUT MONTRER
═══════════════════════════════════════════════════════════════════════════

Une fois sa demande partie, il n'avait plus rien. La référence disparaissait
avec l'écran, et il se retrouvait avec un papier — ou rien du tout.

Or c'est exactement le moment où il a besoin de quelque chose. Il vient
d'envoyer de l'argent à une personne qu'il ne connaît pas, hors de
l'application, et il attend. S'il doit réclamer, il lui faut une trace : ce
qu'il a demandé, quand, pour combien, et sous quelle référence.

─── ÇA NE PROUVE RIEN, ET CE N'EST PAS LE BUT ───────────────────────────

Cet historique dit ce que l'utilisateur a DEMANDÉ, pas ce qu'il a payé.
C'est lui qui l'écrit, depuis son téléphone : il ne prouve rien au
changeur, et il ne doit jamais être présenté comme une preuve.

Ce qu'il fait, et qui suffit : rendre une réclamation possible. « J'ai
demandé VX-UK4GWGQH le 9 octobre à 18 h 52, 5 000 francs contre 8 USDT »
se vérifie en trente secondes dans le fil Telegram du changeur. Sans cette
phrase, il n'y a rien à vérifier.

─── AUCUN SECRET ICI ────────────────────────────────────────────────────

Une référence, des montants, une date. Rien qui permette de déplacer des
fonds, rien qui identifie au-delà de ce que l'utilisateur a lui-même saisi.
C'est pourquoi la liste peut vivre en clair dans les préférences, comme
l'instantané du portefeuille.

Elle y gagne aussi quelque chose : le PIN de panique vide ces préférences
en bloc. L'historique part donc avec le reste, sans qu'on ait pensé à lui
— et il devait partir. « 50 000 FCFA contre 80 USDT » ne nomme personne,
mais laissé sur un téléphone qu'on vient de remettre à quelqu'un sous
contrainte, ça dit qu'il y a de l'argent et combien.

NE PAS DÉPLACER CE STOCKAGE AILLEURS pour cette raison : hors des
préférences, il survivrait à l'effacement d'urgence.
═══════════════════════════════════════════════════════════════════════════
*/

/** Une demande passée, telle que l'utilisateur peut la rappeler. */
data class DemandeChange(
    val reference: String = "",
    /** « achat » ou « vente ». */
    val sens: String = "",
    val monnaie: String = "",
    val montantFcfa: String = "",
    val montantCrypto: String = "",
    val taux: String = "",
    val horodatage: Long = 0L
) {
    val estAchat: Boolean get() = sens == "achat"
}

@Singleton
class HistoriqueChange @Inject constructor(
    private val secureStorage: com.vaultex.core.security.SecureStorage
) {

    private val gson = com.google.gson.Gson()

    /** Les demandes, de la plus récente à la plus ancienne. */
    fun lire(): List<DemandeChange> {
        val json = secureStorage.getHistoriqueChange() ?: return emptyList()
        return try {
            val tableau = gson.fromJson(json, Array<DemandeChange>::class.java)
            /*
            Une entrée sans référence ne sert à rien : elle ne permet ni de
            réclamer ni de retrouver quoi que ce soit. On la jette à la
            lecture plutôt que de l'afficher comme une ligne vide, qui
            inquiéterait sans informer.
            */
            tableau?.filter { it.reference.isNotBlank() }.orEmpty()
        } catch (_: Exception) {
            // Historique illisible : on repart de rien plutôt que de
            // planter un écran. Ce n'est qu'un confort, jamais une preuve.
            emptyList()
        }
    }

    fun ajouter(demande: DemandeChange) {
        if (demande.reference.isBlank()) return
        val liste = fusionner(lire(), demande)
        try {
            secureStorage.saveHistoriqueChange(gson.toJson(liste))
        } catch (_: Exception) { /* un confort, jamais bloquant */ }
    }

    fun vider() = secureStorage.saveHistoriqueChange(null)

    companion object {
        /**
         * Cinquante demandes.
         *
         * Au-delà, personne ne remonte : une réclamation porte sur
         * aujourd'hui ou sur hier. Et une liste qui grandit sans fin finit
         * par peser sur des préférences qu'on relit à chaque ouverture.
         */
        const val MAX = 50

        /**
         * La nouvelle demande en tête, sans doublon, bornée à [MAX].
         *
         * FONCTION PURE, et c'est délibéré : c'est la seule partie de ce
         * fichier où l'on peut se tromper — perdre une entrée, en garder
         * deux fois la même, inverser l'ordre — et la seule qui se teste
         * sans stockage ni téléphone.
         *
         * LE DÉDOUBLONNAGE SE FAIT SUR LA RÉFÉRENCE. Une demande renvoyée
         * après une coupure réseau porte la même : le relais ne la poste
         * qu'une fois, l'historique ne doit pas la montrer deux fois.
         */
        fun fusionner(
            existantes: List<DemandeChange>,
            nouvelle: DemandeChange
        ): List<DemandeChange> =
            (listOf(nouvelle) + existantes.filter { it.reference != nouvelle.reference })
                .sortedByDescending { it.horodatage }
                .take(MAX)
    }
}
