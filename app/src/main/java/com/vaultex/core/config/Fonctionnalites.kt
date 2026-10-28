package com.vaultex.core.config

/*
═══════════════════════════════════════════════════════════════════════════
CE QU'ON MET EN PAUSE SANS LE DÉMONTER
═══════════════════════════════════════════════════════════════════════════

Une fonctionnalité qu'on retire d'un produit ne se retire presque jamais
pour de bon. Elle revient — plus tard, autrement, quand une dépendance
extérieure a bougé. Entre-temps, il faut qu'elle cesse d'exister POUR
L'UTILISATEUR sans cesser d'exister pour le code.

Les deux façons de s'y prendre ne se valent pas :

  SUPPRIMER le code rend la pause irréversible en pratique. L'historique
  git garde tout, bien sûr — mais ressusciter trente fichiers à travers
  six mois d'autres changements coûte plus cher que de les avoir écrits.

  UN INTERRUPTEUR laisse le code en place, compilé, à jour, et le rend
  inatteignable. Le jour du retour, c'est une ligne.

Ce fichier porte donc les interrupteurs, et rien d'autre : pas de réglage
qui change au fil de l'eau — ceux-là viennent du relais — seulement des
décisions de produit, prises ici et vraies jusqu'à la prochaine version.
═══════════════════════════════════════════════════════════════════════════
*/
object Fonctionnalites {

    /*
    ═══════════════════════════════════════════════════════════════════════
    LE PI EST EN PAUSE
    ═══════════════════════════════════════════════════════════════════════

    Mettre `true` le fait réapparaître partout, en un seul geste : la liste
    des actifs, l'accueil, l'envoi, la réception, la fiche de la monnaie et
    l'entrée dans les réglages lisent tous cette ligne.

    ─── CE QUE LA PAUSE FAIT VRAIMENT ────────────────────────────────────

    Le Pi n'est plus CONSTRUIT par le portefeuille. C'est l'interrupteur le
    plus en amont qu'on puisse poser : un actif qui n'existe pas ne peut
    apparaître nulle part, et il n'y a donc pas un écran à penser.

    ─── CE QUE ÇA COÛTE, ET IL FAUT LE SAVOIR ────────────────────────────

    La règle d'affichage des actifs est « visible si activé OU si le solde
    est positif » — on ne masque jamais des fonds. Cette pause passe outre :
    quelqu'un qui détient du Pi ne le verra plus.

    Rien n'est perdu. L'adresse se dérive de la phrase secrète, le solde est
    sur la chaîne, et remettre `true` le fait réapparaître tel quel. Mais
    c'est bien une décision, pas un effet de bord — et c'est pour ça qu'elle
    est écrite ici plutôt que disséminée dans six écrans.

    ─── CE QUI RESTE ALLUMÉ ──────────────────────────────────────────────

    Les routes `Routes.PI` et `Routes.PI_ENVOI` restent déclarées. Elles
    deviennent simplement inatteignables : plus aucun écran n'y mène. Les
    retirer du graphe n'enlèverait rien à l'utilisateur et ajouterait un
    pas au retour.
    ═══════════════════════════════════════════════════════════════════════
    */
    const val PI_ACTIF = false

    /*
    ═══════════════════════════════════════════════════════════════════════
    L'USDT SUR SOLANA SE CONSTRUIT, ET IL EST ÉTEINT PENDANT CE TEMPS
    ═══════════════════════════════════════════════════════════════════════

    Même interrupteur, raison inverse : le Pi est en pause parce qu'il
    marchait, celui-ci est éteint parce qu'il n'est pas fini.

    LA LEÇON VIENT DU PI, et elle vaut dans les deux sens : une option
    visible qui ne marche pas est pire que pas d'option. Quelqu'un la
    choisit PARCE QU'ELLE EST LÀ, et découvre qu'elle ne mène nulle part —
    au milieu d'une opération d'argent.

    Tant que l'envoi et la vérification du relais ne sont pas écrits ET
    éprouvés sur un vrai petit transfert, le solde se lit mais l'actif ne
    s'affiche pas. Le code se construit par morceaux ; l'écran, lui, ne
    connaît que « ça marche » ou « ça n'existe pas ».

    ─── ALLUMÉ POUR LE PORTEFEUILLE, PAS ENCORE POUR LE CHANGE ───────────

    Le relais est éprouvé : son nœud Solana répond depuis Cloudflare, lit
    l'adresse réglée du changeur et rend une réponse valide. Ce qui ne
    l'est pas, c'est l'envoi — aucun USDT n'est encore parti d'ici.

    Or on ne peut pas l'éprouver sans l'allumer : c'est cet interrupteur
    qui empêche de voir le solde et d'atteindre l'écran d'envoi, donc de
    faire le transfert d'essai.

    DEUX VERROUS, ET LE SECOND TIENT ENCORE. Celui-ci ouvre le
    PORTEFEUILLE — le solde, l'envoi, la réception. L'écran de change, lui,
    lit `CHANGE_MONNAIES` sur le relais, qui ne contient pas `USDT-SOL`.
    Acheter et vendre de l'USDT sur Solana reste donc impossible, et le
    restera jusqu'à ce qu'un vrai transfert ait abouti.

    C'est la bonne façon de s'y prendre : on éprouve d'abord ce qui
    n'engage que soi.
    ═══════════════════════════════════════════════════════════════════════
    */
    const val USDT_SOL_ACTIF = true
}
