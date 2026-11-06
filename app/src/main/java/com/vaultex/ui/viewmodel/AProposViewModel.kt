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
 * Un membre de l'équipe, tel que l'écran l'affiche.
 *
 * `photo` peut être vide, et c'est l'état NORMAL tant qu'aucune image n'a
 * été déposée sur le relais — pas un cas d'erreur. L'écran dessine alors
 * les initiales, ce qui se tient tout seul.
 */
data class Membre(
    val nom: String,
    val poste: String,
    val photo: String
)

data class EtatAPropos(
    val chargement: Boolean = true,
    val membres: List<Membre> = emptyList()
)

@HiltViewModel
class AProposViewModel @Inject constructor(
    private val api: ChangeApi
) : ViewModel() {

    private val _etat = MutableStateFlow(EtatAPropos())
    val etat: StateFlow<EtatAPropos> = _etat.asStateFlow()

    init {
        charger()
    }

    /**
     * Va chercher l'équipe.
     *
     * ═══════════════════════════════════════════════════════════════════
     * UN ÉCHEC NE LAISSE PAS UN ÉCRAN VIDE
     * ═══════════════════════════════════════════════════════════════════
     *
     * « À propos » affiche d'abord ce que l'application sait d'elle-même :
     * son nom, sa version, ce qu'elle est. Tout cela est dans l'APK et
     * n'a besoin d'aucun réseau.
     *
     * L'équipe est un supplément. Sans réseau, la section disparaît
     * simplement — on ne pose pas un message d'erreur sur un écran dont
     * l'essentiel s'affiche très bien.
     * ═══════════════════════════════════════════════════════════════════
     */
    fun charger() {
        viewModelScope.launch {
            _etat.update { it.copy(chargement = true) }
            val lu = try {
                withContext(Dispatchers.IO) { api.equipe() }
            } catch (_: Exception) {
                null
            }
            /*
            LE MÊME FILTRAGE STRICT QUE POUR LES ACTUALITÉS : un membre
            sans nom ou sans poste n'est pas un membre, c'est une ligne
            grise. Le DTO a tous ses champs nullables parce qu'il vient du
            réseau ; l'écran, lui, n'aura aucun `?` à dessiner.
            */
            val membres = lu?.equipe.orEmpty().mapNotNull { m ->
                val nom = m.nom?.trim().orEmpty()
                val poste = m.poste?.trim().orEmpty()
                if (nom.isEmpty() || poste.isEmpty()) return@mapNotNull null
                Membre(
                    nom = nom,
                    poste = poste,
                    /*
                    ON N'ACCEPTE QU'UNE ADRESSE HTTPS. Le relais filtre déjà
                    le nom de fichier et construit l'adresse lui-même, donc
                    ceci ne devrait jamais servir. C'est exactement pour ça
                    qu'on le met : la barrière qui ne sert jamais est celle
                    qui tient le jour où l'autre cède.
                    */
                    photo = m.photo?.trim().orEmpty().takeIf { it.startsWith("https://") }.orEmpty()
                )
            }
            _etat.update { it.copy(chargement = false, membres = membres) }
        }
    }
}
