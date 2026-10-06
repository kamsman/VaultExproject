package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.core.crypto.WalletManager
import com.vaultex.core.security.SecureStorage
import com.vaultex.domain.pi.PiCompteService
import com.vaultex.domain.pi.SoldePi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class PiState(
    val adresse: String? = null,
    val solde: SoldePi? = null,
    val chargement: Boolean = false,
    /**
     * Prix d'UN Pi, en francs. Null : non coté ou service muet.
     *
     * C'est une MOYENNE DE MARCHÉ, et l'écran le dit. Le Pi s'échange à des
     * prix sensiblement différents d'une place à l'autre — OKX, Gate, MEXC
     * n'affichent pas le même. Présenter ce chiffre comme « le » prix
     * laisserait croire qu'on obtiendra exactement cela en vendant.
     */
    val prixXof: Double? = null
)

@HiltViewModel
class PiViewModel @Inject constructor(
    private val secureStorage: SecureStorage,
    private val compte: PiCompteService,
    private val coinGecko: com.vaultex.data.remote.api.CoinGeckoApi
) : ViewModel() {

    private val _state = MutableStateFlow(PiState())
    val state: StateFlow<PiState> = _state.asStateFlow()

    init { charger() }

    fun charger() {
        viewModelScope.launch {
            _state.update { it.copy(chargement = true) }
            val adresse = withContext(Dispatchers.IO) {
                val m = secureStorage.getMnemonic() ?: return@withContext null
                runCatching {
                    WalletManager.deriveAddresses(m, secureStorage.getPassphrase()).pi
                }.getOrNull()
            }
            if (adresse == null) {
                _state.update { it.copy(chargement = false) }
                return@launch
            }
            _state.update { it.copy(adresse = adresse) }
            val solde = withContext(Dispatchers.IO) { compte.solde(adresse) }
            _state.update { it.copy(solde = solde, chargement = false) }
            /*
            Le prix APRÈS le solde, et dans son propre appel : un service de
            cotation lent ou muet ne doit pas retarder l'information
            principale, qui est le solde. Son absence se voit — la ligne du
            prix disparaît — et n'empêche rien.
            */
            val prix = withContext(Dispatchers.IO) {
                runCatching {
                    coinGecko.getPrices(
                        ids = ID_COINGECKO,
                        vsCurrencies = "usd,xof",
                        include24hChange = false,
                        includeMarketCap = false
                    )[ID_COINGECKO]?.xof?.takeIf { p -> p > 0.0 }
                }.getOrNull()
            }
            _state.update { it.copy(prixXof = prix) }
        }
    }

    private companion object {
        /**
         * Identifiant du Pi chez CoinGecko.
         *
         * Un identifiant faux ne lève AUCUNE erreur : la réponse est une
         * table vide, le prix reste nul, et la ligne disparaît comme si le
         * Pi n'était pas coté. C'est la seule valeur à revérifier si le prix
         * ne s'affiche jamais alors que le solde, lui, se lit.
         */
        const val ID_COINGECKO = "pi-network"
    }
}
