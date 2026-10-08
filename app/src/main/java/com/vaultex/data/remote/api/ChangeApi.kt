package com.vaultex.data.remote.api

import com.vaultex.data.remote.dto.OrdreChangeBody
import com.vaultex.data.remote.dto.ParametresChangeDto
import com.vaultex.data.remote.dto.ReponseOrdreDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

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
}
