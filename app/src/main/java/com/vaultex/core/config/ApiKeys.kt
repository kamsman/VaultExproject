package com.vaultex.core.config

import com.vaultex.BuildConfig

/**
 * API keys injected at build time from local.properties (gitignored).
 *
 * To configure: add the following lines to your local.properties file:
 *   etherscan.key=YOUR_KEY     → https://etherscan.io/myapikey
 *   bscscan.key=YOUR_KEY       → https://bscscan.com/myapikey
 *   changenow.key=YOUR_KEY     → https://changenow.io/api-keys
 *   simpleswap.key=YOUR_KEY    → https://simpleswap.io/affiliate-program (Web Tools → API)
 *   swap.provider=simpleswap   → bascule l'échangeur (défaut : changenow)
 *   simpleswap.commission=1.5  → le taux porté par la clé, pour l'affichage
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

    /*
    ─── SIMPLESWAP ────────────────────────────────────────────────────────
    La clé PORTE la commission VaultEx, réglée entre 0,4 et 5 % dans l'espace
    partenaire. L'application ne prélève rien elle-même — elle ne le pourrait
    pas sans une seconde transaction, dont les frais réseau dépasseraient la
    commission sur tout échange de moins de 220 $.

    Deux clés valent mieux qu'une : une à 0,4 % pour les essais, une à 1,5 %
    en production. Changer de clé n'est pas changer un taux, ce qui contourne
    la limite d'une modification par 24 h imposée par SimpleSwap.
    */
    val SIMPLESWAP:  String = BuildConfig.SIMPLESWAP_KEY

    /** Commission de la clé en service — pour l'AFFICHAGE seulement. */
    val SIMPLESWAP_COMMISSION: Double = BuildConfig.SIMPLESWAP_COMMISSION

    /**
     * Échangeur en service : « changenow » ou « simpleswap ».
     *
     * Défaut « changenow » : sans décision explicite dans local.properties,
     * rien ne change pour les versions déjà distribuées.
     */
    val SWAP_PROVIDER: String = BuildConfig.SWAP_PROVIDER
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
