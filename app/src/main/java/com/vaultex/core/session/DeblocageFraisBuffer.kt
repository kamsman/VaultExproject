package com.vaultex.core.session

/*
═══════════════════════════════════════════════════════════════════════════
SE DÉBLOQUER SANS QUITTER L'APPLICATION
═══════════════════════════════════════════════════════════════════════════

Détenir un jeton sans la monnaie qui paie les frais est un cul-de-sac : les
fonds sont là, visibles, et rien ne peut en sortir. L'écran d'envoi le
détectait déjà — « il te faut un peu de TRX » — puis laissait l'utilisateur
se débrouiller. En pratique, il va chercher de quoi payer ailleurs, ou il
renonce.

Ce tampon porte la proposition d'un écran à l'autre : quelle monnaie
échanger, contre laquelle, et pour quel montant. L'écran Swap le consomme
une seule fois et se positionne, sans que rien n'ait à être ressaisi.

POURQUOI UN TAMPON SÉPARÉ DE TokenSelectionBuffer. Celui-là ne transporte
qu'un symbole de départ, et sert à trois écrans. Y ajouter une destination
et un montant l'obligerait à servir deux intentions différentes — « ouvre-toi
sur cette monnaie » et « prépare cet échange précis » — que rien ne
distinguerait à la lecture. Deux objets, deux rôles.

RIEN N'EST DÉCIDÉ ICI. Ce n'est qu'un transport : l'écran Swap affiche la
proposition, montre le minimum et le coût, et l'utilisateur confirme ou non.
*/

/** Échange proposé pour débloquer des frais de réseau. */
data class PropositionDeblocage(
    /** Clé de l'actif à échanger, telle que la connaît le registre des swaps. */
    val de: String,
    /** Monnaie native manquante — celle qui paiera les frais. */
    val vers: String,
    /** Montant proposé, déjà au-dessus du minimum de la paire. */
    val montant: String
)

object DeblocageFraisBuffer {

    @Volatile
    private var enAttente: PropositionDeblocage? = null

    fun set(proposition: PropositionDeblocage?) {
        enAttente = proposition
    }

    /** Récupère et efface la proposition en attente. */
    fun consume(): PropositionDeblocage? {
        val p = enAttente
        enAttente = null
        return p
    }
}
