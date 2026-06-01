package com.vaultex.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.data.local.dao.TransactionDao
import com.vaultex.data.local.entity.TransactionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TxDetailViewModel @Inject constructor(
    private val transactionDao: TransactionDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val hash: String = savedStateHandle["hash"] ?: ""

    private val _tx = MutableStateFlow<TransactionEntity?>(null)
    val tx: StateFlow<TransactionEntity?> = _tx.asStateFlow()

    init {
        viewModelScope.launch {
            transactionDao.observeAll().collect { list ->
                _tx.value = list.find { it.hash == hash }
            }
        }
    }
}
