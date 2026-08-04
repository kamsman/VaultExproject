package com.vaultex.core.tx

import com.vaultex.data.remote.api.BitcoinApi
import com.vaultex.data.remote.api.EvmRpcApi
import com.vaultex.data.remote.api.SolanaRpcApi
import com.vaultex.data.remote.api.TronApi
import com.vaultex.data.remote.dto.JsonRpcRequest
import com.vaultex.data.remote.dto.TronTxInfoBody
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Vérifie l'état de confirmation d'une transaction sortante on-chain.
 * Sert au badge « ! » du dashboard et à l'écran « En attente X / Y ».
 */
@Singleton
class ConfirmationChecker @Inject constructor(
    @Named("eth") private val ethRpc: EvmRpcApi,
    @Named("bnb") private val bnbRpc: EvmRpcApi,
    private val tronApi: TronApi,
    private val solanaRpc: SolanaRpcApi,
    private val bitcoinApi: BitcoinApi
) {
    data class Status(
        val confirmations: Int,
        val target: Int,
        val confirmed: Boolean,
        val failed: Boolean = false
    )

    companion object {
        // Cibles de confirmation par chaîne (X / Y affiché à l'utilisateur).
        fun targetFor(chainKey: String): Int = when (chainKey) {
            "ETH" -> 12
            "BNB" -> 15
            "TRX" -> 19
            "BTC" -> 3
            "SOL" -> 32
            else -> 12
        }
    }

    suspend fun check(chainKey: String, hash: String): Status {
        val target = targetFor(chainKey)
        return try {
            when (chainKey) {
                "ETH" -> checkEvm(ethRpc, hash, target)
                "BNB" -> checkEvm(bnbRpc, hash, target)
                "TRX" -> checkTron(hash, target)
                "SOL" -> checkSol(hash, target)
                "BTC" -> checkBtc(hash, target)
                else -> Status(0, target, false)
            }
        } catch (_: Exception) {
            Status(0, target, false)
        }
    }

    private suspend fun checkEvm(rpc: EvmRpcApi, hash: String, target: Int): Status {
        val receiptRes = rpc.rpcCall(JsonRpcRequest("eth_getTransactionReceipt", mutableListOf(hash as Any)))
        @Suppress("UNCHECKED_CAST")
        val receipt = receiptRes.result as? Map<String, Any> ?: return Status(0, target, false)
        val statusHex = receipt["status"] as? String
        if (statusHex == "0x0") return Status(0, target, confirmed = false, failed = true)
        val blockHex = receipt["blockNumber"] as? String ?: return Status(0, target, false)
        val txBlock = BigInteger(blockHex.removePrefix("0x").ifEmpty { "0" }, 16)
        val nowRes = rpc.rpcCall(JsonRpcRequest("eth_blockNumber", mutableListOf()))
        val nowBlock = BigInteger((nowRes.result as? String ?: "0x0").removePrefix("0x").ifEmpty { "0" }, 16)
        val conf = (nowBlock - txBlock + BigInteger.ONE).toInt().coerceAtLeast(1)
        return Status(conf.coerceAtMost(target), target, conf >= target)
    }

    private suspend fun checkTron(hash: String, target: Int): Status {
        val info = tronApi.getTransactionInfoById(TronTxInfoBody(hash))
        // Objet vide => pas encore inclus dans un bloc.
        val blockEl = info.get("blockNumber")
        if (blockEl == null || blockEl.isJsonNull) return Status(0, target, false)
        // Échec d'exécution d'un contrat (REVERT / OUT_OF_ENERGY).
        val receipt = info.getAsJsonObject("receipt")
        val result = receipt?.get("result")?.takeIf { !it.isJsonNull }?.asString
        if (result != null && result != "SUCCESS") return Status(0, target, confirmed = false, failed = true)
        val txBlock = blockEl.asLong
        val now = try {
            tronApi.getNowBlock().getAsJsonObject("block_header")
                ?.getAsJsonObject("raw_data")?.get("number")?.asLong ?: txBlock
        } catch (_: Exception) { txBlock }
        val conf = (now - txBlock + 1).toInt().coerceAtLeast(1)
        return Status(conf.coerceAtMost(target), target, conf >= target)
    }

    private suspend fun checkSol(hash: String, target: Int): Status {
        val res = solanaRpc.rpcCall(
            JsonRpcRequest("getSignatureStatuses",
                mutableListOf(listOf(hash) as Any, mapOf("searchTransactionHistory" to true) as Any))
        )
        @Suppress("UNCHECKED_CAST")
        val value = (res.result as? Map<String, Any>)?.get("value") as? List<Any?>
        val entry = value?.firstOrNull() as? Map<String, Any> ?: return Status(0, target, false)
        if (entry["err"] != null) return Status(0, target, confirmed = false, failed = true)
        return when (entry["confirmationStatus"] as? String) {
            "finalized" -> Status(target, target, true)
            "confirmed" -> Status((target * 2) / 3, target, false)
            "processed" -> Status(1, target, false)
            else -> Status(0, target, false)
        }
    }

    private suspend fun checkBtc(hash: String, target: Int): Status {
        val tx = bitcoinApi.getTransaction(hash)
        return if (tx.status.confirmed) Status(target, target, true) else Status(0, target, false)
    }
}
