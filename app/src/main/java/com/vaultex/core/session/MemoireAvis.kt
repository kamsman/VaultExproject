package com.vaultex.core.session

import android.content.Context

/** Ce qui vient de se passer, pour que la carte le NOMME. */
enum class GenreOperation { ENVOI, RECEPTION, ECHANGE }

/**
 * ═══════════════════════════════════════════════════════════════════════
 * QUAND DEMANDER SON AVIS, ET DE QUOI PARLER
 * ═══════════════════════════════════════════════════════════════════════
 *
 * 223 personnes ont installé VaultEx. Aucune n'a jamais fait une
 * transaction en francs. Entre ces deux chiffres il y a une raison, et
 * personne ne l'a jamais écrite — d'où ce formulaire.
 *
 * ─── IL NE S'OUVRE PAS APRÈS L'INSTALLATION ────────────────────────────
 *
 * C'était l'idée de départ, sur le modèle de « Quoi de neuf ». Elle est
 * mauvaise : quelqu'un qui vient d'installer n'a RIEN à dire. Il n'a rien
 * essayé. Lui demander son avis à ce moment-là produit soit le silence,
 * soit une politesse — et dans les deux cas on a dépensé l'unique
 * occasion de le lui demander.
 *
 * Il s'ouvre après une OPÉRATION ABOUTIE : un envoi parti, un dépôt reçu,
 * un échange terminé.
 *
 * ─── « COMMENT ÇA S'EST PASSÉ ? » — ÇA QUOI ? ──────────────────────────
 *
 * La première version posait la question sans jamais dire de QUOI elle
 * parlait. Vu sur l'appareil : on fait un envoi, on quitte, on revient
 * sur l'accueil plus tard, et une carte demande « comment ça s'est
 * passé ? » devant une liste de soldes. Le référent a disparu.
 *
 * DEUX CORRECTIONS, ET ELLES VONT ENSEMBLE :
 *
 * · ON NOMME L'OPÉRATION. « Comment s'est passé votre envoi ? » se
 *   répond ; « comment ça s'est passé ? » demande d'abord de deviner.
 *
 * · ON LAISSE EXPIRER. Au-delà de [FENETRE_MS], la question ne se pose
 *   plus — on ne demande pas à quelqu'un son avis sur un virement de la
 *   semaine dernière.
 *
 * L'EXPIRATION NE BRÛLE PAS L'OCCASION, et c'est le point délicat : la
 * marque « déjà proposé » n'est posée qu'à l'AFFICHAGE. Une fenêtre qui
 * se ferme sans que la carte ait paru laisse donc la porte ouverte pour
 * la PROCHAINE opération. Le mécanisme se rattrape tout seul au lieu de
 * gâcher l'unique passage sur un mauvais moment.
 *
 * ─── UNE SEULE FOIS, ET JAMAIS RELANCÉ ─────────────────────────────────
 *
 * Celui qui ferme la carte sans lire ne la reverra jamais. La règle
 * inverse — relancer jusqu'à ce qu'il réponde — est celle qui rend les
 * applications détestables. Une personne qui a fermé a répondu : elle a
 * répondu non.
 * ═══════════════════════════════════════════════════════════════════════
 */
object MemoireAvis {

    private const val PREFS = "vaultex_avis"

    /** Instant de la dernière opération aboutie. 0 = aucune. */
    private const val CLE_QUAND = "operation_a"

    /** Son genre, pour que la carte la nomme. */
    private const val CLE_GENRE = "operation_genre"

    /** Vrai dès que la carte s'est affichée une fois, quoi qu'il en soit. */
    private const val CLE_PROPOSE = "deja_propose"

    /*
    VINGT-QUATRE HEURES.

    Assez pour attraper le cas ordinaire — on fait l'opération, on ferme,
    on rouvre le lendemain matin pour regarder son solde — et l'opération
    est alors encore « hier », donc racontable.

    Au-delà, la question devient une devinette. Et comme une fenêtre
    expirée ne consomme rien, se tromper vers le court ne coûte qu'un
    report à l'opération suivante.
    */
    private const val FENETRE_MS = 24L * 60 * 60 * 1000

    private fun prefs(contexte: Context) =
        contexte.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * À appeler quand une opération a VRAIMENT abouti — envoi diffusé,
     * dépôt crédité, échange terminé. Jamais sur une tentative.
     *
     * CHAQUE OPÉRATION ÉCRASE LA PRÉCÉDENTE, ce qui est voulu : c'est la
     * dernière qu'on a en tête, et c'est elle qui relance la fenêtre.
     *
     * L'appel est volontairement muet et sans effet visible, pour pouvoir
     * se glisser dans un chemin de succès sans jamais risquer d'y
     * introduire une erreur.
     */
    fun marquerOperation(contexte: Context, genre: GenreOperation) {
        try {
            prefs(contexte).edit()
                .putLong(CLE_QUAND, System.currentTimeMillis())
                .putString(CLE_GENRE, genre.name)
                .apply()
        } catch (_: Exception) {
            /*
            UN FORMULAIRE D'AVIS NE CASSE JAMAIS CE QU'IL OBSERVE.

            Ces appels vivent dans les chemins de succès d'un envoi et d'un
            dépôt. Une exception qui remonterait d'ici ferait échouer, aux
            yeux de l'application, une opération qui a parfaitement réussi
            sur la blockchain.
            */
        }
    }

    /**
     * Le genre de la dernière opération si elle est encore récente, sinon
     * nul. C'est à la fois la condition d'affichage et le titre à poser.
     */
    fun operationRecente(contexte: Context): GenreOperation? = try {
        val p = prefs(contexte)
        val quand = p.getLong(CLE_QUAND, 0L)
        if (quand <= 0L || System.currentTimeMillis() - quand > FENETRE_MS) null
        else p.getString(CLE_GENRE, null)?.let { nom ->
            // `valueOf` lève sur une valeur inconnue — ce qui arriverait
            // à une préférence écrite par une version future, puis relue
            // après un retour en arrière. On préfère ne rien afficher.
            GenreOperation.entries.firstOrNull { it.name == nom }
        }
    } catch (_: Exception) {
        null
    }

    /** Vrai quand une opération récente attend, et que la carte n'a jamais paru. */
    fun aProposer(contexte: Context): Boolean = try {
        operationRecente(contexte) != null && !prefs(contexte).getBoolean(CLE_PROPOSE, false)
    } catch (_: Exception) {
        false
    }

    /** Posée à l'AFFICHAGE de la carte, pas à la réponse. Voir l'en-tête. */
    fun nePlusProposer(contexte: Context) {
        try {
            prefs(contexte).edit().putBoolean(CLE_PROPOSE, true).apply()
        } catch (_: Exception) { }
    }
}
