package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.data.remote.api.ChangeApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Une actualité, telle que l'écran l'affiche.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * LE MODÈLE DE L'ÉCRAN N'EST PAS CELUI DU RÉSEAU
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Le DTO a tous ses champs nullables, parce qu'il vient d'un flux RSS que
 * personne ne contrôle. Ici, rien ne l'est : un article sans titre ou sans
 * lien n'est pas un article, il est écarté à la lecture.
 *
 * L'écran n'a donc aucun `?` à gérer, et aucune ligne vide à dessiner.
 * ═══════════════════════════════════════════════════════════════════════
 */
data class Actualite(
    val titre: String,
    val lien: String,
    val source: String,
    val finance: Boolean,
    /** Logo de la source, ou vide — l'ecran dessine alors le rond nu. */
    val logo: String,
    val quand: Long
)

data class EtatActus(
    val chargement: Boolean = true,
    val articles: List<Actualite> = emptyList(),
    val erreur: Boolean = false
)

@HiltViewModel
class ActusViewModel @Inject constructor(
    private val api: ChangeApi,
    private val pastille: com.vaultex.core.session.PastilleActus
) : ViewModel() {

    private val _etat = MutableStateFlow(EtatActus())
    val etat: StateFlow<EtatActus> = _etat.asStateFlow()

    init {
        charger()
    }

    /**
     * Va chercher les actualités.
     *
     * ═══════════════════════════════════════════════════════════════════
     * UNE PANNE RÉSEAU N'EFFACE PAS CE QUI EST DÉJÀ LÀ
     * ═══════════════════════════════════════════════════════════════════
     *
     * Quelqu'un qui tire pour rafraîchir dans un tunnel verrait sa liste
     * disparaître au profit d'un message d'erreur — alors qu'il avait de
     * quoi lire. On garde les articles et on signale l'échec à côté.
     *
     * C'est la même règle que partout dans ce dépôt : un échec de lecture
     * dit « on n'a pas pu regarder », jamais « il n'y a rien ».
     * ═══════════════════════════════════════════════════════════════════
     */
    fun charger() {
        viewModelScope.launch {
            _etat.update { it.copy(chargement = true, erreur = false) }
            val lus = try {
                withContext(Dispatchers.IO) { api.actus() }
            } catch (_: Exception) {
                null
            }
            if (lus == null) {
                _etat.update { it.copy(chargement = false, erreur = true) }
                return@launch
            }
            /*
            LE FILTRAGE EST ICI, ET IL EST STRICT.

            Un titre vide ou un lien qui n'est pas une adresse ne donne
            rien d'utile : une ligne grise qu'on touche et qui n'ouvre
            rien. Mieux vaut trois articles que cinq dont deux sont morts.
            */
            val articles = lus.articles.orEmpty().mapNotNull { a ->
                val titre = a.titre?.trim().orEmpty()
                val lien = a.lien?.trim().orEmpty()
                if (titre.isEmpty() || !lien.startsWith("https://")) return@mapNotNull null
                Actualite(
                    titre = titre,
                    lien = lien,
                    source = a.source?.trim().orEmpty().ifEmpty { "—" },
                    finance = a.theme == "finance",
                    /*
                    HTTPS SEULEMENT, comme pour les photos de l'equipe. Le
                    relais construit lui-meme ces adresses et ne peut donc
                    pas en produire d'autres — c'est precisement pourquoi
                    on verifie : la barriere qui ne sert jamais est celle
                    qui tient le jour ou l'autre cede.
                    */
                    logo = a.logo?.trim().orEmpty()
                        .takeIf { it.startsWith("https://") }.orEmpty(),
                    quand = a.quand ?: 0L
                )
            }
            _etat.update {
                it.copy(chargement = false, articles = articles, erreur = articles.isEmpty())
            }

            /*
            LA PASTILLE S'ÉTEINT ICI, ET SEULEMENT SUR UNE LECTURE RÉUSSIE.

            On est à l'intérieur du cas « la requête a abouti et des
            articles ont été retenus » : les deux sorties précédentes ont
            déjà quitté la fonction. Marquer plus haut éteindrait le point
            pour quelqu'un qui a ouvert l'écran dans un tunnel et n'a rien
            vu — il ne saurait jamais qu'il avait du nouveau à lire.

            On retient la date du PLUS RÉCENT article affiché, pas l'heure
            qu'il est : si un article paraît pendant la lecture, il reste
            du nouveau, et la pastille le dira au prochain passage.
            */
            if (articles.isNotEmpty()) {
                pastille.marquerVu(articles.maxOf { it.quand })
            }
        }
    }
}
