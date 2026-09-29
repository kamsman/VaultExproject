package com.vaultex.core.monitoring

/**
 * Enveloppe un appel réseau/service et REND SON ÉCHEC VISIBLE, au lieu de le
 * laisser disparaître dans un `catch (_: Exception) {}`.
 *
 * Pourquoi ce fichier existe : la panne qui a rendu les réceptions ETH/BNB
 * muettes pendant plusieurs jours tenait en une phrase — un appel a échoué,
 * l'erreur a été avalée en silence, et personne (ni l'utilisateur, ni nous)
 * n'a rien vu avant un diagnostic manuel. Le code contient plus d'une centaine
 * de `catch` similaires ; la plupart sont bénins (repli sur un cache local,
 * valeur par défaut sans conséquence), mais certains touchent des appels
 * fund-critical.
 *
 * Ce garde-fou ne les corrige pas tous d'un coup — ce serait risqué sans
 * pouvoir compiler et tester chaque site un par un. Il change la PENTE : la
 * prochaine fois qu'un appel réseau doit être protégé, écrire
 *
 *     guarded("ChangeNOW") { api.createTransaction(...) }
 *
 * est plus court qu'un `try/catch` manuel, et REND l'échec visible par
 * défaut. Le silence redevient un choix explicite (`guarded(..., report =
 * false)`), plutôt que l'option la plus simple à taper.
 */

/** Exécute [block] ; si un appel a échoué (ou n'a rien renvoyé), le signale et retourne null. */
suspend fun <T> guarded(source: String, report: Boolean = true, block: suspend () -> T): T? =
    try {
        block()
    } catch (e: kotlinx.coroutines.CancellationException) {
        /*
         * L'ANNULATION N'EST PAS UNE PANNE — et elle doit etre relancee.
         *
         * Une CancellationException est levee quand la coroutine est annulee :
         * l'ecran est quitte, le worker est arrete par le systeme, le
         * processus se termine. C'est un fonctionnement NORMAL.
         *
         * La capturer sans la relancer casse la concurrence structuree : le
         * parent croit que l'enfant s'est termine normalement, et l'annulation
         * ne se propage plus.
         *
         * La signaler comme panne produit en plus de fausses alertes du type
         * « Service indisponible : Detection de depots — Job was cancelled »,
         * qui inquietent sans rien signaler de reel.
         */
        throw e
    } catch (e: Exception) {
        // Un appareil sans reseau n'est pas un service en panne : voir
        // estHorsLigne(). L'appel echoue quand meme et rend null — seule
        // l'alerte d'administration est tue.
        if (report && !estHorsLigne(e)) signalerUneFois(source, e.message)
        null
    }

/**
 * Signale [e] comme panne de [source] — SAUF si c'est une annulation.
 *
 * Les workers sont arretes en permanence par le systeme : contraintes qui
 * changent, delai depasse, memoire sous pression, ecran quitte pour un
 * ViewModel. Chaque arret levait une CancellationException, aussitot
 * rapportee comme « Service indisponible ». Le canal d'administration se
 * remplissait d'alertes qui ne signalaient rien — et une alerte qui crie au
 * loup finit par masquer les vraies.
 *
 * Le travail concerne repart de lui-meme au cycle suivant : il n'y a rien a
 * signaler, rien a corriger, rien a lire.
 */
fun reportUnlessCancelled(source: String, e: Throwable) {
    if (estAnnulation(e) || estHorsLigne(e)) return
    signalerUneFois(source, e.message)
}

/*
═══════════════════════════════════════════════════════════════════════════
LA MÊME PANNE NE SE SIGNALE PAS TOUTES LES QUINZE MINUTES
═══════════════════════════════════════════════════════════════════════════

Les travaux de fond repassent en boucle : suivi des échanges, alertes de
prix, détection de dépôts. Une cause durable — un quota épuisé, une référence
que le fournisseur ne connaît pas, un forfait insuffisant — produisait donc
une alerte par passage, indéfiniment.

Relevé sur le canal d'administration : la même ligne « statut SimpleSwap —
HTTP 404 » sept fois de suite, entrecoupée de « alertes de prix — HTTP 429 »
identiques. Entre elles, un vrai swap échoué et un portefeuille vide depuis
huit jours — les deux seules lignes qui appelaient une décision, noyées.

RÉPÉTER N'APPREND RIEN. La première alerte dit tout : ce qui est cassé, et
depuis quand. Les suivantes ne font que déplacer vers le haut ce qu'on n'a pas
encore lu.

On garde donc une trace de ce qui vient d'être signalé, et on se tait une
demi-heure sur la même cause. Une panne qui dure ressort ainsi deux fois par
heure au lieu de quatre par heure et par worker — assez pour qu'on la voie,
assez peu pour que le reste reste lisible.

EN MÉMOIRE SEULEMENT, et c'est volontaire. Le processus redémarre, le frein
s'oublie : une panne qui survit à un redémarrage mérite d'être redite. Écrire
sur disque pour économiser une alerte serait payer cher un silence.
*/
private val derniersSignalements = java.util.concurrent.ConcurrentHashMap<String, Long>()
private const val SILENCE_MS = 30L * 60 * 1000

/**
 * Signale une anomalie qui n'est PAS une exception — un état constaté qu'on
 * ne sait pas encore expliquer.
 *
 * Même frein de trente minutes que le reste : un défaut qui se reproduit à
 * chaque geste noierait le canal, et c'est justement un défaut qu'on répète
 * qu'on veut pouvoir lire.
 */
fun signalerEtatAnormal(source: String, message: String) = signalerUneFois(source, message)

private fun signalerUneFois(source: String, message: String?) {
    val cle = source + "|" + (message ?: "")
    val maintenant = System.currentTimeMillis()
    val precedent = derniersSignalements[cle]
    if (precedent != null && maintenant - precedent < SILENCE_MS) return
    derniersSignalements[cle] = maintenant
    // Borne de sûreté : un message qui contiendrait un identifiant variable
    // ferait grossir cette table sans fin. Au-delà, on repart de zéro plutôt
    // que de garder une mémoire qu'on ne relira jamais.
    if (derniersSignalements.size > 200) derniersSignalements.clear()
    AdminBot.serviceFailed(source, message)
}

/**
 * Vrai si [e] traduit un telephone SANS RESEAU, et non un service en panne.
 *
 * Le canal d'administration a recu « Service indisponible : alertes de prix
 * — Unable to resolve host "api.coingecko.com" ». Rien n'etait indisponible :
 * l'appareil n'avait simplement pas de reseau a cet instant. Un utilisateur
 * qui entre dans un batiment, prend l'avion ou epuise son forfait produit
 * cette alerte, et il n'y a rien a corriger de notre cote.
 *
 * C'est exactement le travers deja corrige pour les annulations : une alerte
 * qui se declenche sur une situation normale finit par faire ignorer les
 * vraies. Or les VRAIES pannes reseau — un nœud qui refuse, un quota epuise,
 * un delai depasse — ne passent pas par ce filtre et continuent de remonter.
 *
 * La resolution de nom est le seul cas retenu. Un refus de connexion ou un
 * delai depasse peuvent venir de chez nous autant que de chez l'utilisateur :
 * les taire reviendrait a s'aveugler.
 */
private fun estHorsLigne(e: Throwable): Boolean {
    var courant: Throwable? = e
    var profondeur = 0
    while (courant != null && profondeur < 8) {
        if (courant is java.net.UnknownHostException) return true
        val m = courant.message
        if (m != null &&
            (m.contains("Unable to resolve host", ignoreCase = true) ||
                m.contains("No address associated with hostname", ignoreCase = true))
        ) return true
        if (courant.cause === courant) return false
        courant = courant.cause
        profondeur++
    }
    return false
}

/**
 * Vrai si [e] est une annulation, y compris ENVELOPPEE dans autre chose.
 *
 * La version precedente ne testait que l'exception de surface. Or une
 * annulation qui traverse une couche reseau ou un `async` ressort souvent
 * emballee : le type visible n'est plus une CancellationException, mais sa
 * cause en est une. Le filtre laissait donc passer ce qu'il etait cense
 * arreter, et le canal d'administration recevait des « Job was cancelled »
 * a repetition — des alertes qui ne signalent rien, et qui a force font
 * ignorer les vraies.
 *
 * On remonte donc toute la chaine des causes. La borne de profondeur n'est
 * pas decorative : une chaine de causes peut boucler sur elle-meme, et une
 * remontee naive tournerait alors indefiniment.
 *
 * Le test sur le message est un dernier filet, pour le cas ou une couche
 * tierce aurait recopie le texte sans conserver la cause.
 */
private fun estAnnulation(e: Throwable): Boolean {
    var courant: Throwable? = e
    var profondeur = 0
    while (courant != null && profondeur < 8) {
        if (courant is kotlinx.coroutines.CancellationException) return true
        if (courant.message?.contains("Job was cancelled", ignoreCase = true) == true) return true
        if (courant.cause === courant) return false
        courant = courant.cause
        profondeur++
    }
    return false
}
