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

    /**
     * Transaction D'AUTORISATION toute faite, pour un montant DONNÉ.
     *
     * `amount` est obligatoire ici alors que 1inch l'accepte absent — et
     * son absence signifie « illimité ». L'autorisation illimitée est le
     * défaut de l'industrie et c'est une erreur : elle laisse un contrat
     * ponctionner le solde entier, pour toujours, longtemps après l'échange.
     * On passe donc toujours le montant exact.
     */
    @GET("swap/v6.0/{chainId}/approve/transaction")
    suspend fun transactionAutorisation(
        @Path("chainId") chainId: Long,
        @Query("tokenAddress") tokenAddress: String,
        @Query("amount") amount: String,
        @Header("Authorization") authorization: String
    ): OneInchTxDto

    /**
     * Transaction D'ÉCHANGE toute faite.
     *
     * `slippage` borne la perte acceptable entre le devis et l'exécution :
     * c'est lui qui devient le montant minimum de sortie inscrit DANS la
     * transaction. Sans borne, n'importe qui pourrait se servir au passage.
     */
    @GET("swap/v6.0/{chainId}/swap")
    suspend fun echange(
        @Path("chainId") chainId: Long,
        @Query("src") src: String,
        @Query("dst") dst: String,
        @Query("amount") amount: String,
        @Query("from") from: String,
        @Query("origin") origin: String,
        @Query("slippage") slippage: Double,
        @Header("Authorization") authorization: String
    ): OneInchSwapDto

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
/** Transaction prête à signer, telle que 1inch la rend. */
data class OneInchTxDto(
    val to: String? = null,
    val data: String? = null,
    val value: String? = null,
    val gasPrice: String? = null,
    val gas: Long? = null
)

/**
 * Réponse de `swap` : la transaction, et le montant que 1inch s'engage à
 * rendre. On relit `dstAmount` plutôt que de faire confiance au devis
 * affiché : entre les deux appels, le prix a pu bouger.
 */
data class OneInchSwapDto(
    val dstAmount: String? = null,
    val toTokenAmount: String? = null,
    val tx: OneInchTxDto? = null
)

/** Réponse de `approve/spender`. */
data class OneInchSpenderDto(
    val address: String? = null
)

data class OneInchQuoteDto(
    val dstAmount: String? = null,
    val toTokenAmount: String? = null
)
