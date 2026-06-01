package com.vaultex.data.repository

import com.vaultex.core.crypto.Base58
import com.vaultex.core.crypto.Ed25519Utils
import com.vaultex.core.crypto.Slip10
import com.vaultex.core.security.SecureStorage
import com.vaultex.data.local.dao.TransactionDao
import com.vaultex.data.local.entity.TransactionEntity
import com.vaultex.data.remote.api.BitcoinApi
import com.vaultex.data.remote.api.EvmRpcApi
import com.vaultex.data.remote.api.SolanaRpcApi
import com.vaultex.data.remote.api.TronApi
import com.vaultex.data.remote.dto.JsonRpcRequest
import com.vaultex.data.remote.dto.TronBroadcastDto
import com.vaultex.data.remote.dto.TronCreateTransferRequest
import org.bitcoinj.core.*
import org.bitcoinj.crypto.ChildNumber
import org.bitcoinj.crypto.HDKeyDerivation
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.script.ScriptBuilder
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.web3j.crypto.*
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    @Named("eth") private val ethApi: EvmRpcApi,
    @Named("bnb") private val bnbApi: EvmRpcApi,
    private val btcApi: BitcoinApi,
    private val solanaApi: SolanaRpcApi,
    private val tronApi: TronApi,
    private val secureStorage: SecureStorage,
    private val transactionDao: TransactionDao
) {
    data class TxResult(val txHash: String, val fromAddress: String = "")

    suspend fun send(chain: String, toAddress: String, amountNative: Double): TxResult {
        val mnemonic = secureStorage.getMnemonic() ?: error("No wallet found")
        val seed = MnemonicUtils.generateSeed(mnemonic.trim(), "")
        val result = when (chain) {
            "ETH" -> sendEvm(ethApi, seed, chainId = 1L, toAddress, amountNative)
            "BNB" -> sendEvm(bnbApi, seed, chainId = 56L, toAddress, amountNative)
            "BTC" -> sendBtc(seed, toAddress, amountNative)
            "SOL" -> sendSolana(seed, toAddress, amountNative)
            "TRX" -> sendTron(seed, toAddress, amountNative)
            else  -> error("Unknown chain: $chain")
        }
        transactionDao.insert(TransactionEntity(
            hash = result.txHash,
            type = "SEND",
            blockchain = chain,
            fromAddress = result.fromAddress,
            toAddress = toAddress,
            amount = "%.8f".format(amountNative),
            tokenSymbol = chain,
            fee = "",
            status = "PENDING",
            timestamp = System.currentTimeMillis(),
            confirmations = 0,
            blockNumber = null
        ))
        return result
    }

    // ── ETH / BNB ────────────────────────────────────────────────────────────
    private suspend fun sendEvm(
        api: EvmRpcApi, seed: ByteArray, chainId: Long,
        to: String, amount: Double
    ): TxResult {
        val master = Bip32ECKeyPair.generateKeyPair(seed)
        val path = intArrayOf(
            44 or Bip32ECKeyPair.HARDENED_BIT,
            60 or Bip32ECKeyPair.HARDENED_BIT,
            0  or Bip32ECKeyPair.HARDENED_BIT, 0, 0
        )
        val credentials = Credentials.create(Bip32ECKeyPair.deriveKeyPair(master, path))

        val nonceRes = api.rpcCall(JsonRpcRequest("eth_getTransactionCount", listOf(credentials.address, "latest")))
        val nonce = BigInteger((nonceRes.result as String).removePrefix("0x"), 16)

        val gasPriceRes = api.rpcCall(JsonRpcRequest("eth_gasPrice", emptyList()))
        val gasPrice = BigInteger((gasPriceRes.result as String).removePrefix("0x"), 16)

        val valueWei = BigDecimal(amount)
            .multiply(BigDecimal("1000000000000000000")).toBigIntegerExact()

        val rawTx = RawTransaction.createEtherTransaction(
            nonce, gasPrice, BigInteger.valueOf(21_000L), to, valueWei
        )
        val signed = Numeric.toHexString(TransactionEncoder.signMessage(rawTx, chainId, credentials))

        val res = api.rpcCall(JsonRpcRequest("eth_sendRawTransaction", listOf(signed)))
        val txHash = res.result as? String ?: error(res.error?.message ?: "Broadcast failed")
        return TxResult(txHash, credentials.address)
    }

    // ── BTC ──────────────────────────────────────────────────────────────────
    private suspend fun sendBtc(seed: ByteArray, toAddress: String, amount: Double): TxResult {
        val params = MainNetParams.get()
        val path = listOf(
            ChildNumber(44, true), ChildNumber(0, true),
            ChildNumber(0, true), ChildNumber(0, false), ChildNumber(0, false)
        )
        val hdKey = path.fold(HDKeyDerivation.createMasterPrivateKey(seed)) { k, c ->
            HDKeyDerivation.deriveChildKey(k, c)
        }
        val ecKey = ECKey.fromPrivate(hdKey.privKeyBytes)
        val fromAddress = LegacyAddress.fromKey(params, ecKey)

        val utxos = btcApi.getUtxos(fromAddress.toString()).filter { it.status.confirmed }
        val feeEstimates = btcApi.getFeeEstimates()
        val feePerByte = (feeEstimates["3"] ?: feeEstimates.values.firstOrNull() ?: 10.0).toLong()

        val amountSats = (amount * 100_000_000).toLong()
        val feeSats = feePerByte * 250L  // ~250 vbytes for P2PKH 1-input 2-output

        var inputTotal = 0L
        val selectedUtxos = mutableListOf<com.vaultex.data.remote.dto.BlockstreamUtxoDto>()
        for (utxo in utxos.sortedByDescending { it.value }) {
            selectedUtxos.add(utxo)
            inputTotal += utxo.value
            if (inputTotal >= amountSats + feeSats) break
        }
        if (inputTotal < amountSats + feeSats) error("Solde BTC insuffisant")

        val tx = Transaction(params)
        val inputScript = ScriptBuilder.createOutputScript(fromAddress)

        selectedUtxos.forEach { utxo ->
            val outPoint = TransactionOutPoint(params, utxo.vout.toLong(), Sha256Hash.wrap(utxo.txid))
            tx.addInput(TransactionInput(params, tx, ByteArray(0), outPoint, Coin.valueOf(utxo.value)))
        }

        tx.addOutput(Coin.valueOf(amountSats), Address.fromString(params, toAddress))

        val change = inputTotal - amountSats - feeSats
        if (change > 546L) tx.addOutput(Coin.valueOf(change), fromAddress)

        tx.inputs.forEachIndexed { i, input ->
            val sig = tx.calculateSignature(i, ecKey, inputScript, Transaction.SigHash.ALL, false)
            input.scriptSig = ScriptBuilder.createInputScript(sig, ecKey)
        }

        val rawHex = tx.bitcoinSerialize().toHex()
        val txHash = btcApi.broadcastTx(rawHex)
        return TxResult(txHash.trim().removeSurrounding("\""), fromAddress.toString())
    }

    // ── SOLANA ───────────────────────────────────────────────────────────────
    private suspend fun sendSolana(seed: ByteArray, toAddress: String, amount: Double): TxResult {
        val path = intArrayOf(
            44 or Int.MIN_VALUE, 501 or Int.MIN_VALUE,
            0  or Int.MIN_VALUE, 0 or Int.MIN_VALUE
        )
        val derived = Slip10.deriveEd25519Key(seed, path)
        val pubKeyBytes = Ed25519Utils.publicKeyFromPrivate(derived.privateKey)

        // Fetch recent blockhash
        val bhRes = solanaApi.rpcCall(JsonRpcRequest(
            method = "getLatestBlockhash",
            params = listOf(mapOf("commitment" to "finalized"))
        ))
        val bhMap = (bhRes.result as? Map<*, *>)?.get("value") as? Map<*, *>
            ?: error("Cannot get blockhash")
        val blockhashStr = bhMap["blockhash"] as? String ?: error("No blockhash")
        val blockhashBytes = Base58.decode(blockhashStr)

        val toPubKey = Base58.decode(toAddress)
        val systemProgram = ByteArray(32)  // 11111...11

        val lamports = (amount * 1_000_000_000.0).toLong()

        // Build message
        val message = buildSolanaMessage(pubKeyBytes, toPubKey, systemProgram, blockhashBytes, lamports)

        // Sign message with ed25519
        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(derived.privateKey, 0))
        signer.update(message, 0, message.size)
        val signature = signer.generateSignature()  // 64 bytes

        // Build transaction: [1][signature 64 bytes][message]
        val txBytes = ByteArray(1 + 64 + message.size)
        txBytes[0] = 1
        System.arraycopy(signature, 0, txBytes, 1, 64)
        System.arraycopy(message, 0, txBytes, 65, message.size)

        val txBase64 = android.util.Base64.encodeToString(txBytes, android.util.Base64.NO_WRAP)

        val sendRes = solanaApi.rpcCall(JsonRpcRequest(
            method = "sendTransaction",
            params = listOf(txBase64, mapOf("encoding" to "base64"))
        ))
        val txHash = sendRes.result as? String ?: error(sendRes.error?.message ?: "Solana broadcast failed")
        return TxResult(txHash, Base58.encode(pubKeyBytes))
    }

    private fun buildSolanaMessage(
        from: ByteArray, to: ByteArray, systemProgram: ByteArray,
        blockhash: ByteArray, lamports: Long
    ): ByteArray {
        // Header: numRequired=1, numReadonlySigned=0, numReadonlyUnsigned=1
        val header = byteArrayOf(1, 0, 1)

        // Accounts: [from, to, systemProgram]
        val accounts = ByteArray(1 + 3 * 32)
        accounts[0] = 3  // compact-u16 count = 3
        System.arraycopy(from, 0, accounts, 1, 32)
        System.arraycopy(to, 0, accounts, 33, 32)
        System.arraycopy(systemProgram, 0, accounts, 65, 32)

        // Instruction data: type=2 (transfer) u32 LE + lamports u64 LE
        val data = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(2).putLong(lamports).array()

        // Instruction: programIdIndex=2, accounts=[0,1], data
        val instruction = byteArrayOf(
            2,          // programIdIndex
            2, 0, 1,    // accounts length=2, indices [0,1]
            12          // data length
        ) + data

        // Instructions section: count=1 + instruction
        val instructions = byteArrayOf(1) + instruction

        return header + accounts + blockhash + instructions
    }

    // ── TRON ─────────────────────────────────────────────────────────────────
    private suspend fun sendTron(seed: ByteArray, toAddress: String, amount: Double): TxResult {
        val master = Bip32ECKeyPair.generateKeyPair(seed)
        val path = intArrayOf(
            44 or Bip32ECKeyPair.HARDENED_BIT,
            195 or Bip32ECKeyPair.HARDENED_BIT,
            0   or Bip32ECKeyPair.HARDENED_BIT, 0, 0
        )
        val keyPair = Bip32ECKeyPair.deriveKeyPair(master, path)
        val credentials = Credentials.create(keyPair)

        // Get Tron address from credentials (EVM hex → Tron Base58Check)
        val addrBytes = Numeric.hexStringToByteArray(credentials.address.removePrefix("0x"))
        val tronFrom = toTronBase58(addrBytes)

        val sunAmount = (amount * 1_000_000).toLong()

        // Ask TronGrid to create unsigned transaction
        val unsignedTx = tronApi.createTransfer(
            TronCreateTransferRequest(
                owner_address = tronFrom,
                to_address = toAddress,
                amount = sunAmount
            )
        )

        // txID = SHA256(raw_data_bytes) — sign it with secp256k1
        val rawDataBytes = Numeric.hexStringToByteArray(unsignedTx.raw_data_hex)
        val txId = MessageDigest.getInstance("SHA-256").digest(rawDataBytes)

        val signatureData = Sign.signMessage(txId, keyPair.ecKeyPair, false)
        val sigHex = Numeric.toHexString(signatureData.r).removePrefix("0x") +
                     Numeric.toHexString(signatureData.s).removePrefix("0x") +
                     "%02x".format(signatureData.v[0].toInt() - 27)

        val result = tronApi.broadcast(
            TronBroadcastDto(raw_data_hex = unsignedTx.raw_data_hex, signature = listOf(sigHex))
        )
        if (result.result != true) error(result.message ?: "Tron broadcast failed")
        return TxResult(result.txid ?: unsignedTx.txID, tronFrom)
    }

    private fun toTronBase58(addrBytes: ByteArray): String {
        val raw = ByteArray(21).also {
            it[0] = 0x41.toByte()
            System.arraycopy(addrBytes, 0, it, 1, 20)
        }
        val checksum = sha256d(raw).copyOfRange(0, 4)
        return Base58.encode(raw + checksum)
    }

    private fun sha256d(data: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(md.digest(data))
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
