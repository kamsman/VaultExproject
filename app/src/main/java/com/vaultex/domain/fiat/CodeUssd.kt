package com.vaultex.domain.fiat

/*
═══════════════════════════════════════════════════════════════════════════
PRÉ-REMPLIR LE CODE USSD, ET RIEN DE PLUS
═══════════════════════════════════════════════════════════════════════════

Pour payer son achat, l'utilisateur tape sur son téléphone quelque chose
comme « *144*2*1*70123456*10000# ». Il recopie donc à la main le numéro du
changeur et le montant, et c'est là que se perdent les paiements : un
chiffre de travers dans le numéro, et l'argent part chez un inconnu,
définitivement.

Ce fichier construit cette chaîne à sa place. Rien d'autre.

─── CE N'EST PAS UNE INTÉGRATION ORANGE ─────────────────────────────────

Aucune API, aucun compte marchand, aucune approbation. On ouvre le
composeur du téléphone avec un numéro déjà écrit, exactement comme un lien
« tel: » sur une page web. VaultEx ne déclenche aucun débit et ne constate
rien : c'est l'utilisateur qui appuie, et c'est Orange qui exécute.

Cette distinction est tout ce qui sépare « afficher » de « encaisser », et
donc tout ce qui tient VaultEx à l'écart de ce que la BCEAO encadre.

═══════════════════════════════════════════════════════════════════════════
LE CODE SECRET N'ENTRE JAMAIS DANS CETTE CHAÎNE
═══════════════════════════════════════════════════════════════════════════

Certaines syntaxes USSD acceptent le code secret en dernier paramètre :
« *144*2*3*{agent}*{montant}*{code}# ». Il serait techniquement possible
de le demander et de le pré-remplir aussi. IL NE FAUT PAS, et ce n'est pas
une question de prudence excessive :

  — Une chaîne USSD s'affiche en clair à l'écran pendant la frappe.
  — Elle reste dans le JOURNAL D'APPELS du téléphone, lisible par
    n'importe qui l'ouvre, et sauvegardée dans le cloud du constructeur.
  — Une application qui demande un code secret Orange Money fait
    exactement le geste que les campagnes anti-arnaque décrivent comme
    l'arnaque. Le jour où quelqu'un imite VaultEx, l'utilisateur donnera
    son code sans hésiter, parce que c'est devenu normal.

[modeleValide] refuse donc tout emplacement qui ressemble à un code
secret. La chaîne s'arrête avant, et l'utilisateur termine sur son clavier
— là où son code n'a jamais quitté son téléphone.
═══════════════════════════════════════════════════════════════════════════
*/
object CodeUssd {

    /**
     * Les deux seuls emplacements autorisés dans un modèle.
     *
     * Toute autre accolade fait refuser le modèle en entier. C'est
     * volontairement rigide : un modèle réglé à distance est du code
     * exécuté sur le téléphone de quelqu'un, et un emplacement qu'on
     * n'avait pas prévu est un comportement qu'on n'a pas examiné.
     */
    private const val NUMERO = "{numero}"
    private const val MONTANT = "{montant}"

    /**
     * Vrai si ce modèle peut être utilisé.
     *
     * ═══════════════════════════════════════════════════════════════════
     * ON ÉCHOUE EN FERMANT, TOUJOURS
     * ═══════════════════════════════════════════════════════════════════
     *
     * Un modèle douteux ne donne pas un bouton douteux : il ne donne PAS
     * de bouton. L'écran garde alors le numéro et le montant à copier, ce
     * qui marche depuis le premier jour.
     *
     * Le contraire — afficher un bouton construit sur un modèle mal réglé
     * — enverrait quelqu'un valider un transfert vers un numéro tronqué.
     * Un bouton absent est une gêne ; un bouton faux est une perte de
     * fonds.
     * ═══════════════════════════════════════════════════════════════════
     */
    fun modeleValide(modele: String?): Boolean {
        val m = modele?.trim().orEmpty()
        if (m.isEmpty()) return false
        // Un code USSD commence par * ou # et se termine par #. Sans ça,
        // ce n'est pas un code USSD mais un numéro à appeler.
        if (!(m.startsWith("*") || m.startsWith("#"))) return false
        if (!m.endsWith("#")) return false
        if (!m.contains(NUMERO) || !m.contains(MONTANT)) return false

        /*
        AUCUN AUTRE EMPLACEMENT QUE LES DEUX CONNUS.

        Ce test attrape « {pin} », « {code} », « {secret} » — mais aussi
        « {montan} », une faute de frappe qui laisserait des accolades
        dans la chaîne composée. On ne liste donc pas les mots interdits,
        on refuse tout ce qui n'est pas les deux attendus : une liste noire
        s'oublie, une liste blanche non.
        */
        val reste = m.replace(NUMERO, "").replace(MONTANT, "")
        if (reste.contains('{') || reste.contains('}')) return false

        // Ce qui reste doit être de la syntaxe USSD : des chiffres, des
        // étoiles, des dièses. Une lettre n'y a rien à faire.
        return reste.all { it.isDigit() || it == '*' || it == '#' }
    }

    /**
     * La chaîne USSD prête à composer, ou null si quelque chose ne va pas.
     *
     * @param montantFcfa en francs. Arrondi à l'entier : le franc CFA n'a
     *   pas de centimes, et une virgule dans un code USSD le fait échouer.
     */
    fun composer(modele: String?, numero: String, montantFcfa: Double): String? {
        if (!modeleValide(modele)) return null

        /*
        LE NUMÉRO EST RÉDUIT À SES CHIFFRES.

        Le réglage du relais peut porter « 70 12 34 56 », parce que c'est
        ainsi qu'on écrit un numéro. Une espace dans un code USSD le fait
        échouer — silencieusement, avec un « connexion impossible » qui ne
        dit pas pourquoi.
        */
        val chiffres = numero.filter { it.isDigit() }
        if (chiffres.length < 8) return null

        val montant = Math.round(montantFcfa)
        if (montant <= 0) return null

        val compose = modele!!.trim()
            .replace(NUMERO, chiffres)
            .replace(MONTANT, montant.toString())

        /*
        ON REVÉRIFIE APRÈS SUBSTITUTION, et ce n'est pas redondant.

        Le modèle était valide ; la chaîne composée peut ne pas l'être, si
        le numéro ou le montant ont introduit autre chose que des chiffres.
        C'est cette chaîne-là qui partira au composeur, donc c'est
        celle-là qu'il faut juger.
        */
        if (!compose.all { it.isDigit() || it == '*' || it == '#' }) return null
        if (!compose.endsWith("#")) return null
        // Un code USSD plus long que ça n'existe pas, et la limite protège
        // d'un modèle qui bouclerait sur lui-même.
        if (compose.length > 60) return null

        return compose
    }

    /**
     * La chaîne telle qu'elle doit entrer dans une URI « tel: ».
     *
     * ═══════════════════════════════════════════════════════════════════
     * LE DIÈSE DOIT ÊTRE ENCODÉ, SINON RIEN NE MARCHE
     * ═══════════════════════════════════════════════════════════════════
     *
     * Dans une URI, « # » ouvre un fragment : tout ce qui le suit n'est
     * pas transmis. « tel:*144*2*1*70123456*10000# » arrive donc au
     * composeur comme « *144*2*1*70123456*10000 » — sans le dièse final,
     * c'est-à-dire sans la touche qui valide le code.
     *
     * Le symptôme est le pire possible : le composeur s'ouvre, la chaîne
     * a l'air juste, et rien ne se passe à l'appel. On cherche du côté de
     * l'opérateur pendant une heure.
     *
     * L'étoile, elle, reste telle quelle : elle est permise dans une URI,
     * et l'encoder ferait échouer le code.
     * ═══════════════════════════════════════════════════════════════════
     */
    fun pourUriTel(compose: String): String = compose.replace("#", "%23")
}
