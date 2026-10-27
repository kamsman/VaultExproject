package com.vaultex.domain.support

/*
═══════════════════════════════════════════════════════════════════════════
LES CANAUX OFFICIELS, ÉCRITS UNE SEULE FOIS
═══════════════════════════════════════════════════════════════════════════

Ils étaient dans HelpScreen, en `private const`, avec un doublon mort :
SUPPORT_EMAIL et SUPPORT_EMAILS portaient la même adresse. Le jour où
l'assistance a eu besoin des mêmes canaux, la question s'est posée pour de
bon — recopier une troisième fois un numéro de support, c'est accepter
qu'un jour deux écrans n'affichent plus le même.

─── POURQUOI DANS L'APK ET PAS SEULEMENT DANS LE RELAIS ─────────────────

Le relais les connaît aussi, et c'est voulu : le modèle doit pouvoir les
donner. Mais l'écran doit pouvoir les afficher QUAND LE RELAIS NE RÉPOND
PAS — et c'est précisément le moment où quelqu'un a besoin de joindre un
humain. Une adresse de support qui dépend du serveur en panne ne sert à
rien.

Ce n'est donc pas une duplication par négligence : les deux copies ont des
conditions de disponibilité différentes, et c'est la raison d'être de
celle-ci.

─── CE QUE CE FICHIER N'EST PAS ─────────────────────────────────────────

Un endroit où mettre un secret. Tout ce qui est ici est PUBLIÉ : un numéro
de support, un lien de communauté, une adresse de courriel. Une clé d'API
dans ce fichier serait une clé publique, puisque l'APK se décompile.
═══════════════════════════════════════════════════════════════════════════
*/
object ContactsVaultex {

    const val EMAIL = "vaultexteams@gmail.com"

    /** Pour wa.me : international, sans « + » ni espace. */
    const val WHATSAPP = "22672306526"

    /** La même, telle qu'on la lit. */
    const val WHATSAPP_AFFICHE = "+226 72 30 65 26"

    const val TELEGRAM = "https://t.me/vaultexCommunity"
    const val TELEGRAM_AFFICHE = "t.me/vaultexCommunity"

    const val FACEBOOK = "https://www.facebook.com/profile.php?id=61591429450182"

    const val TELEPHONE_AFFICHE = "+226 79 38 84 55"
    const val TELEPHONE = "+22679388455"

    /*
    LES LIENS TOUT FAITS.

    `wa.me` plutôt qu'un intent WhatsApp nommé : il ouvre l'application si
    elle est installée, et le navigateur sinon. Viser le paquet de WhatsApp
    directement ferait échouer l'ouverture sur un téléphone qui ne l'a pas
    — c'est-à-dire refuser un canal à quelqu'un qui pouvait encore écrire
    depuis le web.
    */
    const val LIEN_WHATSAPP = "https://wa.me/$WHATSAPP"
    const val LIEN_EMAIL = "mailto:$EMAIL?subject=VaultEx%20Support"
    const val LIEN_TELEPHONE = "tel:$TELEPHONE"

    /*
    ═══════════════════════════════════════════════════════════════════════
    L'ASSISTANCE DU CHANGE — UN NUMÉRO À PART, ET C'EST VOULU
    ═══════════════════════════════════════════════════════════════════════

    Les canaux du dessus sont ceux de l'application : une question sur une
    phrase secrète, un écran qui ne s'ouvre pas, une mise à jour. On y
    répond quand on peut.

    Un échange n'est pas ça. Quelqu'un a envoyé de l'argent réel par Orange
    Money, ou une crypto qui ne revient pas, et il attend. Les minutes qu'il
    passe à chercher qui appeler sont les pires de tout le parcours — et
    c'est à ce moment précis qu'un échangeur se fait une réputation, dans un
    sens ou dans l'autre.

    Un numéro dédié dit deux choses d'un coup : qu'il y a quelqu'un, et que
    c'est quelqu'un pour ÇA. Il ne se perd pas dans une file de questions
    générales, et il n'oblige personne à expliquer d'abord ce qu'est VaultEx
    avant de pouvoir dire « mon paiement n'est pas arrivé ».

    LE MÊME POUR L'APPEL ET POUR WHATSAPP, parce que c'est ainsi qu'on
    travaille ici : on appelle quand c'est urgent, on écrit quand on veut
    joindre une capture d'écran. Deux numéros différents feraient hésiter
    sur lequel marche vraiment.

    ─── IL EST ÉCRIT DANS L'APK, ET IL S'Y LIT HORS LIGNE ───────────────

    Même raison que pour les autres, en plus net : on a besoin de ce
    numéro-là précisément quand le relais ne répond pas. Le faire dépendre
    du serveur reviendrait à le retirer au seul moment où il sert.

    S'il doit changer, il change avec l'APK. Si ça devient gênant, on le
    déplacera dans les paramètres du relais AVEC celui-ci en secours —
    jamais à sa place.
    ═══════════════════════════════════════════════════════════════════════
    */
    const val ASSISTANCE_CHANGE = "+22670470749"

    /** La même, telle qu'on la lit — et telle qu'on la recopie. */
    const val ASSISTANCE_CHANGE_AFFICHE = "+226 70 47 07 49"

    const val LIEN_ASSISTANCE_CHANGE_APPEL = "tel:$ASSISTANCE_CHANGE"

    /**
     * Le lien WhatsApp de l'assistance du change.
     *
     * `wa.me` sans le « + », comme plus haut : il ouvre l'application si
     * elle est là, le navigateur sinon.
     *
     * ET IL PORTE LA RÉFÉRENCE QUAND ON EN A UNE. C'est la première chose
     * qu'on demanderait, et la seule que la personne n'a pas forcément sous
     * les yeux au moment où elle écrit — elle vient de quitter l'écran qui
     * l'affichait. Le message part déjà rédigé ; il ne reste qu'à l'envoyer.
     *
     * [message] arrive déjà traduit : l'encodage est fait ici, parce que
     * c'est une affaire d'URL et non de langue.
     */
    fun lienAssistanceChange(message: String = ""): String {
        val base = "https://wa.me/${ASSISTANCE_CHANGE.removePrefix("+")}"
        if (message.isBlank()) return base
        /*
        LE « + » REDEVIENT « %20 », ET CE N'EST PAS UN DÉTAIL.

        `URLEncoder` encode pour un FORMULAIRE, pas pour une URL : il rend
        l'espace sous la forme « + ». Dans une chaîne de requête, certains
        clients WhatsApp relisent ce « + » comme un espace et d'autres
        l'affichent tel quel — le message arriverait alors truffé de plus.

        « %20 » veut dire « espace » partout, sans exception. C'est la seule
        forme qui donne le même message sur tous les téléphones.
        */
        val encode = java.net.URLEncoder.encode(message, "UTF-8").replace("+", "%20")
        return "$base?text=$encode"
    }
}
