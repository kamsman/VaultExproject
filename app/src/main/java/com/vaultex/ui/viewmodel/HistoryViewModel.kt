package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.data.local.dao.TransactionDao
import com.vaultex.data.local.entity.TransactionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val transactionDao: TransactionDao
) : ViewModel() {

    val transactions: StateFlow<List<TransactionEntity>> =
        transactionDao.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredByChain = MutableStateFlow<String?>(null)

    val filtered: StateFlow<List<TransactionEntity>> = combine(transactions, filteredByChain) { txs, chain ->
        if (chain == null) txs else txs.filter { it.blockchain == chain }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun filterByChain(chain: String?) = filteredByChain.update { chain }
}
