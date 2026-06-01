package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.core.security.SecureStorage
import com.vaultex.data.local.dao.TransactionDao
import com.vaultex.data.local.entity.TransactionEntity
import com.vaultex.data.remote.api.EvmRpcApi
import com.vaultex.data.remote.api.OneInchApi
import com.vaultex.data.remote.dto.JsonRpcRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.web3j.crypto.Bip32ECKeyPair
import org.web3j.crypto.Credentials
import org.web3j.crypto.MnemonicUtils
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class SwapViewModel @Inject constructor(
    private val oneInchApi: OneInchApi,
    private val secureStorage: SecureStorage,
    @Named("eth") private val ethRpc: EvmRpcApi,
    @Named("bnb") private val bnbRpc: EvmRpcApi,
    private val transactionDao: TransactionDao
) : ViewModel() {

    sealed class UiState {
        object Idle : UiState()
        object LoadingQuote : UiState()
        data class QuoteReady(val toAmount: String, val toAmountFormatted: String) : UiState()
        object Swapping : UiState()
        data class Success(val txHash: String) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    // 1inch token contract addresses (native = 0xEEEE…)
    private val ethTokens = mapOf(
        "ETH"  to "0xEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE",
        "USDT" to "0xdAC17F958D2ee523a2206206994597C13D831ec7",
        "USDC" to "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
        "WBTC" to "0x2260FAC5E5542a773Aa44fBCfeDf7C193bc2C599"
    )
    private val bnbTokens = mapOf(
        "BNB"  to "0xEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE",
        "USDT" to "0x55d398326f99059fF775485246999027B3197955",
        "USDC" to "0x8AC76a51cc950d9822D68b83fE1Ad97B32Cd580d",
        "WBTC" to "0x7130d2A12B9BCbFAe4f2634d864A1Ee1Ce3Ead9c"
    )
    // token decimals
    private val decimals = mapOf(
        "ETH" to 18, "BNB" to 18, "WBTC" to 8, "USDT" to 6, "USDC" to 6
    )

    // VaultEx fee collection address — replace with real address in production
    private val VAULTEX_FEE_ADDRESS = "0x0000000000000000000000000000000000000001"

    fun getQuote(chain: String, fromSymbol: String, toSymbol: String, amount: String) {
        val amountDouble = amount.toDoubleOrNull() ?: run {
            _state.value = UiState.Error("Montant invalide"); return
        }
        viewModelScope.launch {
            _state.value = UiState.LoadingQuote
            try {
                val result = withContext(Dispatchers.IO) {
                    val tokens = if (chain == "ETH") ethTokens else bnbTokens
                    val chainId = if (chain == "ETH") 1 else 56
                    val fromAddr = tokens[fromSymbol] ?: error("Token $fromSymbol non supporté sur $chain")
                    val toAddr   = tokens[toSymbol]   ?: error("Token $toSymbol non supporté sur $chain")
                    val fromDec  = decimals[fromSymbol] ?: 18
                    val amountWei = BigDecimal(amountDouble)
                        .multiply(BigDecimal.TEN.pow(fromDec)).toBigInteger().toString()

                    val quote = oneInchApi.getQuote(
                        chainId = chainId,
                        fromTokenAddress = fromAddr,
                        toTokenAddress = toAddr,
                        amount = amountWei
                    )
                    val toDec = decimals[toSymbol] ?: 18
                    val toFormatted = BigDecimal(quote.toAmount)
                        .divide(BigDecimal.TEN.pow(toDec))
                        .setScale(6, java.math.RoundingMode.HALF_UP)
                        .toPlainString()
                    Pair(quote.toAmount, "$toFormatted $toSymbol")
                }
                _state.value = UiState.QuoteReady(result.first, result.second)
            } catch (e: Exception) {
                _state.value = UiState.Error(e.message ?: "Erreur lors du devis")
            }
        }
    }

    fun executeSwap(
        chain: String, fromSymbol: String, toSymbol: String,
        amount: String, slippage: String
    ) {
        val amountDouble = amount.toDoubleOrNull() ?: return
        viewModelScope.launch {
            _state.value = UiState.Swapping
            try {
                val txHash = withContext(Dispatchers.IO) {
                    val mnemonic = secureStorage.getMnemonic() ?: error("Pas de wallet")
                    val seed = MnemonicUtils.generateSeed(mnemonic.trim(), "")
                    val master = Bip32ECKeyPair.generateKeyPair(seed)
                    val path = intArrayOf(
                        44 or Bip32ECKeyPair.HARDENED_BIT,
                        60 or Bip32ECKeyPair.HARDENED_BIT,
                        0  or Bip32ECKeyPair.HARDENED_BIT, 0, 0
                    )
                    val credentials = Credentials.create(Bip32ECKeyPair.deriveKeyPair(master, path))
                    val fromAddress = credentials.address

                    val tokens  = if (chain == "ETH") ethTokens else bnbTokens
                    val chainId = if (chain == "ETH") 1L else 56L
                    val rpc     = if (chain == "ETH") ethRpc else bnbRpc

                    val fromAddr = tokens[fromSymbol] ?: error("Token $fromSymbol non supporté")
                    val toAddr   = tokens[toSymbol]   ?: error("Token $toSymbol non supporté")
                    val fromDec  = decimals[fromSymbol] ?: 18
                    val amountWei = BigDecimal(amountDouble)
                        .multiply(BigDecimal.TEN.pow(fromDec)).toBigInteger().toString()

                    val swapData = oneInchApi.getSwapData(
                        chainId = chainId.toInt(),
                        fromTokenAddress = fromAddr,
                        toTokenAddress = toAddr,
                        amount = amountWei,
                        fromAddress = fromAddress,
                        slippagePercent = slippage.toDoubleOrNull() ?: 0.5,
                        feeRecipient = VAULTEX_FEE_ADDRESS
                    )

                    // Get nonce
                    val nonceRes = rpc.rpcCall(JsonRpcRequest("eth_getTransactionCount", listOf(fromAddress, "latest")))
                    val nonce = BigInteger((nonceRes.result as String).removePrefix("0x"), 16)

                    val gasPriceRes = rpc.rpcCall(JsonRpcRequest("eth_gasPrice", emptyList()))
                    val gasPrice = BigInteger((gasPriceRes.result as String).removePrefix("0x"), 16)

                    val tx = swapData.tx
                    val value = if (tx.value == "0") BigInteger.ZERO
                                else BigInteger(tx.value.removePrefix("0x"), 16)

                    val rawTx = RawTransaction.createTransaction(
                        nonce, gasPrice, BigInteger.valueOf(tx.gas * 12 / 10),
                        tx.to, value, tx.data
                    )
                    val signed = Numeric.toHexString(TransactionEncoder.signMessage(rawTx, chainId, credentials))
                    val res = rpc.rpcCall(JsonRpcRequest("eth_sendRawTransaction", listOf(signed)))
                    val hash = res.result as? String ?: error(res.error?.message ?: "Broadcast failed")

                    transactionDao.insert(TransactionEntity(
                        hash = hash, type = "SWAP", blockchain = chain,
                        fromAddress = fromAddress, toAddress = tx.to,
                        amount = "$amount $fromSymbol → $toSymbol",
                        tokenSymbol = "$fromSymbol/$toSymbol", fee = "",
                        status = "PENDING", timestamp = System.currentTimeMillis(),
                        confirmations = 0, blockNumber = null
                    ))
                    hash
                }
                _state.value = UiState.Success(txHash)
            } catch (e: Exception) {
                _state.value = UiState.Error(e.message ?: "Erreur lors du swap")
            }
        }
    }

    fun reset() { _state.value = UiState.Idle }
}
