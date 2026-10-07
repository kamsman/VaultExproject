package com.vaultex.domain.pi

/*
═══════════════════════════════════════════════════════════════════════════
POURQUOI LE RÉSEAU A REFUSÉ — ET LE DIRE EN FRANÇAIS
═══════════════════════════════════════════════════════════════════════════

Horizon refuse avec un 400 dont le corps porte des codes précis :

  { "extras": { "result_codes": {
      "transaction": "tx_insufficient_balance",
      "operations": [ "op_no_destination" ] } } }

Sans cette traduction, l'application afficherait « échec de l'envoi » et
l'utilisateur n'aurait aucun moyen de corriger. Or presque tous ces codes
désignent une action concrète : attendre, baisser le montant, ajouter un
mémo. Les taire transforme un problème réparable en mur.

─── CE QUI EST TRADUIT ET CE QUI NE L'EST PAS ───────────────────────────

Les codes qu'un portefeuille peut réellement rencontrer sont traduits un
par un. Les autres — les offres, les contrats, les actifs — ne peuvent pas
survenir ici : ce code n'émet que des paiements et des créations de compte.
Ils retombent sur le code brut, qui est au moins cherchable, plutôt que sur
un message rassurant et faux.

─── UNE DISTINCTION QUI DÉCIDE DE TOUT ──────────────────────────────────

`estRejetDefinitif` sépare les refus dont on sait qu'ils n'ont RIEN
consommé de ceux qui laissent un doute. Après un refus définitif on peut
reconstruire et renvoyer immédiatement. Après un doute, jamais — il faut
d'abord savoir ce qu'est devenue la première transaction, sinon on débite
deux fois.
═══════════════════════════════════════════════════════════════════════════
*/
object PiCodesResultat {

    /**
     * Codes extraits d'un corps d'erreur Horizon.
     *
     * @param transaction code de niveau transaction, ou null si illisible.
     * @param operations codes par opération, dans l'ordre.
     */
    data class Codes(
        val transaction: String? = null,
        val operations: List<String> = emptyList()
    )

    /** Lit les codes dans un corps d'erreur JSON. Tolérant : jamais d'exception. */
    fun lire(corps: String?): Codes {
        if (corps.isNullOrBlank()) return Codes()
        return try {
            val racine = com.google.gson.JsonParser.parseString(corps).asJsonObject
            val rc = racine.getAsJsonObject("extras")?.getAsJsonObject("result_codes")
                ?: return Codes()
            val tx = rc.get("transaction")?.takeIf { !it.isJsonNull }?.asString
            val ops = rc.getAsJsonArray("operations")
                ?.mapNotNull { it.takeIf { e -> !e.isJsonNull }?.asString }
                ?: emptyList()
            Codes(tx, ops)
        } catch (_: Exception) {
            Codes()
        }
    }

    /**
     * Phrase à montrer à l'utilisateur.
     *
     * L'opération passe AVANT la transaction : quand une opération échoue,
     * le code de transaction vaut `tx_failed`, qui ne dit rien. C'est
     * l'opération qui porte la raison utile.
     */
    fun message(codes: Codes): String {
        codes.operations.firstOrNull { it != "op_success" }
            ?.let { return messageOperation(it) }
        return messageTransaction(codes.transaction)
    }

    private fun messageOperation(code: String): String = when (code) {
        /*
        LE CODE LE PLUS IMPORTANT DE CETTE LISTE.

        Il veut dire que la destination n'a jamais reçu de Pi, donc que son
        compte n'existe pas encore sur la chaîne. PiEnvoiUseCase est censé
        l'avoir détecté AVANT de signer et avoir émis une création de
        compte à la place. Le voir ici signifie que le compte a disparu
        entre la vérification et la diffusion — autrement dit presque
        jamais — ou que la détection a échoué. Dans les deux cas, le
        message doit dire quoi faire.
        */
        "op_no_destination" ->
            "Cette adresse n'a encore jamais reçu de Pi. Il faut lui en envoyer " +
                "au moins le minimum pour créer son compte. Réessaie : VaultEx " +
                "s'en charge automatiquement."

        "op_underfunded" ->
            "Solde insuffisant. Pense à la réserve que le réseau Pi oblige à " +
                "laisser sur le compte : utilise le bouton Max pour connaître le " +
                "montant réellement disponible."

        "op_low_reserve" ->
            "Montant trop faible pour créer le compte de destination. Le réseau " +
                "Pi exige un minimum pour qu'une adresse neuve existe."

        "op_line_full" -> "Le compte de destination ne peut pas recevoir plus."
        "op_no_issuer" -> "Actif inconnu du réseau."
        "op_not_authorized" -> "Le compte de destination n'accepte pas ce versement."
        "op_malformed" -> "Adresse de destination ou montant invalide."
        "op_no_account" -> "Ton compte Pi n'existe pas encore sur la chaîne."
        else -> "Le réseau Pi a refusé l'opération ($code)."
    }

    private fun messageTransaction(code: String?): String = when (code) {
        null -> "Le réseau Pi a refusé la transaction, sans préciser pourquoi."

        /*
        SÉQUENCE DÉPASSÉE. Un autre appareil, ou une autre application
        utilisant la même phrase secrète, a envoyé entre-temps.
        PiEnvoiUseCase relit la séquence et réessaie une fois tout seul ;
        ce message n'apparaît que si le second essai échoue aussi.
        */
        "tx_bad_seq" ->
            "Une autre transaction est partie de ce compte entre-temps. " +
                "Réessaie dans quelques secondes."

        "tx_insufficient_balance" ->
            "Solde insuffisant pour couvrir le montant et les frais, en " +
                "laissant la réserve exigée par le réseau Pi."

        "tx_bad_auth", "tx_bad_auth_extra" ->
            "Signature refusée par le réseau Pi. Ne réessaie pas : signale-le, " +
                "c'est un défaut de l'application et non de ton portefeuille."

        "tx_no_source_account" ->
            "Ton compte Pi n'existe pas encore sur la chaîne : il n'a jamais " +
                "reçu de Pi, il n'y a donc rien à envoyer."

        "tx_too_late" ->
            "La transaction a mis trop de temps à partir et n'est plus valable. " +
                "Aucun Pi n'a bougé — tu peux réessayer."

        "tx_too_early" -> "La transaction a été présentée trop tôt."
        "tx_insufficient_fee" ->
            "Le réseau Pi est chargé et demande des frais plus élevés. Réessaie."

        "tx_failed" ->
            // On ne devrait jamais arriver ici : tx_failed veut dire qu'une
            // opération a échoué, et [message] traite l'opération d'abord.
            "Une opération de la transaction a échoué."

        else -> "Le réseau Pi a refusé la transaction ($code)."
    }

    /**
     * Vrai si ce refus garantit que RIEN n'a été appliqué.
     *
     * ═══════════════════════════════════════════════════════════════════
     * C'EST LA FRONTIÈRE ENTRE « RÉESSAYER » ET « SURTOUT PAS »
     * ═══════════════════════════════════════════════════════════════════
     *
     * Un 400 d'Horizon porte un verdict : la transaction a été examinée et
     * rejetée. Rien n'est entré dans un grand livre, aucun frais n'a été
     * prélevé, et on peut reconstruire tout de suite.
     *
     * Ce qui n'est PAS un refus définitif : une coupure réseau, un délai
     * dépassé, un 502. Là, la transaction a peut-être été reçue et
     * appliquée alors que la réponse s'est perdue. Réessayer dans ce
     * cas-là, c'est accepter de payer deux fois — et c'est exactement
     * l'erreur que PiEnvoiUseCase.reconcilier existe pour empêcher.
     *
     * La fonction rend donc FAUX par défaut : en l'absence de codes
     * lisibles, on considère qu'on ne sait pas. C'est le sens prudent.
     * ═══════════════════════════════════════════════════════════════════
     */
    fun estRejetDefinitif(codes: Codes): Boolean =
        codes.transaction != null || codes.operations.isNotEmpty()

    /** Vrai si relire la séquence et réessayer a une chance d'aboutir. */
    fun vautUnSecondEssai(codes: Codes): Boolean =
        codes.transaction == "tx_bad_seq" || codes.transaction == "tx_insufficient_fee"

    /**
     * Vrai si ce refus a quand même coûté les frais.
     *
     * ═══════════════════════════════════════════════════════════════════
     * UNE DISTINCTION QU'ON OUBLIE PRESQUE TOUJOURS
     * ═══════════════════════════════════════════════════════════════════
     *
     * Horizon répond 400 dans deux situations qui n'ont rien à voir.
     *
     * REFUS AVANT INSCRIPTION — mauvaise séquence, signature invalide,
     * enchère trop basse. La transaction n'entre dans aucun grand livre et
     * ne coûte rien.
     *
     * ÉCHEC APRÈS INSCRIPTION — c'est `tx_failed`. La transaction était
     * valide, elle est ENTRÉE dans un grand livre, ses frais ont été
     * prélevés, et c'est son opération qui a échoué : destination
     * inexistante, solde insuffisant au moment de l'application.
     *
     * Les deux renvoient 400. Dire « aucun Pi n'a bougé » dans le second
     * cas est faux, et l'utilisateur verra son solde baisser après un
     * message qui lui promettait le contraire — exactement ce qui fait
     * perdre confiance en un portefeuille.
     * ═══════════════════════════════════════════════════════════════════
     */
    fun fraisPreleves(codes: Codes): Boolean =
        codes.transaction == "tx_failed" ||
            codes.operations.any { it.startsWith("op_") && it != "op_success" }
}
