package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.core.crypto.PiXdr
import com.vaultex.core.crypto.WalletManager
import com.vaultex.core.security.SecureStorage
import com.vaultex.domain.pi.EtatComptePi
import com.vaultex.domain.pi.PiCompteService
import com.vaultex.domain.pi.PiReseau
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Le compte Pi doit-il encore être créé, et combien faut-il pour cela ?
 *
 * @param requise vrai tant qu'aucun versement n'a jamais atteint l'adresse.
 * @param minimumPi montant qui créera le compte, écrit pour l'affichage.
 */
data class ActivationPi(val requise: Boolean, val minimumPi: String)

@HiltViewModel
class ReceiveViewModel @Inject constructor(
    private val secureStorage: SecureStorage,
    private val piComptes: PiCompteService,
    private val piReseau: PiReseau
) : ViewModel() {

    data class State(
        val addresses: Map<String, String> = emptyMap(),
        val isLoading: Boolean = true
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _activationPi = MutableStateFlow<ActivationPi?>(null)
    val activationPi: StateFlow<ActivationPi?> = _activationPi.asStateFlow()

    init {
        viewModelScope.launch {
            val addresses = withContext(Dispatchers.IO) {
                val mnemonic = secureStorage.getMnemonic() ?: return@withContext emptyMap<String, String>()
                val derived = WalletManager.deriveAddresses(mnemonic, secureStorage.getPassphrase())
                mapOf(
                    "BTC" to derived.btc,
                    "ETH" to derived.eth,
                    "BNB" to derived.bnb,
                    "SOL" to derived.sol,
                    "TRX" to derived.trx,
                    /*
                    Le Pi est ici alors qu'il n'est PAS dans l'écran Envoyer,
                    et ce déséquilibre est volontaire : recevoir ne demande
                    qu'une adresse, envoyer demande une signature. PiWallet
                    sait faire la première, pas encore la seconde.

                    Proposer la réception avant l'envoi n'est donc pas une
                    demi-mesure — c'est tout ce qu'une adresse permet, et
                    c'est déjà ce dont quelqu'un a besoin pour faire venir
                    ses Pi dans VaultEx.
                    */
                    "PI" to derived.pi
                )
            }
            _state.update { it.copy(addresses = addresses, isLoading = false) }
        }
    }

    /*
    ═══════════════════════════════════════════════════════════════════════
    LE MUR SUR LEQUEL TOUT NOUVEAU PORTEFEUILLE PI VA BUTER
    ═══════════════════════════════════════════════════════════════════════

    Mesuré sur OKX, et c'est la raison d'être de cette fonction : la
    plateforme REFUSE l'adresse VaultEx avec « Adresse incorrecte saisie ».
    La même adresse est pourtant irréprochable — 56 caractères, alphabet
    base32, octet de version 0x30, somme de contrôle CRC16 juste.

    Le contrôle fait par OKX est ailleurs : coller SA PROPRE adresse de
    dépôt Pi dans le même champ passe sans erreur. La seule différence entre
    les deux est que la sienne EXISTE sur la chaîne et que la nôtre est
    neuve. OKX interroge donc le réseau, et refuse d'envoyer vers un compte
    qui n'a jamais été créé.

    C'est cohérent avec la règle de cette famille de réseaux : un paiement
    vers une adresse jamais créditée échoue, il faut une opération de
    création de compte. Une plateforme dont le système de retrait n'émet que
    des paiements ordinaires préfère refuser à la saisie plutôt que de
    laisser partir des fonds vers un échec.

    SANS CE MESSAGE, L'UTILISATEUR EST DANS UNE IMPASSE MUETTE. Il voit une
    adresse, il la colle chez son courtier, on lui dit qu'elle est
    « incorrecte ». Il en conclura que VaultEx est cassé — c'est la seule
    conclusion disponible. Or l'adresse est bonne, et il manque une seule
    chose : un premier versement venu d'un portefeuille Pi, qui sait créer
    un compte.

    LE MINIMUM VIENT DU RÉSEAU, jamais d'une constante : deux parts de
    réserve, et la part est un paramètre que les validateurs peuvent voter.
    Annoncer un chiffre périmé enverrait l'utilisateur essayer un montant
    qui serait refusé.
    ═══════════════════════════════════════════════════════════════════════
    */
    fun verifierComptePi() {
        // Une seule interrogation par ouverture d'écran : la réponse ne
        // change pas tant qu'un versement n'est pas arrivé, et cet écran se
        // recompose à chaque frappe.
        if (_activationPi.value != null) return
        val adresse = _state.value.addresses["PI"]?.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            val etat = withContext(Dispatchers.IO) { piComptes.etat(adresse) }
            /*
            On n'avertit QUE sur une réponse positive « ce compte n'existe
            pas ». Un réseau injoignable rend « indéterminé », et afficher
            l'avertissement dans ce cas serait affirmer sans savoir — sur un
            compte peut-être déjà actif.
            */
            if (etat !is EtatComptePi.Inexistant) {
                _activationPi.value = ActivationPi(requise = false, minimumPi = "")
                return@launch
            }
            val minimum = withContext(Dispatchers.IO) { piReseau.parametres() }
                ?.let { PiXdr.texteDepuisStroops(it.reserveDeBaseStroops * 2) }
            _activationPi.value = ActivationPi(
                requise = true,
                // Repli au-dessus du relevé (0,98 Pi) : un minimum annoncé
                // trop bas fait échouer la tentative, trop haut ne coûte rien.
                minimumPi = minimum ?: "1"
            )
        }
    }
}
