package com.vaultex.core.tx

import com.vaultex.data.remote.api.BlockCypherDogeApi
import com.vaultex.data.remote.api.DogechainApi
import com.vaultex.data.remote.dto.BlockCypherPousseeDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * LE SEUL ENDROIT QUI PARLE AUX FOURNISSEURS DOGECOIN
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Entre le transport — deux interfaces Retrofit — et la chaîne de calcul,
 * qui ne connaît aucun réseau. Tout ce qui est ici est de la traduction et
 * de la tolérance aux pannes ; aucune décision sur l'argent ne s'y prend.
 *
 * ─── DEUX MAISONS, PARCE QU'AUCUNE NE FAIT LES DEUX ────────────────────────
 *
 * Mesuré depuis Ouagadougou : dogechain pagine vraiment et donne le solde
 * total en un appel, mais ne diffuse pas. BlockCypher diffuse et est le seul
 * à le faire. Sochain a le meilleur quota et ne montre que dix pièces.
 *
 * ─── RIEN NE LÈVE, TOUT REND null ──────────────────────────────────────────
 *
 * Un fournisseur distant qui ne répond pas n'est pas un défaut de
 * programmation : c'est l'état normal d'un réseau mobile au Burkina Faso.
 * Laisser une exception remonter ferait tomber un écran entier sur une
 * coupure de trois secondes.
 *
 * `null` DIT « JE N'AI PAS PU REGARDER », ET CE N'EST PAS ZÉRO. La
 * distinction est tout l'intérêt : afficher « 0 DOGE » à quelqu'un dont le
 * réseau a hoqueté lui ferait croire ses fonds disparus.
 * ═══════════════════════════════════════════════════════════════════════════
 */
@Singleton
class DogeDepot @Inject constructor(
    private val dogechain: DogechainApi,
    private val blockcypher: BlockCypherDogeApi,
) {

    /**
     * Le solde complet d'une adresse, en unités. `null` si on n'a pas pu
     * regarder.
     *
     * UN SEUL APPEL, et c'est pourquoi cette méthode existe séparément de
     * [pieces] : le solde arrive complet, les pièces sont paginées. Un
     * portefeuille à cinquante pièces ferait cinq appels à chaque ouverture
     * d'écran si on les confondait.
     */
    suspend fun solde(adresse: String): Long? = try {
        val r = dogechain.solde(adresse)
        if (r.success != 1) null else DogeLecture.unitesDepuisTexte(r.balance)
    } catch (_: Exception) {
        null
    }

    /**
     * Les pièces dépensables, en parcourant les pages.
     *
     * [plafondUnites] arrête le parcours dès qu'il y a de quoi couvrir un
     * envoi : inutile de lire cinquante pages pour en dépenser deux. Zéro
     * lit tout, ce que seul un inventaire complet demande.
     *
     * LA GARDE CONTRE UN SERVICE QUI MENT vit dans [DogeLecture.parcourir],
     * pas ici : sochain rend la même page quel que soit le numéro, et un
     * parcours naïf accumulerait vingt fois les mêmes pièces.
     */
    suspend fun pieces(
        adresse: String,
        plafondUnites: Long = 0L,
        confirmationsMinimum: Int = 1,
    ): List<DogeSelection.Piece> {
        /*
        LE PARCOURS EST SYNCHRONE ET L'APPEL NE L'EST PAS. `parcourir` prend
        une fonction ordinaire ; on lit donc les pages d'abord, puis on les
        assemble. C'est volontaire : rendre `parcourir` suspendue le
        couplerait aux coroutines pour une logique qui n'en a aucun besoin,
        et elle ne serait plus testable aussi simplement.
        */
        val pages = mutableListOf<List<DogeLecture.PieceLue>?>()
        val dejaVues = HashSet<String>()
        var cumul = 0L

        for (n in 1..PAGES_MAXIMUM) {
            val lot = try {
                val r = dogechain.sortiesNonDepensees(adresse, n)
                if (r.success != 1) null
                else r.sorties.orEmpty().mapNotNull { lue(it, confirmationsMinimum) }
            } catch (_: Exception) {
                null
            }
            pages += lot
            if (lot.isNullOrEmpty()) break

            /*
            ═══════════════════════════════════════════════════════════════
            DEUX GARDES QUI SE RESSEMBLENT ET NE FONT PAS LE MÊME TRAVAIL
            ═══════════════════════════════════════════════════════════════

            Celle-ci ÉCONOMISE DES APPELS : sochain rend la même page quel
            que soit le numéro, et sans elle on en demanderait vingt pour
            rien, sur un quota compté et une connexion lente.

            Celle de [DogeLecture.parcourir] GARANTIT LE RÉSULTAT : même si
            des doublons arrivaient ici, ils ne seraient pas comptés deux
            fois. Elle reste l'autorité sur ce qui ressort.

            Les deux sont nécessaires, et aucune ne remplace l'autre : l'une
            protège la facture, l'autre protège le solde.
            ═══════════════════════════════════════════════════════════════
            */
            val nouvelles = lot.filter { dejaVues.add(it.txidAffiche + ":" + it.index) }
            if (nouvelles.isEmpty()) break
            cumul += nouvelles.sumOf { it.unites }

            if (plafondUnites > 0 && cumul >= plafondUnites) break
        }

        return DogeLecture.parcourir(plafondUnites) { n -> pages.getOrNull(n - 1) }
            .map {
                DogeSelection.Piece(
                    txidAffiche = it.txidAffiche,
                    index = it.index,
                    unites = it.unites,
                    // `parcourir` ne garde pas les confirmations ; elles ont
                    // servi au filtre ci-dessus, et ce qui ressort est déjà
                    // confirmé.
                    confirmations = confirmationsMinimum,
                )
            }
    }

    /**
     * Pousse une transaction signée. Rend son identifiant, ou `null`.
     *
     * CE QUI N'EST PAS PROUVÉ : que BlockCypher accepte une transaction
     * VALIDE que nous fabriquons. Il refuse proprement un corps invalide en
     * nommant le défaut, donc il analyse — pas qu'il relaie. Seul un envoi
     * réel le dira, et c'est pour ça que le message d'erreur est remonté
     * plutôt qu'avalé.
     */
    suspend fun diffuser(hex: String, jeton: String? = null): Diffusion = try {
        val r = blockcypher.diffuser(BlockCypherPousseeDto(hex), jeton)
        val id = r.tx?.hash
        when {
            !id.isNullOrBlank() -> Diffusion.Ok(id)
            !r.error.isNullOrBlank() -> Diffusion.Refus(r.error!!)
            !r.errors.isNullOrEmpty() ->
                Diffusion.Refus(r.errors!!.joinToString("; ") { it.values.joinToString() })
            else -> Diffusion.Refus("réponse sans identifiant ni raison")
        }
    } catch (e: Exception) {
        Diffusion.Injoignable(e.message ?: "diffuseur injoignable")
    }

    sealed class Diffusion {
        data class Ok(val txid: String) : Diffusion()
        /** Le service a répondu, et il refuse. La raison vient de lui. */
        data class Refus(val raison: String) : Diffusion()
        /** On n'a pas pu lui parler. Réessayer a du sens, contrairement au refus. */
        data class Injoignable(val raison: String) : Diffusion()
    }

    private fun lue(
        dto: com.vaultex.data.remote.dto.DogechainSortieDto,
        confirmationsMinimum: Int,
    ): DogeLecture.PieceLue? {
        val txid = dto.txHash?.takeIf { it.length == 64 } ?: return null
        val index = dto.sortieIndex ?: return null
        val unites = DogeLecture.unitesDepuisTexte(dto.value) ?: return null
        if (unites <= 0) return null
        /*
        DÉPENSER UNE PIÈCE NON CONFIRMÉE produit une transaction qui dépend
        d'une autre : si la première est remplacée, la nôtre devient
        invalide, et personne ne sait l'expliquer à qui la subit.
        */
        val conf = dto.confirmations ?: 0
        if (conf < confirmationsMinimum) return null
        return DogeLecture.PieceLue(txid, index, unites, null)
    }

    private companion object {
        const val PAGES_MAXIMUM = 20
    }
}
