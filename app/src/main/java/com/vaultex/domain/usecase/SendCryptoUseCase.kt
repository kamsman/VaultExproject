package com.vaultex.domain.usecase

import android.util.Base64
import com.vaultex.core.crypto.Base58
import com.vaultex.core.crypto.WalletManager
import com.vaultex.core.security.SecureStorage
import com.vaultex.core.tx.BtcTransactionService
import com.vaultex.core.tx.EvmTransactionService
import com.vaultex.core.tx.SolanaTransactionService
import com.vaultex.core.tx.TronTransactionService
import com.vaultex.core.tx.Utxo
import com.vaultex.data.remote.api.BitcoinApi
import com.vaultex.data.remote.api.EvmRpcApi
import com.vaultex.data.remote.api.SolanaRpcApi
import com.vaultex.data.remote.api.TronApi
import com.vaultex.data.remote.dto.JsonRpcRequest
import com.vaultex.data.remote.dto.TronBroadcastDto
import com.vaultex.data.remote.dto.TronCreateTxBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Named

class SendCryptoUseCase @Inject constructor(
    private val secureStorage: SecureStorage,
    private val evmTx: EvmTransactionService,
    private val btcTx: BtcTransactionService,
    private val solTx: SolanaTransactionService,
    private val tronTx: TronTransactionService,
    @Named("eth") private val ethRpc: EvmRpcApi,
    @Named("bnb") private val bnbRpc: EvmRpcApi,
    private val bitcoinApi: BitcoinApi,
    private val solanaRpc: SolanaRpcApi,
    private val tronApi: TronApi
) {
    sealed class Result {
        data class Success(val txHash: String) : Result()
        data class Error(val message: String) : Result()
    }

    // ─── EVM (ETH / BNB) ─────────────────────────────────────────────

    suspend fun sendEvm(
        toAddress: String,
        amountWei: BigInteger,
        chainId: Long,
        coinType: Int = 60
    ): Result {
        val mnemonic = secureStorage.getMnemonic() ?: return Result.Error("Wallet non trouvé")
        return try {
            val rpc = if (chainId == 1L) ethRpc else bnbRpc
            val fromAddress = WalletManager.deriveAddresses(mnemonic).eth

            // Nonce réseau
            val nonceRes = rpc.rpcCall(
                JsonRpcRequest("eth_getTransactionCount", mutableListOf(fromAddress as Any, "pending" as Any))
            )
            val nonce = BigInteger(
                (nonceRes.result as? String ?: "0x0").removePrefix("0x").ifEmpty { "0" }, 16
            )

            // Gas price réseau
            val gpRes = rpc.rpcCall(JsonRpcRequest("eth_gasPrice", mutableListOf()))
            val gasPrice = try {
                BigInteger((gpRes.result as? String ?: "0x4A817C800").removePrefix("0x"), 16)
            } catch (_: Exception) { BigInteger.valueOf(20_000_000_000L) }

            // Estimation gas (+ 20% buffer)
            val estimateReq = JsonRpcRequest(
                "eth_estimateGas",
                mutableListOf(mapOf("from" to fromAddress, "to" to toAddress, "value" to "0x${amountWei.toString(16)}") as Any)
            )
            val glRes = rpc.rpcCall(estimateReq)
            val gasLimit = try {
                BigInteger((glRes.result as? String ?: "0x5208").removePrefix("0x"), 16)
                    .multiply(BigInteger.valueOf(120)).divide(BigInteger.valueOf(100))
            } catch (_: Exception) { BigInteger.valueOf(25_200L) } // 21000 + 20%

            val signed = evmTx.signTransaction(mnemonic, toAddress, amountWei, gasPrice, gasLimit, nonce, chainId, coinType)
            val broadcastRes = rpc.rpcCall(
                JsonRpcRequest("eth_sendRawTransaction", mutableListOf("0x$signed" as Any))
            )
            if (broadcastRes.error != null) Result.Error(broadcastRes.error.message)
            else Result.Success(broadcastRes.result as? String ?: signed)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Erreur transaction EVM")
        }
    }

    // ─── BITCOIN ─────────────────────────────────────────────────────

    suspend fun sendBtc(toAddress: String, amountSatoshi: Long): Result {
        val mnemonic = secureStorage.getMnemonic() ?: return Result.Error("Wallet non trouvé")
        return try {
            val btcAddress = WalletManager.deriveAddresses(mnemonic).btc

            val utxosDto = bitcoinApi.getUtxos(btcAddress)
            val feeEstimates = bitcoinApi.getFeeEstimates()
            val satPerByte = (feeEstimates["6"] ?: feeEstimates["3"] ?: feeEstimates["1"] ?: 10.0).toLong()

            val confirmedUtxos = utxosDto
                .filter { it.status.confirmed }
                .sortedByDescending { it.value }
                .map { Utxo(txHash = it.txid, outputIndex = it.vout, valueSatoshi = it.value) }

            if (confirmedUtxos.isEmpty()) return Result.Error("Aucun UTXO confirmé disponible")

            // Estimation taille tx: 10 + 148*inputs + 34*outputs
            val inputCount = confirmedUtxos.size.coerceAtMost(10)
            val estimatedBytes = (10 + 148 * inputCount + 34 * 2).toLong()
            val feeSatoshi = estimatedBytes * satPerByte

            val signed = btcTx.signTransaction(mnemonic, toAddress, amountSatoshi, feeSatoshi, confirmedUtxos)
            val signedHex = signed.joinToString("") { "%02x".format(it) }
            val txHash = bitcoinApi.broadcastTx(signedHex.toRequestBody("text/plain".toMediaType()))
            Result.Success(txHash)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Erreur transaction BTC")
        }
    }

    // ─── TRON ────────────────────────────────────────────────────────

    suspend fun sendTrx(toAddress: String, amountSun: Long): Result {
        val mnemonic = secureStorage.getMnemonic() ?: return Result.Error("Wallet non trouvé")
        return try {
            val ownerAddress = tronTx.deriveAddress(mnemonic)
            val rawTx = tronApi.createTransaction(
                TronCreateTxBody(owner_address = ownerAddress, to_address = toAddress, amount = amountSun)
            )
            val rawDataHex = rawTx.rawDataHex ?: return Result.Error("Création transaction TRX échouée")
            val signature = tronTx.signRawTransaction(mnemonic, rawDataHex)
            val result = tronApi.broadcast(TronBroadcastDto(raw_data_hex = rawDataHex, signature = listOf(signature)))
            if (result.result == true) Result.Success(result.txid ?: rawTx.txID)
            else Result.Error(result.message ?: "Broadcast TRX échoué")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Erreur transaction TRX")
        }
    }

    // ─── SOLANA ──────────────────────────────────────────────────────

    suspend fun sendSol(toAddress: String, lamports: Long): Result {
        val mnemonic = secureStorage.getMnemonic() ?: return Result.Error("Wallet non trouvé")
        return try {
            val fromPubKey = Base58.decode(WalletManager.deriveAddresses(mnemonic).sol)
            val toPubKey = Base58.decode(toAddress)

            // Blockhash récent
            val bhRes = solanaRpc.rpcCall(
                JsonRpcRequest("getLatestBlockhash", mutableListOf(mapOf("commitment" to "finalized") as Any))
            )
            @Suppress("UNCHECKED_CAST")
            val bhValue = (bhRes.result as? Map<String, Any>)?.get("value") as? Map<String, Any>
            val blockhashB58 = bhValue?.get("blockhash") as? String
                ?: return Result.Error("Blockhash Solana introuvable")
            val recentBlockhash = Base58.decode(blockhashB58)

            // Construire le message de transfert natif SOL
            val message = buildSolTransferMessage(fromPubKey, toPubKey, recentBlockhash, lamports)

            // Signer
            val sig = solTx.signTransaction(mnemonic, message)

            // Encoder la transaction : [compact(1)] + [sig 64B] + [message]
            val txBytes = ByteArray(1 + 64 + message.size)
            txBytes[0] = 1
            System.arraycopy(sig, 0, txBytes, 1, 64)
            System.arraycopy(message, 0, txBytes, 65, message.size)

            val txBase64 = Base64.encodeToString(txBytes, Base64.NO_WRAP)
            val sendRes = solanaRpc.rpcCall(
                JsonRpcRequest("sendTransaction", mutableListOf(txBase64 as Any, mapOf("encoding" to "base64") as Any))
            )
            if (sendRes.error != null) Result.Error(sendRes.error.message)
            else Result.Success(sendRes.result as? String ?: "unknown")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Erreur transaction SOL")
        }
    }

    // Message de transfert natif SOL (System Program Transfer)
    private fun buildSolTransferMessage(
        from: ByteArray, to: ByteArray, recentBlockhash: ByteArray, lamports: Long
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val systemProgram = ByteArray(32) // 0x00...00
        out.write(1); out.write(0); out.write(1) // header
        out.write(3)                              // 3 comptes
        out.write(from); out.write(to); out.write(systemProgram)
        out.write(recentBlockhash)
        out.write(1)  // 1 instruction
        out.write(2)  // program_id_index = 2 (System Program)
        out.write(2)  // 2 comptes impliqués
        out.write(0); out.write(1)  // from=0, to=1
        out.write(12) // data length = 12 bytes
        out.write(2); out.write(0); out.write(0); out.write(0) // Transfer = type 2 (LE u32)
        for (i in 0 until 8) out.write((lamports ushr (i * 8)).toInt() and 0xFF) // lamports LE u64
        return out.toByteArray()
    }
}
