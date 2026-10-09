package com.vaultex.data.remote.api

import com.vaultex.data.remote.dto.OrdreChangeBody
import com.vaultex.data.remote.dto.ParametresChangeDto
import com.vaultex.data.remote.dto.ReponseOrdreDto
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
        @Query("txid") txid: String = ""
    ): VerificationDto
}
