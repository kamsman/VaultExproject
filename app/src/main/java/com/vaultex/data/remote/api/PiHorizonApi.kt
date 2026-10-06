package com.vaultex.data.remote.api

import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Pi Network — API Horizon, en LECTURE SEULE.
 *
 * Pi étant un dérivé de Stellar, son API en reprend les chemins et les
 * formats. Le point de terminaison qui DIFFUSE une transaction s'appelle
 * `/transactions` en POST : il n'est volontairement pas déclaré ici, comme
 * le `swap` de 1inch ne l'était pas à l'étape du devis. On ne met pas à
 * portée de main ce que l'étape en cours n'a pas le droit d'appeler.
 */
interface PiHorizonApi {

    /**
     * Compte et ses soldes.
     *
     * RÉPOND 404 QUAND LE COMPTE N'EXISTE PAS, et ce n'est pas une panne :
     * sur un réseau de la famille Stellar, une adresse n'existe qu'à partir
     * du premier versement reçu. Une adresse neuve est donc introuvable, et
     * c'est l'état normal de toute adresse VaultEx tant que personne n'y a
     * envoyé de Pi. L'appelant doit le lire comme « solde zéro », jamais
     * comme une erreur de service.
     */
    @GET("accounts/{adresse}")
    suspend fun compte(@Path("adresse") adresse: String): PiCompteDto
}

data class PiCompteDto(
    val balances: List<PiSoldeDto>? = null
)

data class PiSoldeDto(
    val balance: String? = null,
    /** « native » pour le Pi lui-même ; les autres actifs ont un code. */
    val asset_type: String? = null
)
