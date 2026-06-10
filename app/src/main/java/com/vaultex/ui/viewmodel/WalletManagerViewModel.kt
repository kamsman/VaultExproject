package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val walletDao: WalletDao
) : ViewModel() {

    val wallets: StateFlow<List<WalletEntity>> = walletDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun activateWallet(id: String) {
        viewModelScope.launch {
            walletDao.deactivateAll()
            walletDao.activate(id)
        }
    }

    fun renameWallet(id: String, name: String) {
        viewModelScope.launch { walletDao.rename(id, name) }
    }

    fun deleteWallet(id: String) {
        viewModelScope.launch { walletDao.delete(id) }
    }
}
