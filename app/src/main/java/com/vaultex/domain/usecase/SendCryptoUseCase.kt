package com.vaultex.domain.usecase

import com.vaultex.core.security.SecureStorage
import com.vaultex.core.tx.EvmTransactionService
import com.vaultex.core.tx.BtcTransactionService
import com.vaultex.core.tx.Utxo
import java.math.BigInteger
import javax.inject.Inject

class SendCryptoUseCase @Inject constructor(
    private val secureStorage: SecureStorage,
    private val evmTx: EvmTransactionService,
    private val btcTx: BtcTransactionService
) {
    sealed class Result {
        data class Success(val signedTxHex: String) : Result()
        data class Error(val message: String) : Result()
    }

    fun sendEvm(
        toAddress: String,
        amountWei: BigInteger,
        gasPrice: BigInteger,
        gasLimit: BigInteger,
        nonce: BigInteger,
        chainId: Long,
        coinType: Int = 60
    ): Result {
        val mnemonic = secureStorage.getMnemonic() ?: return Result.Error("Wallet non trouvé")
        return try {
            val signed = evmTx.signTransaction(mnemonic, toAddress, amountWei, gasPrice, gasLimit, nonce, chainId, coinType)
            Result.Success(signed)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Erreur de signature")
        }
    }

    fun sendBtc(
        toAddress: String,
        amountSatoshi: Long,
        feeSatoshi: Long,
        utxos: List<Utxo>
    ): Result {
        val mnemonic = secureStorage.getMnemonic() ?: return Result.Error("Wallet non trouvé")
        return try {
            val signed = btcTx.signTransaction(mnemonic, toAddress, amountSatoshi, feeSatoshi, utxos)
            Result.Success(signed.joinToString("") { "%02x".format(it) })
        } catch (e: Exception) {
            Result.Error(e.message ?: "Erreur de signature BTC")
        }
    }
}
