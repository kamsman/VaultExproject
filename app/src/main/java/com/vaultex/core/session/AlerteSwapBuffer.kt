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

    /*
    ═══════════════════════════════════════════════════════════════════════
    L'INTENTION NE PORTE PLUS LA SUGGESTION, SEULEMENT UN JETON
    ═══════════════════════════════════════════════════════════════════════

    MainActivity est EXPORTÉE — elle doit l'être, c'est le point d'entrée du
    lanceur. N'importe quelle application installée peut donc la démarrer
    avec les extras de son choix.

    La première version posait le symbole et le sens directement dans
    l'intention. Une autre application pouvait alors ouvrir VaultEx sur le
    formulaire d'échange, positionné sur la monnaie de son choix. Elle ne
    pouvait ni saisir de montant, ni confirmer — l'empreinte garde cette
    porte — mais faire surgir le vrai écran d'échange au bon moment donne
    du crédit à une mise en scène, et c'est précisément ce qu'on refuse à
    un lien `vaultex://`. Le refuser au lien pour l'accepter par les extras
    n'aurait eu aucun sens.

    L'intention ne porte donc plus qu'un JETON ALÉATOIRE. La suggestion
    elle-même est déposée ici, par le worker, au moment de notifier. Un
    jeton inventé ne correspond à rien, et la lecture l'efface : il ne sert
    qu'une fois.

    POURQUOI SUR DISQUE. Le worker tourne application fermée, et la
    notification peut être touchée des heures plus tard, le processus tué
    entre-temps. La mémoire ne suffit donc pas — c'est la raison même pour
    laquelle les extras avaient été choisis au départ.
    ═══════════════════════════════════════════════════════════════════════
    */

    /** Clé de l'extra. Posée par NotificationHub, lue par MainActivity. */
    const val EXTRA_JETON = "vaultex_swap_jeton"

    private const val PREFS = "vaultex_alerte_swap"
    /** Au-delà, une suggestion ne vaut plus rien : le cours a changé. */
    private const val VALIDITE_MS = 7L * 24 * 60 * 60 * 1000

    private fun prefs(context: android.content.Context) =
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)

    /**
     * Dépose une suggestion et renvoie le jeton qui permettra de la relire.
     * Renvoie null si le symbole est vide : pas de jeton, pas d'extra, et la
     * notification ouvre l'application comme n'importe quelle autre.
     */
    fun deposer(context: android.content.Context, symbole: String?, vente: Boolean): String? {
        val s = symbole?.trim()?.uppercase().orEmpty()
        if (s.isEmpty()) return null
        val jeton = java.util.UUID.randomUUID().toString()
        val p = prefs(context)
        /*
        On balaie les entrees perimees a chaque depot. Sans cela, une
        suggestion dont la notification a ete balayee sans etre touchee
        resterait indefiniment : le fichier grossirait d'une ligne par alerte
        declenchee, pour des donnees que personne ne relira jamais.
        */
        val maintenant = System.currentTimeMillis()
        val e = p.edit()
        p.all.keys.toList().forEach { cle ->
            val horodatage = cle.substringAfterLast('|').toLongOrNull()
            if (horodatage == null || maintenant - horodatage > VALIDITE_MS) e.remove(cle)
        }
        e.putString("$jeton|$maintenant", "$s|$vente").apply()
        return jeton
    }

    /** Relit et EFFACE la suggestion portée par [jeton]. Null si inconnu ou périmé. */
    fun retirer(context: android.content.Context, jeton: String?): EchangeSuggere? {
        if (jeton.isNullOrBlank()) return null
        val p = prefs(context)
        val cle = p.all.keys.firstOrNull { it.substringBefore('|') == jeton } ?: return null
        val valeur = p.getString(cle, null)
        p.edit().remove(cle).apply()   // un jeton ne sert qu'une fois
        val horodatage = cle.substringAfterLast('|').toLongOrNull() ?: return null
        if (System.currentTimeMillis() - horodatage > VALIDITE_MS) return null
        val parts = valeur?.split('|') ?: return null
        val symbole = parts.getOrNull(0).orEmpty()
        if (symbole.isEmpty()) return null
        return EchangeSuggere(
            symbole,
            if (parts.getOrNull(1) == "true") SensEchange.VENTE else SensEchange.ACHAT
        )
    }

    /*
    ─── CE QUE L'ALERTE DOIT FAIRE, CHOISI À SA CRÉATION ──────────────────

    Écrit tel quel dans la colonne `intention` de price_alerts. Des chaînes
    et non un enum : Room les stocke sans convertisseur, et une valeur
    inconnue — base d'une version future, écriture manuelle — retombe sur la
    déduction au lieu de faire échouer la lecture.

    LA CHAÎNE VIDE A UN SENS, et c'est le plus important ici : une alerte
    créée avant que ce choix existe. On déduit alors de la condition, ce qui
    est exactement le comportement précédent. Aucune alerte en base ne
    devient inerte.
    */
    const val INTENTION_VENTE = "VENTE"
    const val INTENTION_ACHAT = "ACHAT"
    const val INTENTION_RIEN = "RIEN"
}
