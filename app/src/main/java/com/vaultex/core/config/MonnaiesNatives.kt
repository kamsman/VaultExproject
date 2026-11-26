package com.vaultex.core.config

/**
 * ═══════════════════════════════════════════════════════════════════════
 * LES MONNAIES QUI PAIENT LEURS PROPRES FRAIS
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Une monnaie est NATIVE quand les frais de son réseau se paient dans
 * cette monnaie-là. Envoyer 52 DOGE en coûte donc 52 plus les frais ; un
 * USDT-BNB, lui, s'envoie en payant les frais en BNB, et le total débité
 * en USDT reste le montant tapé.
 *
 * Cette distinction décide d'UN CHIFFRE À L'ÉCRAN, et c'est celui sur
 * lequel on appuie « Continuer ».
 *
 * ─── POURQUOI CE FICHIER EXISTE ────────────────────────────────────────
 *
 * VU SUR L'APPAREIL le 26 novembre 2026, écran « Envoyer DOGE » :
 *
 *     Solde           53,06554788 DOGE
 *     Montant         52
 *     Frais réseau     0,00455816 DOGE
 *     Total débité    52 DOGE          ← les frais n'y étaient pas
 *
 * La cause tenait en une ligne, dans `SendScreen` :
 *
 *     val isNative = state.selectedChain in listOf("BTC","ETH","BNB","SOL","TRX")
 *
 * DOGE n'y était pas. Dogecoin a été ajouté à l'application après que
 * cette liste a été écrite, et une liste écrite à la main à qui il manque
 * une entrée NE SE PLAINT JAMAIS : elle rend `false`, ce qui est une
 * réponse parfaitement valide pour le compilateur.
 *
 * Le même oubli avait déjà coûté l'écran de change entier, côté relais,
 * quand `tether` manquait de la table des cours.
 *
 * ─── CINQ COPIES, ET C'EST LA VRAIE LEÇON ──────────────────────────────
 *
 * Au moment d'écrire ceci, `listOf("BTC","ETH","BNB","SOL","TRX")`
 * apparaissait à CINQ endroits du code. Elles ne veulent pas toutes dire
 * la même chose — chaînes exportées dans la sauvegarde, paires de cours
 * sûres, titres sans suffixe de réseau — et les fusionner serait une
 * autre faute.
 *
 * Mais deux d'entre elles posaient bien la question « cette monnaie
 * paie-t-elle ses propres frais ? ». Celles-là ont désormais un seul
 * endroit où se tromper.
 */
object MonnaiesNatives {

    /**
     * Les symboles dont les frais se paient dans la monnaie elle-même.
     *
     * UN `Set` ET NON UNE `List` : l'appartenance est la seule question
     * qu'on lui pose, et un doublon ajouté par distraction n'aurait aucun
     * sens ici.
     */
    val SYMBOLES: Set<String> = setOf("BTC", "ETH", "BNB", "SOL", "TRX", "DOGE")

    /**
     * Vrai si les frais d'envoi de [symbole] se retranchent du même solde
     * que le montant envoyé.
     *
     * La comparaison ignore la casse : les symboles arrivent tantôt d'un
     * réglage, tantôt d'un instantané de portefeuille, et deux chemins
     * qui ne normalisent pas pareil finissent par diverger un jour.
     */
    fun estNative(symbole: String?): Boolean =
        symbole != null && symbole.trim().uppercase() in SYMBOLES
}
