package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.core.session.WalletStore
import com.vaultex.data.local.dao.WalletDao
import com.vaultex.data.local.entity.WalletEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WalletManagerViewModel @Inject constructor(
    private val walletDao: WalletDao,
    private val walletStore: WalletStore,
    private val secureStorage: com.vaultex.core.security.SecureStorage
) : ViewModel() {

    init {
        // Migration : inscrit le wallet historique (pré-multi-wallets) dans la
        // liste s'il n'y est pas encore — son seed reste intact et sélectionnable.
        viewModelScope.launch { runCatching { walletStore.ensureRegistered() } }
    }

    val wallets: StateFlow<List<WalletEntity>> = walletDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Bascule de wallet via WalletStore : change le seed ACTIF et purge les
     * caches financiers (l'ancien bug laissait les montants du wallet précédent).
     * [onSwitched] est appelé UNIQUEMENT si la bascule a réussi — l'écran
     * repart alors sur un accueil neuf (pile vidée → aucun ancien solde en RAM).
     */
    fun activateWallet(id: String, onSwitched: () -> Unit = {}) {
        viewModelScope.launch {
            if (walletStore.switchWallet(id)) onSwitched()
        }
    }

    fun renameWallet(id: String, name: String) {
        viewModelScope.launch {
            walletDao.rename(id, name)
            // Le nom affiché sur l'accueil suit le wallet actif.
            if (id == secureStorage.activeWalletId()) secureStorage.saveWalletName(name)
        }
    }

    /** Supprime un wallet INACTIF (entité + seed chiffré). Refus si actif. */
    fun deleteWallet(id: String) {
        viewModelScope.launch { walletStore.deleteWallet(id) }
    }
}
