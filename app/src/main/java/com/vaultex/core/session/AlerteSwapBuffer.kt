package com.vaultex.core.session

/*
═══════════════════════════════════════════════════════════════════════════
UNE ALERTE QUI MÈNE À L'ÉCHANGE, ET NON À LA PORTE D'ENTRÉE
═══════════════════════════════════════════════════════════════════════════

« BTC est passé au-dessus de 50 000 000 FCFA » arrivait, on la touchait, et
l'application s'ouvrait sur l'accueil. Il fallait ensuite trouver le Swap,
rechoisir la monnaie, rechoisir le sens. Le temps de faire tout ça, le cours
a bougé et l'intention s'est perdue — c'est la définition d'une notification
qui ne sert à rien.

Ce tampon porte ce que l'alerte savait : quelle monnaie, et dans quel sens.

─── POURQUOI LE TAMPON N'EST PAS LE TRANSPORT ───────────────────────────

Il vit en mémoire. Or le worker qui déclenche l'alerte tourne APPLICATION
FERMÉE, et la notification peut être touchée trois heures plus tard, après
que le processus a été tué. Le poser ici au moment de notifier ne servirait
donc à rien : il serait vide quand le doigt arrive.

Ce sont les EXTRAS de l'intention qui traversent la mort du processus —
Android les conserve dans le PendingIntent. MainActivity les lit au réveil
et remplit ce tampon, qui n'assure plus que le passage d'un écran à l'autre,
à l'intérieur de l'application. C'est exactement le trajet de DeepLinkBuffer,
et pour la même raison.

ET PAS UN LIEN `vaultex://swap?de=…`. Un lien est atteignable de l'extérieur :
n'importe quelle application, n'importe quelle page web pourrait en fabriquer
un et pré-remplir un formulaire d'échange chez quelqu'un. Les extras d'un
PendingIntent que l'application a créé elle-même, non.

─── CE QUE CE TAMPON NE FAIT PAS ────────────────────────────────────────

Il ne porte AUCUN montant, et c'est délibéré. Pré-remplir un montant — a
fortiori le solde entier — sur un écran qui mène à l'empreinte digitale
serait pousser quelqu'un à tout vendre sur un mouvement de cours.

Il ne décide rien non plus. L'écran d'échange affiche le taux RECALCULÉ à cet
instant — pas celui de la notification, qui a peut-être déjà rebroussé
chemin — le minimum de la paire et le coût en pourcentage. L'utilisateur
saisit son montant et valide, ou renonce.

Un ordre qui s'exécuterait tout seul supposerait que VaultEx détienne les
fonds. C'est toute la différence entre une alerte qui ouvre l'échange et un
ordre automatique, et elle n'est pas négociable.
═══════════════════════════════════════════════════════════════════════════
*/

/** Sens déduit de la condition de l'alerte. */
enum class SensEchange {
    /** Le cours est monté : on propose de vendre cette monnaie. */
    VENTE,

    /** Le cours est descendu : on propose d'en acheter. */
    ACHAT
}

/** Échange suggéré par une alerte de prix atteinte. */
data class EchangeSuggere(
    /** Symbole de la monnaie visée par l'alerte (BTC, ETH…). */
    val symbole: String,
    val sens: SensEchange
)

object AlerteSwapBuffer {

    @Volatile
    private var enAttente: EchangeSuggere? = null

    /**
     * Mémorise la suggestion. Un symbole vide n'est pas retenu : mieux vaut
     * ouvrir l'accueil que le formulaire d'échange sur une monnaie inconnue.
     */
    fun offer(symbole: String?, vente: Boolean) {
        val s = symbole?.trim()?.uppercase().orEmpty()
        if (s.isEmpty()) return
        enAttente = EchangeSuggere(s, if (vente) SensEchange.VENTE else SensEchange.ACHAT)
    }

    fun hasPending(): Boolean = enAttente != null

    /** Récupère et efface la suggestion en attente. */
    fun consume(): EchangeSuggere? {
        val e = enAttente
        enAttente = null
        return e
    }

    /** Clés des extras. Posées par NotificationHub, lues par MainActivity. */
    const val EXTRA_SYMBOLE = "vaultex_swap_symbole"
    const val EXTRA_VENTE = "vaultex_swap_vente"
}
