package com.vaultex.domain.pi

import com.vaultex.core.crypto.PiXdr
import javax.inject.Inject

/*
═══════════════════════════════════════════════════════════════════════════
PI — CE QU'ON SAIT D'UN COMPTE
═══════════════════════════════════════════════════════════════════════════

Rien n'est signé ici, rien n'est diffusé. On demande l'état d'un compte et
on le rend.

─── « COMPTE INTROUVABLE » N'EST PAS UNE PANNE ──────────────────────────

Sur un réseau de la famille Stellar, une adresse n'EXISTE qu'à partir du
premier versement reçu. Tant que personne n'a envoyé de Pi à l'adresse
VaultEx, Horizon répond 404 — et c'est l'état normal, pas un défaut.

Les confondre produirait le pire message possible : « service indisponible »
affiché à quelqu'un dont le compte fonctionne parfaitement et n'attend qu'un
premier versement. On distingue donc les deux, et seule la vraie panne
remonte au canal d'administration.

Côté ENVOI, ce même 404 porte une autre information, et elle est décisive :
interrogé sur la DESTINATION, il dit qu'il faut CRÉER le compte plutôt que
le payer. Un paiement vers une adresse jamais créditée échoue — c'est la
première cause d'envoi raté sur cette famille de réseaux, et elle ne se
devine pas.
═══════════════════════════════════════════════════════════════════════════
*/

/** Solde Pi d'une adresse. */
sealed class SoldePi {
    /** Compte alimenté : [montant] Pi disponibles. */
    data class Connu(val montant: Double) : SoldePi()

    /** Adresse jamais créditée — normal pour une adresse neuve. */
    data object JamaisCredite : SoldePi()

    /** Horizon injoignable ou en panne : on ne sait pas. */
    data object Inconnu : SoldePi()
}

/**
 * État complet d'un compte, tel qu'il faut le connaître pour envoyer.
 *
 * @param soldeStroops solde natif total, réserve comprise.
 * @param numeroSequence séquence ACTUELLE ; une transaction utilise la suivante.
 * @param entreesAnnexes entrées qui alourdissent la réserve minimale.
 * @param engageEnVenteStroops montant bloqué par des offres de vente.
 */
data class ComptePi(
    val soldeStroops: Long,
    val numeroSequence: Long,
    val entreesAnnexes: Int,
    val parrainages: Int,
    val parraine: Int,
    val engageEnVenteStroops: Long
) {
    /**
     * Réserve que le réseau exige de laisser sur le compte.
     *
     * La formule est celle de Stellar : deux parts de base, plus une par
     * entrée annexe, plus une par parrainage accordé, moins une par
     * parrainage reçu. Un compte VaultEx ordinaire n'a ni entrée annexe ni
     * parrainage — le calcul vaut donc deux parts — mais la même phrase
     * secrète peut aussi être utilisée depuis une autre application, et
     * sous-estimer la réserve donne une transaction refusée dont les frais
     * sont quand même brûlés.
     */
    fun reserveStroops(reserveDeBaseStroops: Long): Long {
        val parts = 2L + entreesAnnexes + parrainages - parraine
        return parts.coerceAtLeast(2L) * reserveDeBaseStroops + engageEnVenteStroops
    }

    /** Ce qu'on peut réellement envoyer, frais déduits. Jamais négatif. */
    fun disponibleStroops(reserveDeBaseStroops: Long, fraisStroops: Long): Long =
        (soldeStroops - reserveStroops(reserveDeBaseStroops) - fraisStroops)
            .coerceAtLeast(0L)
}

/** Réponse à « ce compte existe-t-il ? » — trois états, jamais deux. */
sealed class EtatComptePi {
    data class Existe(val compte: ComptePi) : EtatComptePi()
    data object Inexistant : EtatComptePi()
    data object Indetermine : EtatComptePi()
}

class PiCompteService @Inject constructor(
    private val api: com.vaultex.data.remote.api.PiHorizonApi
) {

    suspend fun solde(adresse: String): SoldePi =
        when (val etat = etat(adresse)) {
            is EtatComptePi.Existe ->
                SoldePi.Connu(PiXdr.piDepuisStroops(etat.compte.soldeStroops).toDouble())
            EtatComptePi.Inexistant -> SoldePi.JamaisCredite
            EtatComptePi.Indetermine -> SoldePi.Inconnu
        }

    /**
     * État d'un compte.
     *
     * [EtatComptePi.Indetermine] et [EtatComptePi.Inexistant] ne doivent
     * JAMAIS être confondus. Le premier veut dire « on ne sait pas » et
     * interdit d'agir ; le second est une information positive, qui mène
     * à créer le compte.
     */
    suspend fun etat(adresse: String): EtatComptePi {
        if (!com.vaultex.core.crypto.PiWallet.adresseValide(adresse))
            return EtatComptePi.Indetermine
        return try {
            lire(api.compte(adresse))
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 404) EtatComptePi.Inexistant
            else {
                com.vaultex.core.monitoring.reportUnlessCancelled("compte Pi", e)
                EtatComptePi.Indetermine
            }
        } catch (e: Exception) {
            com.vaultex.core.monitoring.reportUnlessCancelled("compte Pi", e)
            EtatComptePi.Indetermine
        }
    }

    private fun lire(dto: com.vaultex.data.remote.api.PiCompteDto): EtatComptePi {
        val natif = dto.balances?.firstOrNull { it.asset_type == "native" }
        /*
        UN COMPTE SANS SOLDE NATIF LISIBLE EST INDÉTERMINÉ, PAS VIDE.

        Horizon répond toujours avec un solde natif pour un compte qui
        existe. Son absence signifie donc qu'on n'a pas compris la réponse
        — version d'API différente, champ renommé — et non que le compte
        est à zéro. Rendre zéro ici afficherait un portefeuille vide à
        quelqu'un qui a des fonds.
        */
        val soldeStroops = natif?.balance?.let { stroopsDepuis(it) }
            ?: return EtatComptePi.Indetermine
        val sequence = dto.sequence?.toLongOrNull() ?: return EtatComptePi.Indetermine
        return EtatComptePi.Existe(
            ComptePi(
                soldeStroops = soldeStroops,
                numeroSequence = sequence,
                entreesAnnexes = dto.subentry_count ?: 0,
                parrainages = dto.num_sponsoring ?: 0,
                parraine = dto.num_sponsored ?: 0,
                engageEnVenteStroops = natif.selling_liabilities?.let { stroopsDepuis(it) } ?: 0L
            )
        )
    }

    /**
     * Horizon écrit les montants en Pi, avec sept décimales (« 12.3456789 »).
     *
     * On passe par BigDecimal et non par Double : au-delà de quelques
     * millions de Pi, un Double perd des stroops, et un solde faux de
     * quelques stroops suffit à faire refuser une transaction qui vide le
     * compte.
     */
    private fun stroopsDepuis(montant: String): Long? = try {
        java.math.BigDecimal(montant.trim())
            .movePointRight(PiXdr.DECIMALES)
            .toBigIntegerExact()
            .let { if (it.bitLength() >= 63) null else it.toLong() }
    } catch (_: Exception) { null }
}
