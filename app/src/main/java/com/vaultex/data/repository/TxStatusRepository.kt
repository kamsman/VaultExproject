package com.vaultex.data.repository

import com.vaultex.data.local.dao.TransactionDao
import com.vaultex.data.remote.api.BitcoinApi
import com.vaultex.data.remote.api.EvmRpcApi
import com.vaultex.data.remote.api.SolanaRpcApi
import com.vaultex.data.remote.api.TronApi
import com.vaultex.data.remote.dto.JsonRpcRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Polls PENDING transactions for all chains and updates status in Room.
 * Called from DashboardViewModel on each load.
 */
@Singleton
class TxStatusRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    @Named("eth") private val ethRpc: EvmRpcApi,
    @Named("bnb") private val bnbRpc: EvmRpcApi,
    private val btcApi: BitcoinApi,
    private val solanaApi: SolanaRpcApi,
    private val tronApi: TronApi
) {
    suspend fun refreshPendingStatuses() = withContext(Dispatchers.IO) {
        val pending = transactionDao.observeAll().first().filter { it.status == "PENDING" }

        for (tx in pending) {
            try {
                when (tx.blockchain) {
                    "ETH", "SWAP" -> checkEvm(ethRpc, tx.hash)?.let { transactionDao.updateStatus(tx.hash, it, 1) }
                    "BNB"         -> checkEvm(bnbRpc, tx.hash)?.let { transactionDao.updateStatus(tx.hash, it, 1) }
                    "BTC"         -> checkBtc(tx.hash)?.let { transactionDao.updateStatus(tx.hash, it, 1) }
                    "SOL"         -> checkSol(tx.hash)?.let { transactionDao.updateStatus(tx.hash, it, 1) }
                    "TRX"         -> checkTrx(tx.hash)?.let { transactionDao.updateStatus(tx.hash, it, 1) }
                }
            } catch (_: Exception) { /* leave PENDING, retry next time */ }
        }
    }

    // ── EVM (ETH / BNB) — eth_getTransactionReceipt ──────────────────────────
    private suspend fun checkEvm(rpc: EvmRpcApi, hash: String): String? {
        val res = rpc.rpcCall(JsonRpcRequest("eth_getTransactionReceipt", listOf(hash)))
        val receipt = res.result as? Map<*, *> ?: return null
        val status = receipt["status"] as? String ?: return null
        return if (status == "0x1") "CONFIRMED" else "FAILED"
    }

    // ── BTC — Blockstream GET /tx/{txid} ─────────────────────────────────────
    private suspend fun checkBtc(hash: String): String? {
        val tx = btcApi.getTransaction(hash)
        return if (tx.status.confirmed) "CONFIRMED" else null
    }

    // ── SOL — getSignatureStatuses RPC ────────────────────────────────────────
    private suspend fun checkSol(hash: String): String? {
        val res = solanaApi.rpcCall(JsonRpcRequest(
            method = "getSignatureStatuses",
            params = listOf(listOf(hash), mapOf("searchTransactionHistory" to true))
        ))
        val valueList = (res.result as? Map<*, *>)?.get("value") as? List<*> ?: return null
        val statusMap = valueList.firstOrNull() as? Map<*, *> ?: return null
        if (statusMap["err"] != null) return "FAILED"
        return when (statusMap["confirmationStatus"] as? String) {
            "finalized", "confirmed" -> "CONFIRMED"
            else -> null
        }
    }

    // ── TRX — TronGrid GET /v1/transactions/{txid} ────────────────────────────
    private suspend fun checkTrx(hash: String): String? {
        val res = tronApi.getTransactionById(hash)
        val data = res.data.firstOrNull() ?: return null
        if (data.blockNumber == null) return null
        val contractRet = data.ret?.firstOrNull()?.get("contractRet") ?: return "CONFIRMED"
        return if (contractRet == "SUCCESS") "CONFIRMED" else "FAILED"
    }
}
