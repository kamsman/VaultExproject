package com.vaultex.core.config

import com.vaultex.BuildConfig

/**
 * API keys injected at build time from local.properties (gitignored).
 *
 * To configure: add the following lines to your local.properties file:
 *   etherscan.key=YOUR_KEY     → https://etherscan.io/myapikey
 *   bscscan.key=YOUR_KEY       → https://bscscan.com/myapikey
 *   changenow.key=YOUR_KEY     → https://changenow.io/api-keys
 *   flutterwave.key=YOUR_KEY   → Flutterwave dashboard → API → Secret key
 *
 * Keys default to empty string (APIs still work, may rate-limit without a key).
 */
object ApiKeys {
    val ETHERSCAN:   String = BuildConfig.ETHERSCAN_KEY
    val BSCSCAN:     String = BuildConfig.BSCSCAN_KEY
    val CHANGENOW:   String = BuildConfig.CHANGENOW_KEY
    val FLUTTERWAVE: String = BuildConfig.FLUTTERWAVE_KEY
}
