package com.vaultex.core.tx

import com.vaultex.data.remote.dto.DogechainSortieDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ═══════════════════════════════════════════════════════════════════════
 * DEUX SERVICES, DEUX CONVENTIONS DE MONTANT — ET RIEN NE LE DIT
 * ═══════════════════════════════════════════════════════════════════════
 *
 * MESURÉ sur les réponses réelles :
 *
 *   sochain     "value": "0.01000000"   une CHAÎNE, en DOGE
 *   dogechain   "value": 1000000        un ENTIER, en UNITÉS
 *
 * La même pièce. Et le MÊME service emploie les deux : `/address/balance`
 * rend une chaîne décimale en DOGE, `/address/unspent` un entier en
 * unités.
 *
 * Le champ était déclaré en chaîne et passé par la conversion décimale.
 * Gson convertit un nombre en chaîne sans rien dire, et une pièce de
 * 0,01 DOGE devenait 1 000 000 DOGE — cent millions de fois trop grande.
 *
 * Le portefeuille aurait affiché une fortune, et tout envoi aurait été
 * refusé par le réseau : des entrées qui ne couvrent pas ce qu'on
 * prétend dépenser. Sans erreur lisible nulle part.
 *
 * Les valeurs ci-dessous sont copiées d'une réponse réelle de dogechain.
 * ═══════════════════════════════════════════════════════════════════════
 */
class DogeDepotLectureTest {

    private val depot = DogeDepot(
        dogechain = object : com.vaultex.data.remote.api.DogechainApi {
            override suspend fun solde(adresse: String) =
                com.vaultex.data.remote.dto.DogechainSoldeDto()
            override suspend fun sortiesNonDepensees(adresse: String, page: Int) =
                com.vaultex.data.remote.dto.DogechainSortiesDto()
            /*
            JAMAIS APPELEE ICI, ET ELLE LE DIT.

            `DogeDepot` ne lit que le solde et les pieces ; l'historique
            est le travail de `TransactionSyncService`. Rendre un objet
            vide laisserait croire qu'il n'y a pas de transactions, et un
            test qui s'appuierait dessus passerait pour une mauvaise
            raison.

            Lever est plus honnete : si quelqu'un branche un jour ce
            chemin sur DogeDepot, l'echec nomme ce qu'il faut ecrire.
            */
            override suspend fun transactions(
                adresse: String, page: Int
            ): com.vaultex.data.remote.dto.DogechainTransactionsDto =
                throw UnsupportedOperationException(
                    "DogeDepot ne lit pas l'historique — voir TransactionSyncService.syncDoge"
                )
        },
        blockcypher = object : com.vaultex.data.remote.api.BlockCypherDogeApi {
            override suspend fun diffuser(
                corps: com.vaultex.data.remote.dto.BlockCypherPousseeDto,
                jeton: String?
            ) = com.vaultex.data.remote.dto.BlockCypherResultatDto()
        }
    )

    /** La première sortie de la réponse réelle, champ pour champ. */
    private fun reelle(
        valeur: Long? = 1_000_000L,
        confirmations: Long? = 637_482L,
        hash: String? = "1a968537014743dc183a6fa4a3f45fa4309f92bfd03f0c4cb4d86971b2fa206d",
        index: Int? = 0,
    ) = DogechainSortieDto(
        address = "DBx8K8iX2ca3MVrxYnRH5PF6FQgaXiDvSU",
        txHash = hash,
        sortieIndex = index,
        script = "76a9144ab5c91866ef9ac85bd0835345b86fceff230f6388ac",
        value = valeur,
        confirmations = confirmations,
    )

    /*
    LE TEST QUI COMPTE. 1 000 000 est le montant en UNITES, soit 0,01 DOGE.
    S'il ressort multiplie par cent millions, la confusion est revenue.
    */
    @Test
    fun `la valeur est lue en unites, pas en DOGE`() {
        val piece = depot.lue(reelle(), confirmationsMinimum = 1)
        assertEquals(1_000_000L, piece?.unites)
        assertEquals(
            "0,01 DOGE et rien d'autre",
            "0.01", DogeLecture.texteDepuisUnites(piece!!.unites)
        )
    }

    @Test
    fun `l identifiant et le rang de la sortie sont repris`() {
        val piece = depot.lue(reelle(index = 3), confirmationsMinimum = 1)
        assertEquals(
            "1a968537014743dc183a6fa4a3f45fa4309f92bfd03f0c4cb4d86971b2fa206d",
            piece?.txidAffiche
        )
        assertEquals(3, piece?.index)
    }

    /*
    DEPENSER UNE PIECE NON CONFIRMEE produit une transaction qui depend
    d'une autre : si la premiere est remplacee, la notre devient invalide.
    */
    @Test
    fun `une piece non confirmee est ecartee`() {
        assertNull(depot.lue(reelle(confirmations = 0L), confirmationsMinimum = 1))
        assertEquals(
            1_000_000L,
            depot.lue(reelle(confirmations = 1L), confirmationsMinimum = 1)?.unites
        )
    }

    /*
    UNE LIGNE ABIMEE EST ECARTEE, PAS DEVINEE. Un champ manquant chez un
    fournisseur distant n'est pas un defaut de programmation : c'est un
    cas a traiter, et le traiter veut dire ne pas s'en servir.
    */
    @Test
    fun `une ligne incomplete est ecartee`() {
        assertNull("sans valeur", depot.lue(reelle(valeur = null), 1))
        assertNull("sans identifiant", depot.lue(reelle(hash = null), 1))
        assertNull("sans rang", depot.lue(reelle(index = null), 1))
        assertNull("valeur nulle", depot.lue(reelle(valeur = 0L), 1))
        assertNull("identifiant tronque", depot.lue(reelle(hash = "1a96"), 1))
    }
}
