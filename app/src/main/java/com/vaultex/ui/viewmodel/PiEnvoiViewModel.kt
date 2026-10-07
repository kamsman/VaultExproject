package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.core.crypto.PiWallet
import com.vaultex.core.crypto.PiXdr
import com.vaultex.core.security.SecureStorage
import com.vaultex.domain.pi.CapaciteEnvoiPi
import com.vaultex.domain.pi.PiEnvoiUseCase
import com.vaultex.domain.pi.ResultatEnvoiPi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Type de mémo choisi par l'utilisateur. */
enum class TypeMemoPi { AUCUN, TEXTE, IDENTIFIANT }

data class PiEnvoiState(
    val capacite: CapaciteEnvoiPi? = null,
    val chargement: Boolean = true,
    val destination: String = "",
    val montant: String = "",
    val typeMemo: TypeMemoPi = TypeMemoPi.AUCUN,
    val memo: String = "",
    val envoiEnCours: Boolean = false,
    val resultat: ResultatEnvoiPi? = null,
    /**
     * Vrai quand la destination n'existe pas encore sur la chaîne.
     *
     * L'écran le dit AVANT la confirmation, parce que cela change le
     * montant minimum : un compte neuf doit être créé, et le réseau exige
     * un plancher pour cela. Le découvrir au refus serait tardif.
     */
    val destinationNeuve: Boolean? = null
)

@HiltViewModel
class PiEnvoiViewModel @Inject constructor(
    private val secureStorage: SecureStorage,
    private val envoi: PiEnvoiUseCase,
    private val comptes: com.vaultex.domain.pi.PiCompteService
) : ViewModel() {

    private val _state = MutableStateFlow(PiEnvoiState())
    val state: StateFlow<PiEnvoiState> = _state.asStateFlow()

    private var jobDestination: kotlinx.coroutines.Job? = null

    init {
        charger()
        /*
        ON RÉCONCILIE DÈS L'OUVERTURE DE L'ÉCRAN.

        Si un envoi précédent s'est perdu — application fermée pendant
        l'appel réseau, connexion coupée — sa trace est sur le disque. On
        demande son sort au réseau MAINTENANT, avant que l'utilisateur ne
        saisisse quoi que ce soit : il a le droit de savoir que ses Pi sont
        déjà partis avant de les envoyer une seconde fois.
        */
        viewModelScope.launch {
            withContext(Dispatchers.IO) { envoi.reconcilier() }
                ?.let { r -> _state.update { it.copy(resultat = r) } }
        }
    }

    fun charger() {
        viewModelScope.launch {
            _state.update { it.copy(chargement = true) }
            val capacite = withContext(Dispatchers.IO) {
                val m = secureStorage.getMnemonic() ?: return@withContext null
                envoi.capacite(m, secureStorage.getPassphrase())
            }
            _state.update { it.copy(capacite = capacite, chargement = false) }
        }
    }

    fun onDestination(valeur: String) {
        _state.update { it.copy(destination = valeur, destinationNeuve = null) }
        jobDestination?.cancel()
        val propre = valeur.trim()
        if (!PiWallet.adresseValide(propre)) return
        /*
        On interroge la chaîne dès que l'adresse est complète et valide,
        pas à la confirmation. Savoir qu'un compte est neuf change le
        montant minimum acceptable ; l'apprendre après avoir tapé un
        montant trop petit oblige à tout refaire.
        */
        jobDestination = viewModelScope.launch {
            val etat = withContext(Dispatchers.IO) { comptes.etat(propre) }
            val neuve = when (etat) {
                is com.vaultex.domain.pi.EtatComptePi.Existe -> false
                com.vaultex.domain.pi.EtatComptePi.Inexistant -> true
                com.vaultex.domain.pi.EtatComptePi.Indetermine -> null
            }
            // L'adresse a pu changer pendant l'appel : on ne colle pas une
            // réponse périmée sur une saisie plus récente.
            _state.update {
                if (it.destination.trim() == propre) it.copy(destinationNeuve = neuve) else it
            }
        }
    }

    fun onMontant(valeur: String) = _state.update { it.copy(montant = valeur) }
    fun onTypeMemo(type: TypeMemoPi) = _state.update { it.copy(typeMemo = type, memo = "") }
    fun onMemo(valeur: String) = _state.update { it.copy(memo = valeur) }
    fun effacerResultat() = _state.update { it.copy(resultat = null) }

    /**
     * Bouton MAX : le disponible, réserve ET frais déjà déduits.
     *
     * C'est le seul chiffre qu'on ait le droit de proposer ici. Le solde
     * brut produirait une transaction refusée à chaque fois, et les frais
     * seraient brûlés à chaque tentative.
     */
    fun onMax() {
        val dispo = _state.value.capacite?.disponibleStroops ?: return
        if (dispo <= 0L) return
        _state.update { it.copy(montant = PiXdr.texteDepuisStroops(dispo)) }
    }

    fun envoyer() {
        val s = _state.value
        if (s.envoiEnCours) return
        _state.update { it.copy(envoiEnCours = true, resultat = null) }
        viewModelScope.launch {
            val resultat = withContext(Dispatchers.IO) {
                val m = secureStorage.getMnemonic()
                    ?: return@withContext ResultatEnvoiPi.Invalide(
                        "Portefeuille non initialisé."
                    )
                envoi.envoyer(
                    mnemonic = m,
                    passphrase = secureStorage.getPassphrase(),
                    destination = s.destination,
                    montantTexte = s.montant,
                    memo = memoChoisi(s)
                )
            }
            _state.update { it.copy(envoiEnCours = false, resultat = resultat) }
            // Le solde a bougé (ou les frais) : on relit la capacité.
            if (resultat is ResultatEnvoiPi.Reussi) charger()
        }
    }

    private fun memoChoisi(s: PiEnvoiState): PiXdr.Memo = when (s.typeMemo) {
        TypeMemoPi.AUCUN -> PiXdr.Memo.Aucun
        TypeMemoPi.TEXTE -> s.memo.trim().takeIf { it.isNotEmpty() }
            ?.let { PiXdr.Memo.Texte(it) } ?: PiXdr.Memo.Aucun
        TypeMemoPi.IDENTIFIANT -> s.memo.trim().toLongOrNull()
            ?.let { PiXdr.Memo.Identifiant(it) } ?: PiXdr.Memo.Aucun
    }

    /** Vrai si le formulaire est prêt à être envoyé. */
    fun pretAEnvoyer(): Boolean {
        val s = _state.value
        if (s.envoiEnCours) return false
        if (!PiWallet.adresseValide(s.destination.trim())) return false
        val stroops = PiXdr.stroopsDepuisTexte(s.montant) ?: return false
        if (stroops > (s.capacite?.disponibleStroops ?: 0L)) return false
        return when (s.typeMemo) {
            TypeMemoPi.AUCUN -> true
            TypeMemoPi.TEXTE -> PiXdr.memoTexteValide(s.memo)
            // Un identifiant vide vaut « pas de mémo » ; un identifiant
            // qui n'est pas un nombre est une saisie à corriger, pas un
            // mémo absent — on bloque plutôt que de l'ignorer en silence.
            TypeMemoPi.IDENTIFIANT -> s.memo.isBlank() || s.memo.trim().toLongOrNull() != null
        }
    }
}
