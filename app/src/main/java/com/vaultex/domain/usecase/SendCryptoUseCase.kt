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
import com.vaultex.core.validation.AddressValidator
import com.vaultex.data.remote.api.BitcoinApi
import com.vaultex.data.remote.api.EvmRpcApi
import com.vaultex.data.remote.api.SolanaRpcApi
import com.vaultex.data.remote.api.TronApi
import com.vaultex.data.remote.dto.JsonRpcRequest
import com.vaultex.data.remote.dto.TronBroadcastDto
import com.vaultex.data.remote.dto.TronCreateTxBody
import com.vaultex.data.remote.dto.TronTriggerSmartContractBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
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

    companion object {
        const val USDT_TRC20_CONTRACT = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"
        const val USDT_ERC20_CONTRACT = "0xdAC17F958D2ee523a2206206994597C13D831ec7"
        const val USDT_BEP20_CONTRACT = "0x55d398326f99059fF775485246999027B3197955"
    }

    /**
     * Point d'entrée UNIQUE d'un envoi : convertit le montant saisi (unité
     * humaine) selon la chaîne puis appelle le bon envoi. Utilisé à la fois
     * par l'UI (envoi direct en ligne) et par la file hors-ligne
     * (PendingSendWorker), afin que les deux chemins soient identiques.
     */
    suspend fun sendByChain(chain: String, toAddress: String, amount: String): Result {
        return when (chain) {
            "ETH", "BNB" -> {
                val chainId = if (chain == "ETH") 1L else 56L
                val amountWei = try {
                    BigDecimal(amount).multiply(BigDecimal("1000000000000000000")).toBigInteger()
                } catch (_: Exception) { return Result.Error("Montant invalide") }
                sendEvm(toAddress = toAddress, amountWei = amountWei, chainId = chainId)
            }
            "BTC" -> {
                val amountSatoshi = try {
                    BigDecimal(amount).multiply(BigDecimal("100000000")).toLong()
                } catch (_: Exception) { return Result.Error("Montant invalide") }
                sendBtc(toAddress = toAddress, amountSatoshi = amountSatoshi)
            }
            "TRX" -> {
                val amountSun = try {
                    BigDecimal(amount).multiply(BigDecimal("1000000")).toLong()
                } catch (_: Exception) { return Result.Error("Montant invalide") }
                sendTrx(toAddress = toAddress, amountSun = amountSun)
            }
            "SOL" -> {
                val lamports = try {
                    BigDecimal(amount).multiply(BigDecimal("1000000000")).toLong()
                } catch (_: Exception) { return Result.Error("Montant invalide") }
                sendSol(toAddress = toAddress, lamports = lamports)
            }
            "USDT" -> {
                val amountUsdt = amount.toDoubleOrNull() ?: return Result.Error("Montant invalide")
                sendUsdtTrc20(toAddress = toAddress, amountUsdt = amountUsdt)
            }
            "USDT-ETH" -> {
                val amountWei = try {
                    BigDecimal(amount).multiply(BigDecimal("1000000")).toBigInteger() // 6 décimales
                } catch (_: Exception) { return Result.Error("Montant invalide") }
                sendErc20(toAddress = toAddress, amountWei = amountWei,
                    contractAddress = USDT_ERC20_CONTRACT, chainId = 1L)
            }
            "USDT-BNB" -> {
                val amountWei = try {
                    BigDecimal(amount).multiply(BigDecimal("1000000000000000000")).toBigInteger() // 18 décimales
                } catch (_: Exception) { return Result.Error("Montant invalide") }
                sendErc20(toAddress = toAddress, amountWei = amountWei,
                    contractAddress = USDT_BEP20_CONTRACT, chainId = 56L)
            }
            else -> Result.Error("Chain non supportée")
        }
    }

    // ─── EVM (ETH / BNB) ─────────────────────────────────────────────

    suspend fun sendEvm(
        toAddress: String,
        amountWei: BigInteger,
        chainId: Long,
        coinType: Int = 60
    ): Result {
        if (!AddressValidator.isValidEvm(toAddress)) return Result.Error("Adresse ETH/BNB invalide (0x + 40 hex requis)")
        val mnemonic = secureStorage.getMnemonic() ?: return Result.Error("Wallet non trouvé")
        val passphrase = secureStorage.getPassphrase()
        return try {
            val rpc = if (chainId == 1L) ethRpc else bnbRpc
            val fromAddress = WalletManager.deriveAddresses(mnemonic, passphrase).eth

            val nonceRes = rpc.rpcCall(JsonRpcRequest("eth_getTransactionCount",
                mutableListOf(fromAddress as Any, "pending" as Any)))
            val nonce = BigInteger((nonceRes.result as? String ?: "0x0")
                .removePrefix("0x").ifEmpty { "0" }, 16)

            val estimateReq = JsonRpcRequest("eth_estimateGas", mutableListOf(
                mapOf("from" to fromAddress, "to" to toAddress,
                    "value" to "0x${amountWei.toString(16)}") as Any))
            val gasLimit = try {
                BigInteger((rpc.rpcCall(estimateReq).result as? String ?: "0x5208")
                    .removePrefix("0x"), 16)
                    .multiply(BigInteger.valueOf(120)).divide(BigInteger.valueOf(100))
            } catch (_: Exception) { BigInteger.valueOf(25_200L) }

            val signed = if (chainId == 1L) {
                // Ethereum mainnet: EIP-1559 (type-2) — accurate base fee + tip
                val (maxPriority, maxFee) = fetchEip1559Fees(rpc)
                evmTx.signTransactionEip1559(mnemonic, passphrase, toAddress, amountWei,
                    maxPriority, maxFee, gasLimit, nonce, chainId, coinType)
            } else {
                // BSC and others: legacy (type-0)
                val gasPrice = fetchLegacyGasPrice(rpc, default = 5_000_000_000L)
                evmTx.signTransaction(mnemonic, passphrase, toAddress, amountWei,
                    gasPrice, gasLimit, nonce, chainId, coinType)
            }

            val broadcastRes = rpc.rpcCall(JsonRpcRequest("eth_sendRawTransaction",
                mutableListOf(signed as Any)))
            if (broadcastRes.error != null) Result.Error(broadcastRes.error.message)
            else Result.Success(broadcastRes.result as? String ?: signed)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Erreur transaction EVM")
        }
    }

    // ─── ERC-20 (USDT on ETH / BNB) ──────────────────────────────────

    suspend fun sendErc20(
        toAddress: String,
        amountWei: BigInteger,
        contractAddress: String,
        chainId: Long
    ): Result {
        if (!AddressValidator.isValidEvm(toAddress)) return Result.Error("Adresse ETH/BNB invalide (0x + 40 hex requis)")
        val mnemonic = secureStorage.getMnemonic() ?: return Result.Error("Wallet non trouvé")
        val passphrase = secureStorage.getPassphrase()
        return try {
            val rpc = if (chainId == 1L) ethRpc else bnbRpc
            val fromAddress = WalletManager.deriveAddresses(mnemonic, passphrase).eth

            val nonceRes = rpc.rpcCall(JsonRpcRequest("eth_getTransactionCount",
                mutableListOf(fromAddress as Any, "pending" as Any)))
            val nonce = BigInteger((nonceRes.result as? String ?: "0x0")
                .removePrefix("0x").ifEmpty { "0" }, 16)

            val paddedTo  = toAddress.removePrefix("0x").padStart(64, '0')
            val paddedAmt = amountWei.toString(16).padStart(64, '0')
            val callData  = "0xa9059cbb$paddedTo$paddedAmt"
            val estimateReq = JsonRpcRequest("eth_estimateGas", mutableListOf(
                mapOf("from" to fromAddress, "to" to contractAddress, "data" to callData) as Any))
            val gasLimit = try {
                BigInteger((rpc.rpcCall(estimateReq).result as? String ?: "0xEA60")
                    .removePrefix("0x"), 16)
                    .multiply(BigInteger.valueOf(120)).divide(BigInteger.valueOf(100))
            } catch (_: Exception) { BigInteger.valueOf(72_000L) }

            val signed = if (chainId == 1L) {
                val (maxPriority, maxFee) = fetchEip1559Fees(rpc)
                evmTx.signErc20TransferEip1559(mnemonic, passphrase, contractAddress, toAddress,
                    amountWei, maxPriority, maxFee, gasLimit, nonce, chainId)
            } else {
                val gasPrice = fetchLegacyGasPrice(rpc, default = 5_000_000_000L)
                evmTx.signErc20Transfer(mnemonic, passphrase, contractAddress, toAddress,
                    amountWei, gasPrice, gasLimit, nonce, chainId)
            }

            val broadcastRes = rpc.rpcCall(JsonRpcRequest("eth_sendRawTransaction",
                mutableListOf(signed as Any)))
            if (broadcastRes.error != null) Result.Error(broadcastRes.error.message)
            else Result.Success(broadcastRes.result as? String ?: signed)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Erreur transaction ERC-20")
        }
    }

    // ─── BITCOIN ─────────────────────────────────────────────────────

    suspend fun sendBtc(toAddress: String, amountSatoshi: Long): Result {
        if (!AddressValidator.isValidBtc(toAddress)) return Result.Error("Adresse BTC invalide")
        val mnemonic = secureStorage.getMnemonic() ?: return Result.Error("Wallet non trouvé")
        val passphrase = secureStorage.getPassphrase()
        return try {
            val btcAddress = WalletManager.deriveAddresses(mnemonic, passphrase).btc
            val utxosDto = bitcoinApi.getUtxos(btcAddress)
            val feeEstimates = bitcoinApi.getFeeEstimates()
            val satPerByte = (feeEstimates["6"] ?: feeEstimates["3"] ?: feeEstimates["1"] ?: 10.0).toLong()

            val confirmedUtxos = utxosDto.filter { it.status.confirmed }.sortedByDescending { it.value }
                .map { Utxo(txHash = it.txid, outputIndex = it.vout, valueSatoshi = it.value) }
            if (confirmedUtxos.isEmpty()) return Result.Error("Aucun UTXO confirmé disponible")

            val inputCount = confirmedUtxos.size.coerceAtMost(10)
            // P2WPKH virtual size: ~68 vbytes/input (41 non-witness + 108 witness / 4)
            val feeSatoshi = (11 + 68 * inputCount + 31 * 2).toLong() * satPerByte

            val signed = btcTx.signTransaction(mnemonic, passphrase, toAddress, amountSatoshi, feeSatoshi, confirmedUtxos)
            val signedHex = signed.joinToString("") { "%02x".format(it) }
            val txHash = bitcoinApi.broadcastTx(signedHex.toRequestBody("text/plain".toMediaType()))
            Result.Success(txHash)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Erreur transaction BTC")
        }
    }

    // ─── TRON (TRX natif) — flux : Créer → Signer → Broadcast → Hash ──

    suspend fun sendTrx(toAddress: String, amountSun: Long): Result {
        if (!AddressValidator.isValidTron(toAddress)) return Result.Error("Adresse TRX invalide (T + 34 caractères + checksum)")
        val mnemonic = secureStorage.getMnemonic() ?: return Result.Error("Wallet non trouvé")
        val passphrase = secureStorage.getPassphrase()
        return try {
            // Étape 1 — Dériver l'adresse owner en hex (format attendu par TronGrid)
            val ownerHex = tronAddrToHex(tronTx.deriveAddress(mnemonic, passphrase))
            val toHex    = tronAddrToHex(toAddress)

            // Étape 2 — Créer la transaction non signée via TronGrid
            val rawTx = tronApi.createTransaction(
                TronCreateTxBody(owner_address = ownerHex, to_address = toHex, amount = amountSun)
            )
            val rawDataHex = rawTx.rawDataHex ?: return Result.Error("Création transaction TRX échouée")

            // Étape 3 — Signer : SHA3(rawDataHex) → secp256k1 → r+s+v hex
            val signature = tronTx.signRawTransaction(mnemonic, passphrase, rawDataHex)

            // Étape 4 — Broadcast sur le réseau TRON, récupérer le txID
            val broadcast = tronApi.broadcast(TronBroadcastDto(raw_data_hex = rawDataHex, signature = listOf(signature)))
            if (broadcast.result == true) Result.Success(broadcast.txid ?: rawTx.txID)
            else Result.Error(broadcast.message ?: "Broadcast TRX échoué")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Erreur transaction TRX")
        }
    }

    // ─── USDT TRC20 — flux : Trigger → Signer → Broadcast → Hash ────

    suspend fun sendUsdtTrc20(toAddress: String, amountUsdt: Double): Result {
        if (!AddressValidator.isValidTron(toAddress)) return Result.Error("Adresse TRX invalide (T + 34 caractères + checksum)")
        val mnemonic = secureStorage.getMnemonic() ?: return Result.Error("Wallet non trouvé")
        val passphrase = secureStorage.getPassphrase()
        return try {
            // Étape 1 — Préparer les paramètres hex + ABI-encode transfer(address,uint256)
            val ownerHex    = tronAddrToHex(tronTx.deriveAddress(mnemonic, passphrase))
            val contractHex = tronAddrToHex(USDT_TRC20_CONTRACT)
            val amountMicro = (amountUsdt * 1_000_000).toLong()
            val parameter   = buildTrc20Param(toAddress, amountMicro)

            // Étape 2 — Déclencher le smart contract (génère la tx non signée)
            val triggerRes = tronApi.triggerSmartContract(
                TronTriggerSmartContractBody(
                    owner_address     = ownerHex,
                    contract_address  = contractHex,
                    function_selector = "transfer(address,uint256)",
                    parameter         = parameter,
                    fee_limit         = 10_000_000
                )
            )
            if (triggerRes.result.result != true)
                return Result.Error(triggerRes.result.message ?: "Création TRC20 échouée")

            // Étape 3 — Signer : SHA3(rawDataHex) → secp256k1 → r+s+v hex
            val rawTx      = triggerRes.transaction ?: return Result.Error("Transaction TRC20 vide")
            val rawDataHex = rawTx.rawDataHex       ?: return Result.Error("raw_data_hex absent")
            val signature  = tronTx.signRawTransaction(mnemonic, passphrase, rawDataHex)

            // Étape 4 — Broadcast sur le réseau TRON, récupérer le txID
            val broadcast = tronApi.broadcast(TronBroadcastDto(raw_data_hex = rawDataHex, signature = listOf(signature)))
            if (broadcast.result == true) Result.Success(broadcast.txid ?: rawTx.txID)
            else Result.Error(broadcast.message ?: "Broadcast USDT échoué")
        } catch (e: Exception) {
            Result.Error(e.message ?: "Erreur USDT TRC20")
        }
    }

    // ─── SOLANA ──────────────────────────────────────────────────────

    suspend fun sendSol(toAddress: String, lamports: Long): Result {
        if (!AddressValidator.isValidSolana(toAddress)) return Result.Error("Adresse SOL invalide")
        val mnemonic = secureStorage.getMnemonic() ?: return Result.Error("Wallet non trouvé")
        val passphrase = secureStorage.getPassphrase()
        return try {
            val fromPubKey = Base58.decode(WalletManager.deriveAddresses(mnemonic, passphrase).sol)
            val toPubKey = Base58.decode(toAddress)

            val bhRes = solanaRpc.rpcCall(
                JsonRpcRequest("getLatestBlockhash", mutableListOf(mapOf("commitment" to "finalized") as Any))
            )
            @Suppress("UNCHECKED_CAST")
            val bhValue = (bhRes.result as? Map<String, Any>)?.get("value") as? Map<String, Any>
            val blockhashB58 = bhValue?.get("blockhash") as? String
                ?: return Result.Error("Blockhash Solana introuvable")
            val recentBlockhash = Base58.decode(blockhashB58)

            val message = buildSolTransferMessage(fromPubKey, toPubKey, recentBlockhash, lamports)
            val sig = solTx.signTransaction(mnemonic, passphrase, message)

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

    // ─── Helpers ─────────────────────────────────────────────────────

    /**
     * EIP-1559 fees: returns (maxPriorityFeePerGas, maxFeePerGas).
     * maxFeePerGas = 2 × baseFee + maxPriorityFeePerGas (canonical formula).
     */
    private suspend fun fetchEip1559Fees(rpc: EvmRpcApi): Pair<BigInteger, BigInteger> {
        val priorityRes = rpc.rpcCall(JsonRpcRequest("eth_maxPriorityFeePerGas", mutableListOf()))
        val maxPriority = try {
            BigInteger((priorityRes.result as? String ?: "0x3B9ACA00").removePrefix("0x"), 16)
        } catch (_: Exception) { BigInteger.valueOf(1_000_000_000L) }  // 1 gwei fallback

        val blockRes = rpc.rpcCall(JsonRpcRequest("eth_getBlockByNumber",
            mutableListOf("latest" as Any, false as Any)))
        @Suppress("UNCHECKED_CAST")
        val baseFeeHex = (blockRes.result as? Map<String, Any>)
            ?.get("baseFeePerGas") as? String ?: "0x0"
        val baseFee = BigInteger(baseFeeHex.removePrefix("0x").ifEmpty { "0" }, 16)
        val maxFee = baseFee.multiply(BigInteger.TWO).add(maxPriority)
        return Pair(maxPriority, maxFee)
    }

    private suspend fun fetchLegacyGasPrice(rpc: EvmRpcApi, default: Long): BigInteger = try {
        BigInteger((rpc.rpcCall(JsonRpcRequest("eth_gasPrice", mutableListOf()))
            .result as? String ?: "0x0").removePrefix("0x"), 16)
    } catch (_: Exception) { BigInteger.valueOf(default) }

    /** Convertit une adresse Tron Base58Check en hex sans 0x (ex: "41XXXX...") */
    private fun tronAddrToHex(address: String): String =
        Base58.decode(address).copyOfRange(0, 21).joinToString("") { "%02x".format(it) }

    /** ABI encode pour transfer(address,uint256) dans TronGrid */
    private fun buildTrc20Param(toAddress: String, amountMicro: Long): String {
        val decoded = Base58.decode(toAddress)
        val addrHex = decoded.copyOfRange(1, 21).joinToString("") { "%02x".format(it) }
        return addrHex.padStart(64, '0') + amountMicro.toString(16).padStart(64, '0')
    }

    /**
     * Sérialise un message Solana « legacy » pour un transfert natif SOL.
     *
     * IMPORTANT (m-04) : sérialiseur écrit à la main, volontairement limité
     * au seul cas SystemProgram::Transfer. Les compteurs sont encodés sur un
     * octet, ce qui est valide tant que toutes les valeurs restent < 128
     * (compact-u16 / ShortVec) — ce qui est garanti ici (3 comptes, 1
     * instruction, 12 octets de data). Les gardes ci-dessous échouent vite
     * plutôt que de produire un message mal formé mais signé.
     *
     * Disposition exacte des octets :
     *   [header]      numRequiredSignatures=1, numReadonlySigned=0, numReadonlyUnsigned=1
     *   [comptes]     count=3, then from(signer,writable) | to(writable) | systemProgram(readonly)
     *   [blockhash]   32 octets
     *   [instructions] count=1
     *     programIdIndex=2 (systemProgram)
     *     accountCount=2, indices=[0(from), 1(to)]
     *     dataLen=12
     *     data = instruction(=2, Transfer, u32 LE) + lamports(u64 LE)
     */
    private fun buildSolTransferMessage(
        from: ByteArray, to: ByteArray, recentBlockhash: ByteArray, lamports: Long
    ): ByteArray {
        require(from.size == 32) { "Clé publique source Solana invalide (${from.size} octets)" }
        require(to.size == 32) { "Clé publique destination Solana invalide (${to.size} octets)" }
        require(recentBlockhash.size == 32) { "Blockhash Solana invalide (${recentBlockhash.size} octets)" }
        require(lamports >= 0) { "Montant lamports négatif" }

        val out = ByteArrayOutputStream()
        val systemProgram = ByteArray(32)
        out.write(1); out.write(0); out.write(1)
        out.write(3)
        out.write(from); out.write(to); out.write(systemProgram)
        out.write(recentBlockhash)
        out.write(1)
        out.write(2); out.write(2); out.write(0); out.write(1)
        out.write(12)
        out.write(2); out.write(0); out.write(0); out.write(0)
        for (i in 0 until 8) out.write((lamports ushr (i * 8)).toInt() and 0xFF)
        return out.toByteArray()
    }
}
