package com.vaultex.ui.navigation

object Routes {

    const val SPLASH = "splash"

    const val ONBOARDING = "onboarding"

    const val WELCOME = "welcome"

    const val MNEMONIC_DISPLAY = "mnemonicDisplay"

    const val MNEMONIC_VERIFY = "mnemonicVerify"

    const val IMPORT_WALLET = "importWallet"

    /*
    =========================
    PIN
    =========================
     */

    const val PIN_SETUP = "pinSetup"

    const val PIN_UNLOCK = "pinUnlock"

    /*
    =========================
    MAIN
    =========================
     */

    const val DASHBOARD = "dashboard"

    const val MARKET = "market"

    const val SETTINGS = "settings"

    const val SEND = "send"

    const val RECEIVE = "receive"

    const val SWAP = "swap"

    const val HISTORY = "history"

    const val NOTIFICATIONS = "notifications"

    const val TOKEN_DETAIL = "tokenDetail/{symbol}"

    fun tokenDetail(symbol: String) = "tokenDetail/$symbol"

    const val COIN_DETAIL = "coinDetail"

    const val MOBILE_MONEY = "mobileMoney"

    const val BACKUP = "backup"

    const val PANIC_PIN = "panicPin"

    const val BIOMETRIC_SETUP = "biometricSetup"

    const val WALLET_MANAGER = "walletManager"

    const val TOKEN_MANAGER = "tokenManager"

    const val ADDRESS_BOOK = "addressBook"

    const val NETWORK_SETTINGS = "networkSettings"

    const val SECURITY = "security"

    const val SCANNER = "scanner"

    const val HISTORY_DETAIL = "history_detail"
}