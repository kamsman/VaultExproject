package com.vaultex.domain.usecase

import com.vaultex.core.crypto.WalletManager
import com.vaultex.core.security.SecureStorage
import com.vaultex.core.tx.BtcTransactionService
import com.vaultex.core.tx.EvmTransactionService
import com.vaultex.core.tx.Utxo
import com.vaultex.data.remote.api.BitcoinApi
import com.vaultex.data.remote.api.EvmRpcApi
import com.vaultex.data.remote.dto.JsonRpcRequest
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Named

class SendCryptoUseCase @Inject constructor(
    private val secureStorage: SecureStorage,
    private val evmTx: EvmTransactionService,
    private val btcTx: BtcTransactionService,
    @Named("eth") private val ethRpc: EvmRpcApi,
    @Named("bnb") private val bnbRpc: EvmRpcApi,
    private val bitcoinApi: BitcoinApi
) {
    sealed class Result {
        data class Success(val txHash: String) : Result()
        data class Error(val message: String) : Result()
    }

    suspend fun sendEvm(
        toAddress: String,
        amountWei: BigInteger,
        gasPrice: BigInteger,
        gasLimit: BigInteger,
        chainId: Long,
        coinType: Int = 60
    ): Result {
        val mnemonic = secureStorage.getMnemonic() ?: return Result.Error("Wallet non trouvé")
        return try {
            val rpc = if (chainId == 1L) ethRpc else bnbRpc
            val fromAddress = WalletManager.deriveAddresses(mnemonic).eth

            val nonceRes = rpc.rpcCall(
                JsonRpcRequest("eth_getTransactionCount", mutableListOf(fromAddress as Any, "pending" as Any))
            )
            val nonce = try {
                BigInteger((nonceRes.result as? String ?: "0x0").removePrefix("0x").ifEmpty { "0" }, 16)
            } catch (_: Exception) { BigInteger.ZERO }

            val signed = evmTx.signTransaction(mnemonic, toAddress, amountWei, gasPrice, gasLimit, nonce, chainId, coinType)
            val broadcastRes = rpc.rpcCall(
                JsonRpcRequest("eth_sendRawTransaction", mutableListOf("0x$signed" as Any))
            )
            if (broadcastRes.error != null) {
                Result.Error(broadcastRes.error.message)
            } else {
                Result.Success(broadcastRes.result as? String ?: signed)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Erreur de transaction")
        }
    }

    suspend fun sendBtc(
        toAddress: String,
        amountSatoshi: Long,
        feeSatoshi: Long,
        utxos: List<Utxo>
    ): Result {
        val mnemonic = secureStorage.getMnemonic() ?: return Result.Error("Wallet non trouvé")
        return try {
            val signed = btcTx.signTransaction(mnemonic, toAddress, amountSatoshi, feeSatoshi, utxos)
            val signedHex = signed.joinToString("") { "%02x".format(it) }
            val txHash = bitcoinApi.broadcastTx(signedHex)
            Result.Success(txHash)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Erreur de transaction BTC")
        }
    }
}
