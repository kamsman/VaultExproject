package com.vaultex.domain.usecase

import com.vaultex.data.remote.api.OneInchApi
import com.vaultex.data.remote.api.ChangeNowApi
import javax.inject.Inject

class SwapUseCase @Inject constructor() {
    companion object {
        const val VAULTEX_FEE_PERCENT = 1.5
        const val MOBILE_MONEY_FEE_PERCENT = 1.0

        fun applyFee(amount: Double): Pair<Double, Double> {
            val fee = amount * (VAULTEX_FEE_PERCENT / 100.0)
            return Pair(fee, amount - fee)
        }
    }
}
