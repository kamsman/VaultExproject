package com.vaultex.domain.model

import com.vaultex.core.crypto.Blockchain
import java.math.BigDecimal
import java.math.BigInteger

data class Wallet(
    val id: String,
    val name: String,
    val accounts: List<Account>,
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class Account(
    val blockchain: Blockchain,
    val address: String,
    val publicKey: String,
    val derivationPath: String,
    val addressIndex: Int = 0
)

data class Token(
    val symbol: String,
    val name: String,
    val contractAddress: String?,    // null pour native (BTC, ETH, SOL...)
    val blockchain: Blockchain,
    val decimals: Int,
    val iconUrl: String?,
    val isNative: Boolean = contractAddress == null,
    val isCustom: Boolean = false
)

data class TokenBalance(
    val token: Token,
    val rawBalance: BigInteger,            // En unités smallest (wei, satoshi, lamport)
    val balanceFormatted: BigDecimal,      // Avec decimals appliqués
    val priceUsd: BigDecimal,
    val valueUsd: BigDecimal,
    val change24h: Double,                 // Pourcentage
    val lastUpdated: Long = System.currentTimeMillis()
)

data class Transaction(
    val hash: String,
    val type: TransactionType,
    val blockchain: Blockchain,
    val from: String,
    val to: String,
    val amount: BigDecimal,
    val token: Token,
    val fee: BigDecimal,
    val status: TransactionStatus,
    val timestamp: Long,
    val confirmations: Int = 0,
    val blockNumber: Long? = null,
    val memo: String? = null
)

enum class TransactionType { SEND, RECEIVE, SWAP, CONTRACT }
enum class TransactionStatus { PENDING, CONFIRMED, FAILED }

data class SwapQuote(
    val fromToken: Token,
    val toToken: Token,
    val fromAmount: BigDecimal,
    val toAmountEstimated: BigDecimal,
    val toAmountMinimum: BigDecimal,    // Avec slippage protection
    val exchangeRate: BigDecimal,
    val priceImpact: Double,            // Pourcentage
    val networkFee: BigDecimal,
    val networkFeeUsd: BigDecimal,
    val vaultexFee: BigDecimal,
    val vaultexFeeUsd: BigDecimal,
    val totalFeeUsd: BigDecimal,
    val route: List<String>,             // Chemin de swap (ex: ["ETH", "USDC", "USDT"])
    val protocols: List<String>,         // DEX utilisés
    val expiresAt: Long,
    val rawTx: String?                   // Données tx pré-construites par l'agrégateur
)

data class Contact(
    val id: String,
    val name: String,
    val addresses: Map<Blockchain, String>,
    val notes: String? = null,
    val avatarColor: Int = 0
)

data class PriceAlert(
    val id: String,
    val tokenSymbol: String,
    val condition: AlertCondition,
    val targetPrice: BigDecimal,
    val isActive: Boolean = true
)

enum class AlertCondition { ABOVE, BELOW }
