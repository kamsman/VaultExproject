package com.vaultex.data.remote.api

import com.vaultex.data.remote.dto.OrdreChangeBody
import com.vaultex.data.remote.dto.ParametresChangeDto
import com.vaultex.data.remote.dto.ReponseOrdreDto
import com.vaultex.data.remote.dto.ConsigneDto
import com.vaultex.data.remote.dto.VerificationDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Change FCFA — réglages et transmission des demandes.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * POURQUOI CETTE INTERFACE NE PARLE PAS À TELEGRAM
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Elle parle au RELAIS, qui parle à Telegram. Le détour n'est pas de la
 * prudence décorative : le jeton du bot est compilé dans l'APK, et
 * quiconque décompile l'application le récupère.
 *
 * Tant qu'il ne sert qu'à des alertes d'administration, c'est une
 * nuisance. Pour des ordres sur lesquels un changeur agit, ce serait un
 * vol : on forge un résumé crédible, le changeur envoie la crypto, et
 * personne n'a jamais payé. Le jeton reste donc dans les secrets du
 * Worker, et le téléphone ne l'a jamais.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * ET SI LE RELAIS N'EST PAS CONFIGURÉ
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Les appels échouent, [ChangeService] rend null, et l'écran ne propose
 * rien. C'est le comportement voulu : sans réglages, on ne peut pas
 * calculer un prix honnête, et un écran d'achat sans prix n'a aucune
 * raison d'exister.
 * ═══════════════════════════════════════════════════════════════════════
 */
interface ChangeApi {

    @GET("change/parametres")
    suspend fun parametres(): ParametresChangeDto

    @POST("change/ordre")
    suspend fun ordre(@Body corps: OrdreChangeBody): ReponseOrdreDto

    /**
     * Le relais a-t-il vu ce versement sur la chaine ?
     *
     * ═══════════════════════════════════════════════════════════════════
     * ON NE DIT PAS AU RELAIS QUELLE ADRESSE SURVEILLER
     * ═══════════════════════════════════════════════════════════════════
     *
     * Elle n'est pas dans les parametres de cet appel, et son absence est
     * le point le plus important de cette interface. Si l'application
     * pouvait nommer l'adresse a verifier, n'importe qui donnerait la
     * sienne, s'enverrait huit USDT a lui-meme, et tout serait « verifie ».
     *
     * L'adresse vient du reglage CHANGE_ADRESSES du Worker. On ne dit ici
     * que QUOI chercher — une monnaie, un montant — jamais OU.
     *
     * ═══════════════════════════════════════════════════════════════════
     * ET ON NE S'EN SERT PAS COMME D'UNE PREUVE
     * ═══════════════════════════════════════════════════════════════════
     *
     * Cette reponse sert a RASSURER l'utilisateur pendant qu'il attend :
     * « le transfert est arrive ». Elle ne decide de rien. Ce qui compte
     * pour le changeur, c'est la verification refaite par le relais au
     * moment du depot de l'ordre — celle-la, le telephone ne la touche
     * pas.
     *
     * C'est le meme code des deux cotes : si ca repond vert ici, ca
     * repondra vert la.
     * ═══════════════════════════════════════════════════════════════════
     */
    @GET("change/verifier")
    suspend fun verifier(
        @Query("monnaie") monnaie: String,
        @Query("montant") montant: String,
        @Query("txid") txid: String = "",
        /**
         * LA REFERENCE, et non plus seulement le montant.
         *
         * Quand le relais marque les montants, c'est LUI qui calcule
         * celui qu'il faut chercher, a partir de cette reference. Le
         * montant envoye ici ne sert plus que de socle.
         *
         * Lui laisser choisir le montant cherche reviendrait a annuler le
         * marquage : il suffirait d'annoncer celui qu'on convoite.
         */
        @Query("reference") reference: String = ""
    ): VerificationDto

    /**
     * Le montant exact a envoyer, avant que l'utilisateur n'envoie.
     *
     * APPELE AU MOMENT DE LA CONFIRMATION, et c'est tout l'interet : le
     * relais en profite pour noter l'instant ou la demande est nee. Seuls
     * les versements POSTERIEURS pourront ensuite la satisfaire — ce qui
     * empeche de deposer une demande apres avoir vu passer le versement
     * de quelqu'un d'autre.
     */
    /**
     * Pose une question a l'assistance.
     *
     * LA CLE DU MODELE N'EST PAS ICI, et c'est tout l'interet de passer par
     * le relais : un APK se decompile en dix minutes, et une cle qui y
     * figure est une cle publique dont la facture est a celui qui a signe.
     */
    @POST("assistance")
    suspend fun assistance(
        @Body corps: com.vaultex.data.remote.dto.DemandeAssistance
    ): com.vaultex.data.remote.dto.ReponseAssistance

    @GET("change/consigne")
    suspend fun consigne(
        @Query("monnaie") monnaie: String,
        @Query("montant") montant: String,
        @Query("reference") reference: String
    ): ConsigneDto
}
