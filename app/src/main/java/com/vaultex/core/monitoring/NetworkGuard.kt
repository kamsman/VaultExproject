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
        if (report) AdminBot.serviceFailed(source, e.message)
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
    if (e is kotlinx.coroutines.CancellationException) return
    AdminBot.serviceFailed(source, e.message)
}
