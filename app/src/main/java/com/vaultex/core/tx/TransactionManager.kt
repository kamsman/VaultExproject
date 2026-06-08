package com.vaultex.core.tx

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionManager @Inject constructor(
    val evm: EvmTransactionService,
    val btc: BtcTransactionService,
    val tron: TronTransactionService
)
