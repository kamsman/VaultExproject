package com.vaultex.domain.fiat

/*
═══════════════════════════════════════════════════════════════════════════
LA SAISIE D'UN MONTANT, ET POURQUOI ELLE N'EST PAS DANS L'ÉCRAN
═══════════════════════════════════════════════════════════════════════════

Ce fichier portait un pavé numérique construit dans l'application, qui
n'autorisait que les frappes valides. Le pavé est parti et le clavier du
système est revenu — mais les règles, elles, restent nécessaires.

ELLES NE SE VOIENT PAS EN REGARDANT L'ÉCRAN. « Deux virgules », « zéro
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
  rend alors null. Un montant qui se réaffiche tel qu'il a été tapé ne
  peut pas être victime de ça.
═══════════════════════════════════════════════════════════════════════════
*/

object SaisieMontant {

    /**
     * Combien de chiffres avant la virgule, au plus.
     *
     * Le plafond du changeur est de l'ordre du million de francs ; douze
     * chiffres laissent une marge énorme et empêchent quand même qu'une
     * touche restée enfoncée produise un nombre que `Double` ne tient plus.
     */
    const val ENTIERS_MAX = 12

    /** Le séparateur décimal affiché, celui du français. */
    const val VIRGULE = ','

    /*
    ═══════════════════════════════════════════════════════════════════════
    LE FILTRE, QUI A REMPLACÉ LE PAVÉ NUMÉRIQUE
    ═══════════════════════════════════════════════════════════════════════

    Il y avait un pavé construit dans l'application, qui n'autorisait que
    les frappes valides. Il est parti ; le clavier du système est revenu.

    MAIS LE PAVÉ PORTAIT UNE GARANTIE QUE LE CLAVIER N'A PAS : on ne peut
    pas taper plus de décimales que le jeton n'en possède. Sans elle,
    l'écran réaffiche « 8,19672131 USDT » — deux chiffres qui n'existent
    pas sur un jeton à six décimales, donc une somme que personne ne peut
    envoyer. Ce défaut a déjà existé dans ce projet.

    Le filtre la rétablit sans rien imposer au clavier : ce qui est tapé
    passe par ici avant d'être gardé.

    ─── IL CORRIGE, IL NE REFUSE PAS ───────────────────────────────────

    Un champ qui refuse une frappe donne l'impression d'un clavier cassé.
    Celui-ci garde ce qui est utilisable et écarte le reste, de sorte que
    taper une lettre ne fasse simplement rien de visible.
    ═══════════════════════════════════════════════════════════════════════
    */

    /**
     * Ce qu'on garde de ce que l'utilisateur vient de taper.
     *
     * [decimalesMax] vaut zéro pour les francs CFA, qui n'ont pas de
     * centimes, et le nombre de décimales de la monnaie pour la crypto.
     */
    fun filtrer(saisie: String, decimalesMax: Int): String {
        /*
        LE POINT DEVIENT UNE VIRGULE. Un clavier Android peut rendre l'un
        ou l'autre selon la disposition et la langue, et deux écritures
        pour le même nombre finissent par diverger quelque part.
        */
        val brut = saisie.replace('.', VIRGULE)

        val sortie = StringBuilder()
        var virguleVue = false
        var decimales = 0

        for (c in brut) {
            when {
                c.isDigit() -> {
                    if (virguleVue) {
                        // Pas plus de décimales que la monnaie n'en a.
                        if (decimales >= decimalesMax) continue
                        decimales += 1
                    } else {
                        // « 05 » ne s'écrit nulle part.
                        if (sortie.toString() == "0") sortie.setLength(0)
                        if (sortie.length >= ENTIERS_MAX) continue
                    }
                    sortie.append(c)
                }
                c == VIRGULE -> {
                    /*
                    ═══════════════════════════════════════════════════════
                    LA VIRGULE EST TOUJOURS GARDÉE, MÊME SANS DÉCIMALES
                    ═══════════════════════════════════════════════════════

                    L'ÉCARTER COÛTE CENT FOIS LE MONTANT. Mesuré : sur
                    « 5000,50 » en francs, une virgule simplement ignorée
                    laisse les deux chiffres suivants rejoindre la partie
                    entière — 500050. Cinq cent mille francs au lieu de
                    cinq mille, en silence.

                    Et ça ne se répare pas en « sautant la suite » : ce
                    filtre voit la chaîne entière à chaque frappe, pas les
                    touches. Une virgule écartée disparaît du texte gardé,
                    et le chiffre d'après arrive dans une chaîne où plus
                    rien ne dit qu'elle a existé.

                    On la garde donc, et ce sont les DÉCIMALES qui sont
                    bornées — à zéro pour les francs. « 5000,50 » devient
                    « 5000, », que `valeur` refuse de lire : le montant
                    reçu affiche « — », et la personne voit tout de suite
                    qu'il faut retirer la virgule.

                    Visible et faux valent toujours mieux qu'invisible et
                    faux. Et l'écran propose un clavier SANS virgule quand
                    la monnaie n'a pas de décimales, de sorte que ce cas ne
                    se présente qu'après un collage.
                    ═══════════════════════════════════════════════════════
                    */
                    if (virguleVue) continue
                    // Une virgule seule commence par un zéro : « ,5 » se
                    // lit comme une faute de frappe, « 0,5 » par tout le
                    // monde.
                    if (sortie.isEmpty()) sortie.append('0')
                    virguleVue = true
                    sortie.append(c)
                }
                // Tout le reste — lettres, espaces, signes — est écarté.
                else -> continue
            }
        }
        return sortie.toString()
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
