package com.vaultex.domain.pi

import javax.inject.Inject

/*
═══════════════════════════════════════════════════════════════════════════
PI — ÉTAPE A2 : L'ADRESSE ET LE SOLDE, EN LECTURE SEULE
═══════════════════════════════════════════════════════════════════════════

Rien n'est signé, rien n'est envoyé. On demande un solde et on le rend.

─── « COMPTE INTROUVABLE » N'EST PAS UNE PANNE ──────────────────────────

Sur un réseau de la famille Stellar, une adresse n'EXISTE qu'à partir du
premier versement reçu. Tant que personne n'a envoyé de Pi à l'adresse
VaultEx, Horizon répond 404 — et c'est l'état normal, pas un défaut.

Les confondre produirait le pire message possible : « service indisponible »
affiché à quelqu'un dont le compte fonctionne parfaitement et n'attend qu'un
premier versement. On distingue donc les deux, et seule la vraie panne
remonte au canal d'administration.
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

class PiCompteService @Inject constructor(
    private val api: com.vaultex.data.remote.api.PiHorizonApi
) {

    suspend fun solde(adresse: String): SoldePi {
        if (!com.vaultex.core.crypto.PiWallet.adresseValide(adresse)) return SoldePi.Inconnu
        return try {
            val natif = api.compte(adresse).balances
                ?.firstOrNull { it.asset_type == "native" }
                ?.balance
                ?.toDoubleOrNull()
            SoldePi.Connu(natif ?: 0.0)
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 404) SoldePi.JamaisCredite
            else {
                com.vaultex.core.monitoring.reportUnlessCancelled("solde Pi", e)
                SoldePi.Inconnu
            }
        } catch (e: Exception) {
            com.vaultex.core.monitoring.reportUnlessCancelled("solde Pi", e)
            SoldePi.Inconnu
        }
    }
}
