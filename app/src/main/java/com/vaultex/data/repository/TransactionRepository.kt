package com.vaultex.data.repository

import com.vaultex.data.local.dao.TransactionDao
import com.vaultex.data.local.entity.TransactionEntity
import com.vaultex.data.remote.api.EvmRpcApi
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao
) {
    fun observeAll(): Flow<List<TransactionEntity>> = transactionDao.observeAll()

    fun observeByAddress(address: String): Flow<List<TransactionEntity>> =
        transactionDao.observeByAddress(address)

    suspend fun saveTransaction(tx: TransactionEntity) = transactionDao.insert(tx)

    suspend fun updateStatus(hash: String, status: String, confirmations: Int) =
        transactionDao.updateStatus(hash, status, confirmations)
}
