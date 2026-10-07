package com.vaultex.domain.swap

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.vaultex.data.remote.api.SimpleSwapApi
import com.vaultex.data.remote.dto.SimpleSwapCreateBody
import com.vaultex.data.remote.dto.SimpleSwapExchangeDto
import com.vaultex.data.remote.dto.SimpleSwapRangeDto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ═══════════════════════════════════════════════════════════════════════
 * LE MODE DE TAUX DOIT ÊTRE LE MÊME POUR LES TROIS APPELS
 * ═══════════════════════════════════════════════════════════════════════
 *
 * `fixed` apparaît dans get_estimated, get_ranges et create_exchange, et
 * RIEN dans l'API ne vérifie qu'ils s'accordent. Chaque appel pris
 * isolément reste valide quelle que soit sa valeur.
 *
 * Deviser en fixe et créer en flottant afficherait donc un montant et en
 * livrerait un autre, sans la moindre erreur nulle part. Et les bornes
 * diffèrent entre les deux modes : un minimum lu en flottant, affiché,
 * puis un échange créé en fixe qui le refuse, c'est un utilisateur qui
 * saisit le montant qu'on lui a dit et qu'on rejette.
 *
 * Ce test n'a donc rien à voir avec le réseau. Il vérifie une seule chose,
 * et c'est la seule qui puisse diverger silencieusement : que les trois
 * appels portent la MÊME valeur.
 * ═══════════════════════════════════════════════════════════════════════
 */
class FournisseurSimpleSwapTest {

    /** Retient ce que chaque appel a reçu, sans jamais toucher au réseau. */
    private class ApiEspion : SimpleSwapApi {
        val fixedVus = mutableListOf<Pair<String, Boolean>>()

        override suspend fun getEstimated(
            apiKey: String, from: String, to: String, amount: String, fixed: Boolean
        ): JsonElement {
            fixedVus += "get_estimated" to fixed
            return JsonParser.parseString("\"1.23\"")
        }

        override suspend fun getRanges(
            apiKey: String, from: String, to: String, fixed: Boolean
        ): SimpleSwapRangeDto {
            fixedVus += "get_ranges" to fixed
            return SimpleSwapRangeDto(min = "0.5")
        }

        override suspend fun createExchange(
            apiKey: String, body: SimpleSwapCreateBody
        ): SimpleSwapExchangeDto {
            fixedVus += "create_exchange" to body.fixed
            return SimpleSwapExchangeDto(
                id = "essai", addressFrom = "0xdepot", status = "waiting"
            )
        }

        override suspend fun getExchange(apiKey: String, id: String) =
            SimpleSwapExchangeDto(id = id, status = "waiting")

        override suspend fun getAllCurrencies(apiKey: String): JsonElement =
            JsonParser.parseString("[]")
    }

    @Test fun `les trois appels portent le meme mode de taux`() = runBlocking {
        val espion = ApiEspion()
        val fournisseur = FournisseurSimpleSwap(espion)

        fournisseur.devis("BTC", "USDT", 1.0)
        fournisseur.minimum("BTC", "USDT")
        fournisseur.creerEchange("BTC", "USDT", 1.0, "adresse", null)

        assertEquals(
            "les trois appels doivent avoir été observés",
            listOf("get_estimated", "get_ranges", "create_exchange"),
            espion.fixedVus.map { it.first }
        )
        val modes = espion.fixedVus.map { it.second }.distinct()
        assertEquals(
            "les trois appels ne s'accordent pas sur `fixed` : $modes",
            1, modes.size
        )
    }

    /**
     * Et ce mode est celui que la configuration demande.
     *
     * Le test précédent passerait si les trois valaient `false` alors que
     * le réglage dit `true` : ils seraient cohérents entre eux, et tous
     * faux. Celui-ci ferme cette porte.
     */
    @Test fun `le mode suit le reglage de compilation`() = runBlocking {
        val espion = ApiEspion()
        FournisseurSimpleSwap(espion).devis("BTC", "USDT", 1.0)

        assertEquals(
            com.vaultex.core.config.ApiKeys.SIMPLESWAP_TAUX_FIXE,
            espion.fixedVus.single().second
        )
    }

    /**
     * LE TAUX FIXE EST LE DÉFAUT, et c'est une décision de produit.
     *
     * Sur un réseau lent, face à des gens qui découvrent la crypto, « tu
     * reçois exactement ce qui est écrit » vaut plus que quelques dixièmes
     * de pour cent. Un montant qui change après coup ne se lit pas « le
     * marché a bougé » : il se lit « on m'a pris quelque chose ».
     *
     * Si ce test tombe, c'est que simpleswap.taux.fixe a été mis à false
     * quelque part — ce qui peut être voulu, mais doit être su.
     */
    @Test fun `le taux fixe est actif par defaut`() {
        assertTrue(
            "simpleswap.taux.fixe est à false dans local.properties",
            com.vaultex.core.config.ApiKeys.SIMPLESWAP_TAUX_FIXE
        )
    }

    /**
     * SIMPLESWAP EST BIEN L'ÉCHANGEUR EN SERVICE.
     *
     * Le choix de l'échangeur décide de la commission encaissée sur chaque
     * échange : SimpleSwap la laisse régler entre 0,4 et 5 %, ChangeNOW
     * non. Un retour silencieux à ChangeNOW ne casserait rien de visible —
     * les échanges continueraient de fonctionner — et changerait
     * discrètement les revenus. C'est précisément ce qui ne se remarque
     * pas.
     *
     * Le test échoue aussi si quelqu'un écrit swap.provider=changenow dans
     * son local.properties, ce qui est légitime pour comparer les deux.
     * Dans ce cas, le test dit ce qu'il doit dire : ce n'est pas la
     * configuration de référence.
     */
    @Test fun `simpleswap est l'echangeur configure`() {
        assertEquals(
            "swap.provider ne vaut pas simpleswap",
            "simpleswap",
            com.vaultex.core.config.ApiKeys.SWAP_PROVIDER.lowercase().trim()
        )
    }
}
