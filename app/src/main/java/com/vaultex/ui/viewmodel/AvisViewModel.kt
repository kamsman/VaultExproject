package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.data.remote.api.ChangeApi
import com.vaultex.data.remote.dto.DemandeAvis
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Où en est le formulaire. */
enum class EtatAvis { SAISIE, ENVOI, MERCI, ECHEC }

data class AvisUiState(
    val texte: String = "",
    /** 0 = aucune étoile touchée. Décoratif : voir [DemandeAvis.etoiles]. */
    val etoiles: Int = 0,
    val etat: EtatAvis = EtatAvis.SAISIE
) {
    /*
    TROIS CARACTÈRES, le même seuil que le relais.

    Le faire ici ET là-bas n'est pas une redite inutile : ici c'est pour
    que le bouton soit grisé plutôt que de laisser partir un appel voué au
    refus ; là-bas c'est parce qu'un téléphone n'est pas digne de
    confiance. Les deux seuils doivent concorder, sinon le bouton s'active
    sur un texte que le relais refusera.
    */
    val peutEnvoyer: Boolean get() = texte.trim().length >= 3 && etat != EtatAvis.ENVOI
}

/**
 * ═══════════════════════════════════════════════════════════════════════
 * L'AVIS PART PAR LE RELAIS, PAS PAR `AdminBot`
 * ═══════════════════════════════════════════════════════════════════════
 *
 * `AdminBot` sait déjà écrire dans le canal Telegram de l'administrateur,
 * et y brancher cet écran aurait pris une ligne.
 *
 * Mais `AdminBot` tient son jeton de `BuildConfig.TG_ADMIN_TOKEN` — donc
 * de l'APK, donc de qui l'ouvre. Un audit de ce dépôt avait cherché les
 * secrets dans l'historique git et n'avait rien trouvé, parce que ces
 * clés viennent de `local.properties`, qui est ignoré par git. Elles
 * n'en sont pas moins en clair dans les 223 installations distribuées.
 *
 * Le relais, lui, tient son jeton dans une variable de Worker que
 * personne ne lit. Ce chemin-ci ne répare pas le problème de l'autre —
 * il refuse seulement de l'agrandir.
 *
 * ET C'EST LE RELAIS QUI ANONYMISE, pas cet écran. Un filtre posé sur le
 * téléphone se contourne en modifiant le téléphone, et c'est le serveur
 * qui écrit dans Telegram. Voir `recevoirAvis` dans worker.js.
 * ═══════════════════════════════════════════════════════════════════════
 */
@HiltViewModel
class AvisViewModel @Inject constructor(
    private val api: ChangeApi
) : ViewModel() {

    private val _state = MutableStateFlow(AvisUiState())
    val state: StateFlow<AvisUiState> = _state.asStateFlow()

    fun saisir(texte: String) {
        /*
        BORNÉ À LA SAISIE, à la même valeur que le relais.

        Sans cette borne, on laisserait quelqu'un écrire trois mille
        caractères avant de les tronquer à 400 côté serveur — c'est-à-dire
        jeter en silence les deux tiers de ce qu'il vient de prendre la
        peine d'écrire. Mieux vaut que le clavier s'arrête.
        */
        _state.update { it.copy(texte = texte.take(AVIS_MAX_CARACTERES), etat = EtatAvis.SAISIE) }
    }

    fun noter(etoiles: Int) {
        // Retoucher la même étoile l'enlève : une note posée par erreur
        // doit pouvoir se retirer, et il n'y a pas de place pour un bouton.
        _state.update { it.copy(etoiles = if (it.etoiles == etoiles) 0 else etoiles) }
    }

    fun envoyer(jours: Int, modele: String, version: String) {
        val s = _state.value
        if (!s.peutEnvoyer) return
        _state.update { it.copy(etat = EtatAvis.ENVOI) }

        viewModelScope.launch {
            val ok = try {
                withContext(Dispatchers.IO) {
                    api.avis(
                        DemandeAvis(
                            texte = s.texte.trim(),
                            etoiles = s.etoiles.takeIf { it in 1..5 },
                            /*
                            -1 SIGNIFIE « INCONNU » DANS `AdminBot`, et le
                            relais n'accepte que les valeurs positives. On
                            le transforme donc en absence ici, plutôt que
                            de laisser partir un « J+-1 » que le relais
                            écarterait sans le dire.
                            */
                            jours = jours.takeIf { it >= 0 },
                            modele = modele.takeIf { it.isNotBlank() },
                            version = version.takeIf { it.isNotBlank() }
                        )
                    )
                }.ok == true
            } catch (e: Exception) {
                /*
                UN ÉCHEC N'EST PAS DÉTAILLÉ À L'UTILISATEUR, et c'est la
                différence avec l'assistance.

                Là-bas, distinguer 503 / 429 / 502 changeait ce que la
                personne devait faire ensuite. Ici, rien ne change : son
                avis n'est pas parti, elle peut réessayer, et aucune des
                causes possibles ne lui demande autre chose.
                */
                com.vaultex.core.monitoring.reportUnlessCancelled("avis", e)
                false
            }
            _state.update { it.copy(etat = if (ok) EtatAvis.MERCI else EtatAvis.ECHEC) }
        }
    }

    private companion object {
        /** La même borne que `AVIS_MAX_CARACTERES` dans worker.js. */
        const val AVIS_MAX_CARACTERES = 400
    }
}
