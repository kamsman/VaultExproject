package com.vaultex.domain.fiat

/*
═══════════════════════════════════════════════════════════════════════════
LE PAVÉ NUMÉRIQUE, ET POURQUOI SA LOGIQUE N'EST PAS DANS L'ÉCRAN
═══════════════════════════════════════════════════════════════════════════

Un clavier système refuse certaines frappes tout seul : il connaît le type
« décimal », il place la virgule du pays, il efface. En écrivant le nôtre,
on hérite de tout ça — et de chaque cas limite qui va avec.

Ces cas ne se voient pas en regardant l'écran. « Deux virgules », « zéro
suivi d'un chiffre », « une décimale de trop sur un jeton qui n'en a que
six » ne produisent pas un affichage cassé : ils produisent un NOMBRE
DIFFÉRENT de celui que la personne croit avoir tapé. Sur un écran où l'on
envoie de l'argent, c'est la pire catégorie de défaut.

D'où ce fichier : des fonctions pures, sans Compose, qu'on éprouve par des
tests au lieu de les essayer au doigt sur un téléphone.

─── CE QU'IL NE FAIT PAS ─────────────────────────────────────────────────

Il ne met pas en forme. Pas d'espaces de milliers, pas de reformatage
pendant la frappe. Deux raisons, et la seconde a déjà coûté un défaut dans
ce projet :

— reformater déplace le curseur de celui qui tape ;
— `NumberFormat` français sépare les milliers par une ESPACE INSÉCABLE
  ÉTROITE (U+202F) selon la version d'Android, et `"5 000".toDouble()`
  rend alors null, ou pire, `parseFloat` rend 5. Un montant qui se
  réaffiche tel qu'il a été tapé ne peut pas être victime de ça.
═══════════════════════════════════════════════════════════════════════════
*/

/** Une touche du pavé. */
sealed interface ToucheP {
    data class Chiffre(val valeur: Int) : ToucheP
    data object Virgule : ToucheP
    data object Effacer : ToucheP
    data object Vider : ToucheP
}

object PaveNumerique {

    /**
     * Combien de chiffres avant la virgule, au plus.
     *
     * Le plafond du changeur est de l'ordre du million de francs ; douze
     * chiffres laissent une marge énorme et empêchent quand même qu'un
     * appui resté enfoncé produise un nombre que `Double` ne tient plus.
     */
    const val ENTIERS_MAX = 12

    /** Le séparateur décimal affiché, celui du français. */
    const val VIRGULE = ','

    /**
     * Applique une touche à la saisie courante.
     *
     * [decimalesMax] vaut zéro pour les francs CFA, qui n'ont pas de
     * centimes, et le nombre de décimales de la monnaie pour la crypto.
     * À zéro, la touche virgule ne fait rien — et l'écran la grise, pour
     * qu'on ne découvre pas en appuyant qu'elle est sans effet.
     */
    fun appliquer(saisie: String, touche: ToucheP, decimalesMax: Int): String = when (touche) {
        is ToucheP.Vider -> ""
        is ToucheP.Effacer -> saisie.dropLast(1)
        is ToucheP.Virgule -> avecVirgule(saisie, decimalesMax)
        is ToucheP.Chiffre -> avecChiffre(saisie, touche.valeur, decimalesMax)
    }

    /*
    ═══════════════════════════════════════════════════════════════════════
    LA TOUCHE VIRGULE DOIT ÊTRE DÉSACTIVÉE, PAS SEULEMENT SANS EFFET
    ═══════════════════════════════════════════════════════════════════════

    Elle ne fait rien quand la monnaie n'a pas de décimales. Mais « rien »
    ne suffit pas, et la vérification l'a montré :

        5 0 0 0 , 5 0   →   500050

    La virgule est ignorée, puis les deux chiffres suivants s'ajoutent à la
    partie ENTIÈRE. Cinq cent mille francs au lieu de cinq mille.

    Aucune règle de ce fichier ne peut l'empêcher : appuyer sur une touche
    sans effet puis continuer à taper EST une frappe cohérente, et
    « 500050 » en est le résultat correct.

    La protection est donc unique et vit dans l'écran : la touche est GRISÉE
    quand `decimalesMax` vaut zéro. Ne la réactive pas en croyant corriger
    une gêne — PaveNumeriqueTest fige ce comportement et dira pourquoi.
    ═══════════════════════════════════════════════════════════════════════
    */
    private fun avecVirgule(saisie: String, decimalesMax: Int): String {
        if (decimalesMax <= 0) return saisie
        // Une seconde virgule donnerait « 1,2,3 », qui n'est pas un nombre.
        if (saisie.contains(VIRGULE)) return saisie
        /*
        UNE VIRGULE SEULE COMMENCE PAR UN ZÉRO.
        « ,5 » se lit comme un demi par un habitué et comme une faute de
        frappe par tout le monde. « 0,5 » se lit par tout le monde.
        */
        return if (saisie.isEmpty()) "0$VIRGULE" else "$saisie$VIRGULE"
    }

    private fun avecChiffre(saisie: String, chiffre: Int, decimalesMax: Int): String {
        if (chiffre !in 0..9) return saisie
        val coupe = saisie.indexOf(VIRGULE)

        if (coupe >= 0) {
            // On est dans la partie décimale.
            val decimales = saisie.length - coupe - 1
            /*
            PAS PLUS DE DÉCIMALES QUE LA MONNAIE N'EN A.

            On a déjà affiché « 8,19672131 USDT » dans ce projet — deux
            chiffres qui n'existent pas sur un jeton à six décimales, donc
            une somme que personne ne peut recevoir. Refuser la frappe est
            plus honnête que l'accepter et arrondir en silence : la
            personne voit que ça s'arrête, et sait pourquoi.
            */
            if (decimales >= decimalesMax) return saisie
            return saisie + chiffre
        }

        /*
        LE ZÉRO DE TÊTE DISPARAÎT DÈS QU'UN CHIFFRE ARRIVE.

        Le champ montre « 0 » tant qu'il est vide — c'est un repère, pas
        une valeur. Taper 5 doit donner « 5 », pas « 05 », qui ne s'écrit
        nulle part et laisse croire qu'on a raté une touche.
        */
        if (saisie == "0") return chiffre.toString()
        if (saisie.length >= ENTIERS_MAX) return saisie
        return saisie + chiffre
    }

    /**
     * La saisie est-elle un nombre utilisable tel quel ?
     *
     * « 0, » ne l'est pas : c'est un état de frappe intermédiaire, et le
     * laisser passer ferait afficher un montant reçu pour une virgule
     * qu'on vient juste de poser.
     */
    fun valeur(saisie: String): Double? {
        val net = saisie.replace(VIRGULE, '.')
        if (net.isEmpty() || net.endsWith(".")) return null
        return net.toDoubleOrNull()?.takeIf { it > 0.0 }
    }
}
