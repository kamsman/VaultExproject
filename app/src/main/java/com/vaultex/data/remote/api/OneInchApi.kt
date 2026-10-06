package com.vaultex.data.remote.api

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 1inch — point de terminaison de DEVIS uniquement.
 *
 * `quote` ne construit aucune transaction et ne reçoit aucune adresse
 * d'utilisateur : il répond « combien rendrait cet échange », rien de plus.
 * Le point de terminaison qui construit la transaction s'appelle `swap`, et
 * il n'est volontairement pas déclaré ici — on ne met pas à portée de main
 * ce que l'étape en cours n'a pas le droit d'appeler.
 */
interface OneInchApi {

    /**
     * Adresse du contrat à autoriser avant d'échanger un jeton.
     *
     * DEMANDÉE, JAMAIS ÉCRITE EN DUR. Ce routeur change de version, et une
     * adresse périmée dans l'APK ferait autoriser un contrat qui ne sert
     * plus — autorisation inutile, gaz perdu, et une allocation qui traîne
     * sur une adresse qu'on ne surveille plus.
     */
    @GET("swap/v6.0/{chainId}/approve/spender")
    suspend fun spender(
        @Path("chainId") chainId: Long,
        @Header("Authorization") authorization: String
    ): OneInchSpenderDto

    @GET("swap/v6.0/{chainId}/quote")
    suspend fun quote(
        @Path("chainId") chainId: Long,
        @Query("src") src: String,
        @Query("dst") dst: String,
        /** En unité entière de la chaîne (wei), jamais en décimal. */
        @Query("amount") amount: String,
        @Header("Authorization") authorization: String
    ): OneInchQuoteDto
}

/**
 * Réponse d'un devis.
 *
 * Les DEUX noms sont lus : la version 6 rend `dstAmount`, la version 5
 * rendait `toTokenAmount`. Un champ renommé donnerait un devis toujours
 * absent, sans aucune erreur — panne indiscernable de « paire non
 * supportée », et qu'on mettrait une semaine à voir.
 */
/** Réponse de `approve/spender`. */
data class OneInchSpenderDto(
    val address: String? = null
)

data class OneInchQuoteDto(
    val dstAmount: String? = null,
    val toTokenAmount: String? = null
)
