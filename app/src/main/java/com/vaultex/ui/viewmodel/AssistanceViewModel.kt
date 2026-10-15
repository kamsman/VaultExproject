package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.data.remote.api.ChangeApi
import com.vaultex.data.remote.dto.DemandeAssistance
import com.vaultex.data.remote.dto.TourAssistance
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
 * Un message affiché, du côté de l'écran.
 *
 * Volontairement distinct du DTO : l'écran a besoin de savoir si un message
 * est en cours d'envoi ou s'il a échoué, et le relais n'a que faire de ces
 * états-là.
 */
data class MessageAssistance(
    val deMoi: Boolean,
    val texte: String
)

data class AssistanceState(
    val messages: List<MessageAssistance> = emptyList(),
    val saisie: String = "",
    val enCours: Boolean = false,
    /** Motif du dernier échec, ou null. */
    val erreur: String? = null,
    /**
     * Vrai quand le relais conseille de passer au changeur.
     *
     * Ce n'est pas une erreur de plus : c'est l'état où l'assistance
     * automatique a fait ce qu'elle pouvait, et où il reste un humain.
     * L'écran propose alors Telegram ou WhatsApp.
     */
    val passerAuChangeur: Boolean = false,
    /*
    ═══════════════════════════════════════════════════════════════════════
    LE NUMÉRO, DEMANDÉ APRÈS TROIS ÉCHANGES
    ═══════════════════════════════════════════════════════════════════════

    Pas avant. Quelqu'un qui ouvre l'assistance a une question, et lui
    réclamer son numéro à la première ligne, c'est le formulaire qu'on
    ferme — il n'a encore rien reçu en échange.

    Au troisième échange, la conversation a de la valeur pour les deux :
    elle dure, donc elle n'est pas triviale, et il y a une chance réelle
    qu'elle doive être reprise par le changeur. Le numéro sert à ça, et on
    le dit ainsi.

    IL N'EST PAS OBLIGATOIRE. « Continuer sans » reste là. Un blocage dur
    sur un numéro que le changeur n'utilisera peut-être jamais serait payé
    par ceux qui n'osent pas le donner — et ce sont souvent les mêmes qui
    ont le plus besoin d'aide.
    ═══════════════════════════════════════════════════════════════════════
    */
    val numeroDemande: Boolean = false,
    val numero: String = "",
    val numeroDonne: Boolean = false,
    val numeroIgnore: Boolean = false
) {
    /** Combien de fois l'utilisateur a parlé. */
    val echanges: Int get() = messages.count { it.deMoi }

    /** Huit chiffres, comme partout ailleurs dans l'application. */
    val numeroValide: Boolean
        get() = numero.filter { it.isDigit() }.length == 8

    val peutEnvoyer: Boolean
        get() = saisie.isNotBlank() && !enCours && !numeroDemande
}

/*
═══════════════════════════════════════════════════════════════════════════
L'ASSISTANCE
═══════════════════════════════════════════════════════════════════════════

Le changeur dort, et les questions arrivent la nuit : « c'est quoi le
minimum », « combien de temps », « j'ai envoyé et je n'ai rien reçu ». Sans
réponse, elles font fermer l'application — et c'est le genre de départ dont
on n'entend jamais parler.

─── RIEN N'EST GARDÉ, NI ICI NI LÀ-BAS ──────────────────────────────────

L'historique vit dans cet objet, et meurt avec l'écran. Le relais ne garde
rien entre deux appels : il reçoit la conversation, répond, oublie.

Ce n'est pas de la paresse. Une conversation d'assistance contient ce que
quelqu'un n'a pas compris, combien il échange, et parfois son numéro. Ne
rien garder est la seule façon de ne rien perdre.

─── CE QUE L'ÉCRAN NE PEUT PAS GARANTIR ─────────────────────────────────

Que la réponse soit juste. Le modèle reçoit les vrais réglages — minimum,
plafond, marge, délai — mais il reste un modèle. C'est pourquoi il ne
donne JAMAIS d'adresse, que le relais filtre sa sortie, et que « parler au
changeur » est à un geste.
═══════════════════════════════════════════════════════════════════════════
*/
@HiltViewModel
class AssistanceViewModel @Inject constructor(
    private val api: ChangeApi
) : ViewModel() {

    private val _state = MutableStateFlow(AssistanceState())
    val state: StateFlow<AssistanceState> = _state.asStateFlow()

    fun onSaisie(v: String) = _state.update { it.copy(saisie = v, erreur = null) }
    fun onNumero(v: String) = _state.update { it.copy(numero = v) }

    /** « Continuer sans donner mon numéro ». */
    fun ignorerNumero() = _state.update {
        it.copy(numeroDemande = false, numeroIgnore = true)
    }

    /**
     * Garde le numéro et reprend la conversation.
     *
     * IL N'EST ENVOYÉ NULLE PART POUR L'INSTANT. Il n'ira au changeur que
     * si l'utilisateur appuie sur « parler au changeur » — c'est-à-dire
     * s'il demande lui-même qu'on le rappelle. Le collecter puis le
     * transmettre sans qu'il le sache serait exactement ce que cette
     * application reproche aux autres.
     */
    fun validerNumero() = _state.update {
        if (!it.numeroValide) it else it.copy(numeroDemande = false, numeroDonne = true)
    }

    /**
     * Pose une question toute faite.
     *
     * Elle passe par `saisie` plutôt que d'appeler `envoyer` avec un
     * argument : le message doit apparaître dans la conversation comme
     * s'il avait été tapé, parce que c'est ce qu'il est du point de vue de
     * celui qui lit. Une question posée autrement se relirait comme venue
     * de nulle part.
     */
    fun poser(question: String) {
        if (_state.value.enCours) return
        _state.update { it.copy(saisie = question, erreur = null) }
        envoyer()
    }

    fun envoyer() {
        val s = _state.value
        if (!s.peutEnvoyer) return
        val question = s.saisie.trim()

        /*
        LE MESSAGE EST AFFICHÉ AVANT D'ÊTRE PARTI.

        Sur un réseau lent — le cas courant ici — plusieurs secondes
        séparent l'appui de la réponse. Sans cet affichage immédiat, on
        croit que rien ne s'est passé et on réappuie, ce qui pose la
        question deux fois et la facture aussi.
        */
        val avec = s.messages + MessageAssistance(deMoi = true, texte = question)
        _state.update {
            it.copy(messages = avec, saisie = "", enCours = true, erreur = null, passerAuChangeur = false)
        }

        viewModelScope.launch {
            val tours = avec.map {
                TourAssistance(role = if (it.deMoi) "user" else "assistant", content = it.texte)
            }
            val r = try {
                withContext(Dispatchers.IO) { api.assistance(DemandeAssistance(tours)) }
            } catch (e: Exception) {
                com.vaultex.core.monitoring.reportUnlessCancelled("assistance", e)
                null
            }

            val reponse = r?.reponse?.takeIf { r.ok == true && it.isNotBlank() }
            _state.update { etat ->
                val suite = if (reponse != null) {
                    etat.messages + MessageAssistance(deMoi = false, texte = reponse)
                } else etat.messages

                etat.copy(
                    messages = suite,
                    enCours = false,
                    erreur = if (reponse == null) {
                        r?.raison ?: "L'assistance n'a pas répondu. Vérifie ta connexion."
                    } else null,
                    // `humain` vient du relais ; sans réponse du tout, on le
                    // propose aussi — c'est le moment où un humain sert.
                    passerAuChangeur = reponse == null || r?.humain == true,
                    /*
                    ON NE DEMANDE LE NUMÉRO QU'UNE FOIS, et seulement si
                    l'échange a abouti. Le réclamer après un échec
                    ajouterait une demande à une déception.
                    */
                    numeroDemande = reponse != null &&
                        !etat.numeroDonne && !etat.numeroIgnore &&
                        etat.echanges >= SEUIL_NUMERO
                )
            }
        }
    }

    private companion object {
        /**
         * Trois échanges avant de demander le numéro.
         *
         * En dessous, la conversation peut être une seule question dont la
         * réponse suffit — et on aurait réclamé un numéro pour rien. Au
         * troisième, elle dure : il y a une chance réelle qu'elle doive
         * être reprise par un humain.
         */
        const val SEUIL_NUMERO = 3
    }
}
