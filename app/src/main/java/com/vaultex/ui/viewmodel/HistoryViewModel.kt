package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.vaultex.data.local.dao.TransactionDao
import com.vaultex.data.local.entity.TransactionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    transactionDao: TransactionDao
) : ViewModel() {

    val transactions: Flow<List<TransactionEntity>> = transactionDao.observeAll()
}
