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
}
