package com.vaultex.domain.pi

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/*
═══════════════════════════════════════════════════════════════════════════
LES PARAMÈTRES DU RÉSEAU PI — DEMANDÉS, PAS DEVINÉS
═══════════════════════════════════════════════════════════════════════════

Trois nombres décident de la validité d'une transaction : la phrase du
réseau, les frais de base et la réserve de base. Les trois peuvent changer
— les validateurs votent les deux derniers — et aucun des trois n'est
devinable depuis l'application.

On les demande donc à Horizon, et on les garde une heure : ce sont des
paramètres de réseau, pas des cours de marché.

─── CE QUI ARRIVE SI ON SE TROMPE, PARAMÈTRE PAR PARAMÈTRE ──────────────

LA PHRASE DU RÉSEAU entre dans l'empreinte signée. Fausse, la signature ne
vaut rien et le réseau refuse. C'est l'échec le plus bénin des trois :
visible immédiatement, aucun fonds déplacé, aucun frais prélevé — une
transaction refusée à l'authentification n'entre jamais dans un grand
livre.

LES FRAIS DE BASE sont une enchère. Trop basse, la transaction est refusée
quand le réseau est chargé. On enchérit donc au-dessus du minimum : sur
cette famille de réseaux on ne paie que ce qui est nécessaire, pas ce qu'on
a offert, donc une enchère haute ne coûte rien et évite un refus.

LA RÉSERVE DE BASE est le seul des trois où l'erreur coûte. La
sous-estimer fait croire à un solde disponible plus grand qu'il n'est : la
transaction part, le réseau la refuse pour solde insuffisant, et LES FRAIS
SONT BRÛLÉS. C'est pourquoi la valeur de repli ci-dessous est la plus HAUTE
des valeurs historiques de la famille, et non la plus basse : se tromper
par excès limite un peu le montant maximum, se tromper par défaut coûte de
l'argent.
═══════════════════════════════════════════════════════════════════════════
*/

/**
 * @param phraseReseau phrase dont l'empreinte identifie le réseau.
 * @param fraisDeBaseStroops frais minimum par opération.
 * @param reserveDeBaseStroops une part de réserve.
 */
data class ParametresPi(
    val phraseReseau: String,
    val fraisDeBaseStroops: Long,
    val reserveDeBaseStroops: Long
)

@Singleton
class PiReseau @Inject constructor(
    private val api: com.vaultex.data.remote.api.PiHorizonApi
) {

    private var cache: ParametresPi? = null
    private var cacheHorodatage = 0L
    private val verrou = Mutex()

    /**
     * Paramètres du réseau, ou null si Horizon n'a pas répondu.
     *
     * NULL PLUTÔT QU'UN REPLI COMPLET, et c'est volontaire : on peut se
     * replier sur des frais et une réserve par défaut, jamais sur une
     * phrase de réseau. Signer avec une phrase inventée produit une
     * signature que le réseau refuse, et surtout : si l'on ne peut pas
     * joindre Horizon, on ne peut pas non plus lire le numéro de séquence
     * ni diffuser. Autant s'arrêter ici, avec un message clair.
     */
    suspend fun parametres(): ParametresPi? = verrou.withLock {
        val maintenant = System.currentTimeMillis()
        cache?.let { if (maintenant - cacheHorodatage < DUREE_CACHE) return@withLock it }

        val phrase = try {
            api.racine().network_passphrase?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            com.vaultex.core.monitoring.reportUnlessCancelled("racine Horizon Pi", e)
            null
        } ?: return@withLock null

        /*
        ON SIGNALE UNE PHRASE INATTENDUE, SANS BLOQUER.

        PHRASE_ATTENDUE est ce que le réseau principal du Pi annonçait quand
        ce code a été écrit. Une divergence veut dire l'une de deux choses :
        soit Pi l'a changée, soit on ne parle pas au bon Horizon.

        On ne bloque pas pour autant, parce qu'une phrase fausse échoue
        d'elle-même et sans frais — le réseau refuse la signature. Bloquer
        rendrait l'envoi impossible le jour où Pi change sa phrase, alors
        que suivre Horizon continuerait de marcher. Mais on veut le SAVOIR :
        c'est le genre de changement qu'on doit apprendre par le canal
        d'administration, pas par un utilisateur qui n'arrive plus à
        envoyer.
        */
        if (phrase != PHRASE_ATTENDUE) {
            com.vaultex.core.monitoring.AdminBot.serviceFailed(
                "Pi",
                "phrase de reseau inattendue : \"$phrase\" (attendue \"$PHRASE_ATTENDUE\")"
            )
        }

        val livre = try {
            api.dernierLivre()._embedded?.records?.firstOrNull()
        } catch (e: Exception) {
            com.vaultex.core.monitoring.reportUnlessCancelled("grand livre Pi", e)
            null
        }

        val parametres = ParametresPi(
            phraseReseau = phrase,
            // Les frais tolèrent un repli : une enchère trop basse est
            // refusée sans frais, elle ne coûte qu'un aller-retour.
            fraisDeBaseStroops = livre?.base_fee_in_stroops
                ?.takeIf { it in 1..FRAIS_PLAFOND }
                ?: FRAIS_DE_BASE_DEFAUT,
            // La réserve se replie vers le HAUT. Voir l'en-tête.
            reserveDeBaseStroops = livre?.base_reserve_in_stroops
                ?.takeIf { it in 1..RESERVE_PLAFOND }
                ?: RESERVE_DE_BASE_DEFAUT
        )
        cache = parametres
        cacheHorodatage = maintenant
        parametres
    }

    private companion object {
        /** Une heure : ce sont des paramètres de réseau, pas des cours. */
        const val DUREE_CACHE = 60 * 60 * 1000L

        /** Ce que le réseau principal du Pi annonçait à l'écriture de ce code. */
        const val PHRASE_ATTENDUE = "Pi Network"

        /*
        ═══════════════════════════════════════════════════════════════════
        CE REPLI ÉTAIT MILLE FOIS TROP BAS
        ═══════════════════════════════════════════════════════════════════

        Il valait 100 stroops, la valeur de Stellar, parce que Pi en est un
        fork et que c'était l'hypothèse raisonnable. Elle est fausse :
        relevé sur api.mainnet.minepi.com le 7 octobre 2026,
        `base_fee_in_stroops` vaut 100 000 — soit 0,01 Pi par opération,
        mille fois Stellar.

        Tant qu'Horizon répond, la valeur lue écrase ce repli et rien ne se
        voit. Mais le jour où /ledgers ne répond pas, l'application
        enchérissait 200 stroops sur un minimum de 100 000, et TOUS les
        envois étaient refusés pour frais insuffisants — une panne totale
        déclenchée par un simple hoquet sur un appel accessoire.

        C'est le genre d'erreur qu'aucun test ne trouve : le chemin fautif
        ne s'emprunte que quand un autre appel échoue.
        ═══════════════════════════════════════════════════════════════════
        */
        const val FRAIS_DE_BASE_DEFAUT = 100_000L

        /*
        LA RÉSERVE SE REPLIE VERS LE HAUT, ET RESTE AU-DESSUS DU RELEVÉ.

        Mesurée à 4 900 000 stroops (0,49 Pi) sur le réseau principal. On
        garde 10 000 000 en repli, soit le double : voir l'en-tête de ce
        fichier. Se tromper par excès limite un peu le montant maximum
        envoyable ; se tromper par défaut produit une transaction refusée
        dont les frais sont brûlés.
        */
        const val RESERVE_DE_BASE_DEFAUT = 10_000_000L

        /*
        PLAFONDS DE VRAISEMBLANCE. Ces nombres viennent du réseau. Une
        réponse aberrante — service altéré, champ mal interprété — ne doit
        pas pouvoir annoncer une réserve de mille Pi et geler tous les
        soldes, ni des frais qui videraient un compte. Au-delà, on préfère
        le repli, qui est connu.
        */
        const val FRAIS_PLAFOND = 10_000_000L        // 1 Pi de frais par opération
        const val RESERVE_PLAFOND = 1_000_000_000L   // 100 Pi de réserve
    }
}
