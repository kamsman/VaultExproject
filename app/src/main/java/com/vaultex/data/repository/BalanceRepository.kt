package com.vaultex.data.repository

import com.vaultex.core.crypto.WalletManager
import com.vaultex.data.remote.api.BitcoinApi
import com.vaultex.data.remote.api.EvmRpcApi
import com.vaultex.data.remote.api.SolanaRpcApi
import com.vaultex.data.remote.api.TronApi
import com.vaultex.data.remote.dto.JsonRpcRequest
import javax.inject.Inject
import javax.inject.Named

class BalanceRepository @Inject constructor(
    @Named("eth") private val ethApi: EvmRpcApi,
    @Named("bnb") private val bnbApi: EvmRpcApi,
    private val btcApi: BitcoinApi,
    private val solanaApi: SolanaRpcApi,
    private val tronApi: TronApi
) {

    data class Balances(
        val btc: Double = 0.0,
        val eth: Double = 0.0,
        val bnb: Double = 0.0,
        val sol: Double = 0.0,
        val trx: Double = 0.0
    )

    suspend fun fetchAll(addresses: WalletManager.WalletAddresses): Balances = Balances(
        btc = fetchBtc(addresses.btc),
        eth = fetchEvm(ethApi, addresses.eth),
        bnb = fetchEvm(bnbApi, addresses.bnb),
        sol = fetchSolana(addresses.sol),
        trx = fetchTron(addresses.trx)
    )

    private suspend fun fetchBtc(address: String): Double = try {
        val info = btcApi.getAddressInfo(address)
        val sats = info.chainStats.fundedSum - info.chainStats.spentSum
        sats / 100_000_000.0
    } catch (_: Exception) { 0.0 }

    private suspend fun fetchEvm(api: EvmRpcApi, address: String): Double = try {
        val req = JsonRpcRequest(method = "eth_getBalance", params = listOf(address, "latest"))
        val res = api.rpcCall(req)
        val hex = res.result as? String ?: return 0.0
        // hex = "0x1bc16d674ec80000" → wei → ETH
        java.math.BigInteger(hex.removePrefix("0x"), 16).toBigDecimal()
            .divide(java.math.BigDecimal("1000000000000000000")).toDouble()
    } catch (_: Exception) { 0.0 }

    private suspend fun fetchSolana(address: String): Double = try {
        val req = JsonRpcRequest(method = "getBalance", params = listOf(address))
        val res = solanaApi.rpcCall(req)
        // Gson désérialise result comme Map<String, Any> — value est un Double (JSON number)
        val resultMap = res.result as? Map<*, *>
        val lamports = (resultMap?.get("value") as? Double)?.toLong() ?: 0L
        lamports / 1_000_000_000.0
    } catch (_: Exception) { 0.0 }

    private suspend fun fetchTron(address: String): Double = try {
        val account = tronApi.getAccount(address)
        val sun = account.data.firstOrNull()?.balance ?: 0L
        sun / 1_000_000.0
    } catch (_: Exception) { 0.0 }
}
