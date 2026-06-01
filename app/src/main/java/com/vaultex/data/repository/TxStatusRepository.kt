package com.vaultex.data.repository

import com.vaultex.data.local.dao.TransactionDao
import com.vaultex.data.remote.api.EvmRpcApi
import com.vaultex.data.remote.dto.JsonRpcRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Polls PENDING EVM transactions and updates their status to CONFIRMED or FAILED in Room.
 * Called from DashboardViewModel.loadDashboard() so statuses are refreshed on each open.
 */
@Singleton
class TxStatusRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    @Named("eth") private val ethRpc: EvmRpcApi,
    @Named("bnb") private val bnbRpc: EvmRpcApi
) {
    suspend fun refreshPendingStatuses() = withContext(Dispatchers.IO) {
        val pending = transactionDao.observeAll().first()
            .filter { it.status == "PENDING" && it.blockchain in listOf("ETH", "BNB", "SWAP") }

        for (tx in pending) {
            try {
                val rpc = if (tx.blockchain == "BNB") bnbRpc else ethRpc
                val res = rpc.rpcCall(
                    JsonRpcRequest("eth_getTransactionReceipt", listOf(tx.hash))
                )
                val receipt = res.result as? Map<*, *> ?: continue
                val status = receipt["status"] as? String ?: continue
                val blockNumber = (receipt["blockNumber"] as? String)
                    ?.removePrefix("0x")?.toLongOrNull(16)
                val newStatus = if (status == "0x1") "CONFIRMED" else "FAILED"
                transactionDao.updateStatus(tx.hash, newStatus, 1)
            } catch (_: Exception) {
                // Network error — leave as PENDING, retry next time
            }
        }
    }
}
