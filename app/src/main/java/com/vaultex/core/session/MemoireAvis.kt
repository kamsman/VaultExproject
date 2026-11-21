package com.vaultex.core.session

import android.content.Context

/**
 * ═══════════════════════════════════════════════════════════════════════
 * QUAND DEMANDER SON AVIS, ET POURQUOI PAS À L'INSTALLATION
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
 * un échange terminé. Là, il y a une expérience derrière la réponse.
 *
 * ─── UNE SEULE FOIS, ET JAMAIS RELANCÉ ─────────────────────────────────
 *
 * La marque est posée dès que la carte s'AFFICHE, et non quand on répond.
 * Le choix est volontaire et il coûte quelque chose : celui qui la ferme
 * sans lire ne la reverra jamais.
 *
 * L'autre règle — « relancer jusqu'à ce qu'il réponde » — est celle qui
 * rend les applications détestables. Une personne qui a fermé la carte a
 * répondu : elle a répondu non.
 *
 * ─── CE QUI SE PERD, ET QUI EST ASSUMÉ ─────────────────────────────────
 *
 * Si l'envoi échoue et que l'application est fermée dans la foulée,
 * l'occasion est perdue : la marque est déjà posée. On pourrait la
 * reprendre en cas d'échec réseau ; je ne le fais pas, parce qu'un avis
 * perdu n'est pas un paiement perdu, et qu'une marque qu'on repose
 * conditionnellement est une marque dont on ne sait plus ce qu'elle dit.
 * ═══════════════════════════════════════════════════════════════════════
 */
object MemoireAvis {

    private const val PREFS = "vaultex_avis"

    /** Vrai dès qu'une opération a abouti : c'est la porte d'entrée. */
    private const val CLE_OPERATION = "operation_aboutie"

    /** Vrai dès que la carte s'est affichée une fois, quoi qu'il en soit. */
    private const val CLE_PROPOSE = "deja_propose"

    private fun prefs(contexte: Context) =
        contexte.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * À appeler quand une opération a VRAIMENT abouti — envoi diffusé,
     * dépôt crédité, échange terminé. Jamais sur une tentative.
     *
     * Appelable autant de fois qu'on veut : seule la première compte, et
     * elle ne fait qu'ouvrir la porte. L'appel est volontairement muet et
     * sans effet visible, pour pouvoir se glisser dans un chemin de
     * succès sans jamais risquer d'y introduire une erreur.
     */
    fun marquerOperation(contexte: Context) {
        try {
            val p = prefs(contexte)
            if (p.getBoolean(CLE_OPERATION, false)) return
            p.edit().putBoolean(CLE_OPERATION, true).apply()
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

    /** Vrai quand une opération a abouti et que la carte n'a jamais été montrée. */
    fun aProposer(contexte: Context): Boolean = try {
        val p = prefs(contexte)
        p.getBoolean(CLE_OPERATION, false) && !p.getBoolean(CLE_PROPOSE, false)
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
