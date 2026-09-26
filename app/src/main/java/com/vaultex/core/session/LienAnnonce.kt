package com.vaultex.core.session

/*
═══════════════════════════════════════════════════════════════════════════
UNE NOTIFICATION QUI OUVRE UNE ADRESSE EST UN POUVOIR, PAS UN CONFORT
═══════════════════════════════════════════════════════════════════════════

Toucher une annonce ouvrait l'accueil, toujours, quoi qu'elle raconte. C'est
sûr et c'est inutile : une invitation à rejoindre un groupe ne peut pas être
suivie, et l'adresse doit être retapée à la main.

Rendre l'annonce cliquable change la nature de la chose. Le message vient du
serveur ; il traverse Firebase ; il finit par désigner ce que le téléphone
va ouvrir. Sans contrôle, quiconque obtiendrait la capacité d'émettre sur le
sujet — clé de service divulguée, compte Firebase compromis, erreur de
manipulation — enverrait tous les utilisateurs de VaultEx où il veut. Sur un
portefeuille, « où il veut » signifie une fausse page de récupération qui
demande douze mots.

D'OÙ UNE LISTE BLANCHE, ET ELLE EST COURTE. Deux familles de domaines, et
rien d'autre :

    t.me           le groupe Telegram
    vaultex.*      les pages du projet

Tout le reste est refusé EN SILENCE : la notification s'affiche, elle ouvre
l'application, et c'est le comportement d'avant. Un lien refusé ne doit ni
alerter ni disparaître — il redevient simplement du texte.

POURQUOI CETTE VÉRIFICATION VIT DANS L'APPLICATION ET NON DANS LE SCRIPT.
Le script est notre outil ; l'application reçoit ce qui lui arrive. Filtrer à
l'émission protège des fautes de frappe, pas d'un attaquant — qui n'utilisera
pas notre script. Le seul contrôle qui vaille est celui qui s'exécute sur le
téléphone, juste avant d'agir.

HTTPS SEULEMENT. Une adresse en http se lit en clair sur le réseau et peut
être détournée en chemin ; les schémas exotiques (intent:, file:, content:)
ouvrent des portes qui n'ont rien à faire ici.
*/
object LienAnnonce {

    /**
     * Rend [url] si l'application accepte de l'ouvrir, null sinon.
     *
     * La comparaison porte sur l'hôte tel qu'Android le décode, jamais sur le
     * texte de l'adresse : « https://t.me.exemple.com » CONTIENT « t.me » sans
     * être Telegram, et une comparaison par sous-chaîne le laisserait passer.
     */
    fun valide(url: String?): String? {
        val brut = url?.trim().orEmpty()
        if (brut.isEmpty()) return null
        val uri = runCatching { android.net.Uri.parse(brut) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        val hote = uri.host?.lowercase()?.removePrefix("www.") ?: return null
        val accepte = hote == "t.me" ||
            hote == "telegram.me" ||
            hote == "vaultex.app" ||
            hote.endsWith(".vaultex.app")
        return if (accepte) brut else null
    }
}
