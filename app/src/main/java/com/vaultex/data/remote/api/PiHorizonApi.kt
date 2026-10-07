package com.vaultex.data.remote.api

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Pi Network — API Horizon.
 *
 * Pi étant un dérivé de Stellar, son API en reprend les chemins, les
 * formats et les codes d'erreur.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * CE QUI A CHANGÉ, ET POURQUOI C'EST ACCEPTABLE MAINTENANT
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Cette interface était en LECTURE SEULE, et le disait : le point de
 * diffusion n'y figurait pas, pour ne pas mettre à portée de main ce que
 * l'étape en cours n'avait pas le droit d'appeler.
 *
 * Il y figure désormais, parce que l'étape qui l'appelle existe et qu'elle
 * est vérifiable : PiXdrConformiteTest confronte chaque octet de ce qui
 * sera diffusé aux vecteurs du SDK Stellar officiel, signature comprise.
 * Ce n'était pas vrai avant, et c'est la seule raison pour laquelle
 * [diffuser] était absent.
 * ═══════════════════════════════════════════════════════════════════════
 */
interface PiHorizonApi {

    /**
     * Racine d'Horizon : donne la PHRASE DU RÉSEAU.
     *
     * C'est cette phrase qui, hachée, entre dans tout ce qu'on signe. On la
     * DEMANDE au lieu de l'écrire en dur, pour une raison simple : une
     * constante fausse rendrait toutes les signatures invalides, et on ne
     * le découvrirait qu'au premier envoi réel d'un utilisateur.
     *
     * Le risque inverse est borné. Une phrase erronée — service en panne,
     * réponse altérée — produit une signature que le réseau REFUSE. Ça
     * échoue, ça ne se trompe pas de destinataire : aucun fonds ne bouge.
     */
    @GET(".")
    suspend fun racine(): PiRacineDto

    /**
     * Compte et ses soldes.
     *
     * RÉPOND 404 QUAND LE COMPTE N'EXISTE PAS, et ce n'est pas une panne :
     * sur un réseau de la famille Stellar, une adresse n'existe qu'à partir
     * du premier versement reçu. Une adresse neuve est donc introuvable, et
     * c'est l'état normal de toute adresse VaultEx tant que personne n'y a
     * envoyé de Pi. L'appelant doit le lire comme « solde zéro », jamais
     * comme une erreur de service.
     *
     * CE 404 SERT AUSSI À AUTRE CHOSE, côté envoi : interrogé sur la
     * DESTINATION, il dit qu'il faut créer le compte au lieu de le payer.
     * Voir PiEnvoiUseCase.
     */
    @GET("accounts/{adresse}")
    suspend fun compte(@Path("adresse") adresse: String): PiCompteDto

    /**
     * Dernier grand livre : frais de base et RÉSERVE de base, en stroops.
     *
     * Les deux sont des paramètres de réseau que les validateurs peuvent
     * changer par vote. Les écrire en dur, c'est accepter qu'un jour
     * l'application calcule un solde disponible faux — et un solde
     * disponible surévalué donne une transaction refusée dont les frais
     * sont quand même brûlés.
     */
    @GET("ledgers")
    suspend fun dernierLivre(
        @Query("order") ordre: String = "desc",
        @Query("limit") limite: Int = 1
    ): PiPageLivresDto

    /**
     * Une transaction par son empreinte.
     *
     * ═══════════════════════════════════════════════════════════════════
     * C'EST LE POINT LE PLUS IMPORTANT DE CETTE INTERFACE
     * ═══════════════════════════════════════════════════════════════════
     *
     * Sur un réseau mobile, une diffusion peut partir et la réponse ne
     * jamais revenir. On ne sait alors pas si la transaction a été
     * appliquée — et c'est la situation où une application mal écrite
     * renvoie, et débite deux fois.
     *
     * L'empreinte d'une transaction Stellar se calcule AVANT de la
     * diffuser. On la connaît donc, et on peut demander ici ce qu'il en
     * est advenu au lieu de deviner. 404 veut dire « pas appliquée » ;
     * combiné à la date limite inscrite dans la transaction, cela devient
     * « pas appliquée et ne le sera jamais », qui est la seule réponse
     * permettant de réessayer sans risque.
     * ═══════════════════════════════════════════════════════════════════
     */
    @GET("transactions/{empreinte}")
    suspend fun transaction(@Path("empreinte") empreinte: String): PiTransactionDto

    /**
     * Diffuse une enveloppe signée, en base64.
     *
     * Horizon est SYNCHRONE ici : il répond une fois la transaction
     * incluse dans un grand livre, ou refusée. Un 400 porte les codes de
     * résultat dans `extras.result_codes` — voir PiCodesResultat, qui les
     * traduit. Les lire est indispensable : « transaction refusée » sans
     * raison ne permet à personne de corriger quoi que ce soit.
     */
    @FormUrlEncoded
    @POST("transactions")
    suspend fun diffuser(@Field("tx") enveloppeBase64: String): PiTransactionDto
}

data class PiRacineDto(
    val network_passphrase: String? = null,
    val horizon_version: String? = null,
    val history_latest_ledger: Long? = null
)

data class PiCompteDto(
    val account_id: String? = null,
    /**
     * Numéro de séquence ACTUEL, en texte.
     *
     * En texte parce qu'il dépasse ce qu'un double peut porter sans perte :
     * Gson décode un nombre JSON non typé en Double, et au-delà de 2^53 un
     * numéro de séquence y perdrait ses derniers chiffres. Un numéro de
     * séquence faux donne une transaction refusée.
     */
    val sequence: String? = null,
    val balances: List<PiSoldeDto>? = null,
    /** Entrées annexes du compte : chacune augmente la réserve minimale. */
    val subentry_count: Int? = null,
    val num_sponsoring: Int? = null,
    val num_sponsored: Int? = null
)

data class PiSoldeDto(
    val balance: String? = null,
    /** « native » pour le Pi lui-même ; les autres actifs ont un code. */
    val asset_type: String? = null,
    /**
     * Montant engagé dans des offres de vente, indisponible pour un envoi.
     *
     * Nul pour un portefeuille qui ne place pas d'offres — donc toujours,
     * dans VaultEx. On le lit quand même : ce compte peut aussi être
     * utilisé depuis une autre application.
     */
    val selling_liabilities: String? = null
)

data class PiPageLivresDto(val _embedded: PiLivresEmbarquesDto? = null)
data class PiLivresEmbarquesDto(val records: List<PiLivreDto>? = null)

data class PiLivreDto(
    val sequence: Long? = null,
    val base_fee_in_stroops: Long? = null,
    val base_reserve_in_stroops: Long? = null
)

data class PiTransactionDto(
    val hash: String? = null,
    val successful: Boolean? = null,
    val ledger: Long? = null
)
