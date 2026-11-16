package com.vaultex.core.tx

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * LIRE CE QU'UN FOURNISSEUR DOGECOIN RÉPOND, SANS LE CROIRE SUR PAROLE
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Cinquième brique. Elle ne fait aucun appel réseau : elle contient les deux
 * morceaux de raisonnement qui, eux, se prouvent hors ligne — convertir un
 * montant, et parcourir des pages. Le transport viendra à côté.
 *
 * ─── POURQUOI CES DEUX-LÀ ET PAS D'AUTRES ──────────────────────────────────
 *
 * Parce que les deux ont déjà failli coûter cher, et qu'aucun des deux ne
 * lève d'erreur quand il se trompe.
 *
 * ═══ LE MONTANT ARRIVE EN TEXTE, ET EN DOGE ═══════════════════════════════
 *
 * MESURÉ : dogechain et sochain rendent `"value": "0.01000000"`. Pas un
 * entier d'unités — une CHAÎNE, en DOGE, avec huit décimales.
 *
 * Le convertir avec un nombre à virgule flottante marche sur les petits
 * montants et DÉRIVE sur les gros : `92233720.36854775` rend une unité de
 * trop. Une unité est un cent-millionième de DOGE, et c'est assez pour
 * produire une transaction dont les entrées ne couvrent pas les sorties —
 * refusée par le réseau, sans explication lisible.
 *
 * On lit donc les chiffres comme du TEXTE, jamais comme un nombre.
 *
 * ═══ LA PAGE N'ANNONCE PAS QU'ELLE EST UNE PAGE ═══════════════════════════
 *
 * MESURÉ, et c'est la mesure qui a changé toute l'architecture : une adresse
 * détenant 5 907 762 DOGE a rendu DIX sorties de 0,01 — soit 0,10 DOGE.
 * Rien dans la réponse ne le signalait : « status: success », dix lignes.
 * Un portefeuille écrit contre ça aurait affiché 0,10 à quelqu'un qui en
 * détient six millions.
 *
 * ET LE NUMÉRO DE PAGE PEUT ÊTRE IGNORÉ EN SILENCE. Sochain rend exactement
 * les mêmes dix sorties pour `page=1` et `page=2`. Un parcours qui fait
 * confiance au numéro tournerait indéfiniment sur un tel service, en
 * accumulant dix fois la même pièce — et en croyant le solde multiplié.
 *
 * [parcourir] s'arrête donc sur TROIS conditions, pas une : page vide, page
 * plus courte que la précédente, ou pièce déjà vue. La troisième est celle
 * qu'aucune documentation ne suggère et que la mesure impose.
 * ═══════════════════════════════════════════════════════════════════════════
 */
object DogeLecture {

    /** Huit décimales, comme Bitcoin. */
    const val DECIMALES = 8

    /**
     * `"0.01000000"` → `1000000`. Lit les chiffres, ne calcule jamais.
     *
     * Rend `null` plutôt que de lever : une réponse illisible d'un service
     * distant n'est pas un défaut de programmation, c'est un cas à traiter.
     * Lever ici ferait tomber un écran entier sur une seule ligne abîmée.
     */
    fun unitesDepuisTexte(texte: String?): Long? {
        val t = texte?.trim().orEmpty()
        if (t.isEmpty()) return null

        /*
        LE SIGNE EST REFUSÉ, PAS IGNORÉ. Un montant négatif venant d'un
        fournisseur ne veut rien dire ; le prendre pour sa valeur absolue
        fabriquerait des fonds qui n'existent pas.
        */
        if (t.startsWith("-")) return null
        val sansSigne = t.removePrefix("+")

        val entiere = sansSigne.substringBefore('.')
        val decimale = if ('.' in sansSigne) sansSigne.substringAfter('.') else ""

        if (entiere.isEmpty() && decimale.isEmpty()) return null
        if (!entiere.all { it.isDigit() }) return null
        if (!decimale.all { it.isDigit() }) return null

        /*
        PLUS DE HUIT DÉCIMALES N'EST PAS ARRONDI, C'EST REFUSÉ. Un fournisseur
        qui en rendrait neuf dirait quelque chose qu'on ne comprend pas, et
        tronquer silencieusement reviendrait à décider à sa place.
        */
        if (decimale.length > DECIMALES) return null

        val complete = decimale.padEnd(DECIMALES, '0')
        val chiffres = (entiere.ifEmpty { "0" }) + complete

        // `toLongOrNull` rend null au-delà de la capacité, ce qui est le bon
        // comportement : un montant qui ne tient pas dans un Long ne tient pas
        // non plus dans une transaction.
        return chiffres.trimStart('0').ifEmpty { "0" }.toLongOrNull()
    }

    /** `1000000` → `"0.01"`, pour l'affichage. Jamais pour un calcul. */
    fun texteDepuisUnites(unites: Long): String {
        require(unites >= 0) { "montant négatif" }
        val entiere = unites / 100_000_000L
        val decimale = (unites % 100_000_000L).toString().padStart(DECIMALES, '0')
        return "$entiere.${decimale.trimEnd('0').ifEmpty { "0" }}"
    }

    /** Ce qu'une page de résultats contient, une fois lue. */
    data class PieceLue(
        val txidAffiche: String,
        val index: Int,
        val unites: Long,
        val blocDeNaissance: Long?,
    )

    /**
     * Parcourt les pages jusqu'à [plafondUnites] ou jusqu'à épuisement.
     *
     * [page] rend la page numéro n (à partir de 1), ou `null` si l'appel a
     * échoué — distinct d'une liste vide, qui signifie « il n'y en a plus ».
     *
     * [plafondUnites] permet de s'arrêter dès qu'on a de quoi couvrir un
     * envoi : inutile de lire cinquante pages pour en dépenser deux pièces.
     * Zéro signifie « tout lire », ce que fait un calcul de solde.
     */
    fun parcourir(
        plafondUnites: Long = 0L,
        pagesMaximum: Int = 20,
        page: (Int) -> List<PieceLue>?,
    ): List<PieceLue> {
        val vues = LinkedHashMap<String, PieceLue>()
        var cumul = 0L
        var taillePrecedente = Int.MAX_VALUE

        for (n in 1..pagesMaximum) {
            val lot = page(n) ?: break          // échec réseau : on rend ce qu'on a
            if (lot.isEmpty()) break

            /*
            LA GARDE QUI VIENT DE LA MESURE. Une clé déjà vue signifie que le
            service a rendu la même page — soit qu'il ignore le numéro, soit
            qu'il a changé d'avis entre deux appels. Dans les deux cas,
            continuer accumulerait des doublons et gonflerait le solde.
            */
            var nouveaute = false
            for (p in lot) {
                val cle = "${p.txidAffiche}:${p.index}"
                if (vues.containsKey(cle)) continue
                vues[cle] = p
                cumul += p.unites
                nouveaute = true
            }
            if (!nouveaute) break

            if (plafondUnites > 0 && cumul >= plafondUnites) break

            // Une page plus courte que la précédente est la dernière.
            if (lot.size < taillePrecedente && n > 1) break
            taillePrecedente = lot.size
        }
        return vues.values.toList()
    }
}
