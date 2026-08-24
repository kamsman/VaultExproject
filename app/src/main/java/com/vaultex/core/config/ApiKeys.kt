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
 *   trongrid.key=YOUR_KEY      → https://www.trongrid.io (optionnel, anti rate-limit)
 *   coingecko.key=YOUR_KEY     → https://www.coingecko.com/en/developers/dashboard (Demo gratuit, anti rate-limit)
 *
 * Keys default to empty string (APIs still work, may rate-limit without a key).
 */
object ApiKeys {
    val ETHERSCAN:   String = BuildConfig.ETHERSCAN_KEY
    val BSCSCAN:     String = BuildConfig.BSCSCAN_KEY
    val CHANGENOW:   String = BuildConfig.CHANGENOW_KEY
    val FLUTTERWAVE: String = BuildConfig.FLUTTERWAVE_KEY
    val TRONGRID:    String = BuildConfig.TRONGRID_KEY
    val COINGECKO:   String = BuildConfig.COINGECKO_KEY

    /**
     * Adresse du relais de cours (Worker Cloudflare), barre oblique finale
     * comprise. Vide = appel direct à CoinGecko, comme avant.
     *
     * Ce n'est pas un secret — c'est une adresse publique. Elle vit ici avec
     * les clés parce qu'elle dépend du compte de celui qui compile, et n'a
     * donc rien à faire en dur dans le dépôt.
     */
    val PRICE_RELAY: String = BuildConfig.PRICE_RELAY_URL
}
