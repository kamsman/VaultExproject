package com.vaultex.ui.navigation

object Routes {

    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val WELCOME = "welcome"
    const val MNEMONIC_DISPLAY = "mnemonicDisplay"
    const val MNEMONIC_VERIFY = "mnemonicVerify"
    const val IMPORT_WALLET = "importWallet"

    // PIN
    const val PIN_SETUP = "pinSetup"
    const val PIN_UNLOCK = "pinUnlock"
    const val SHOW_MNEMONIC = "showMnemonic"

    // Déverrouillage avec destination — ex: pinUnlockTo/showMnemonic
    private const val PIN_UNLOCK_TO = "pinUnlockTo"
    fun pinUnlockTo(destination: String) = "$PIN_UNLOCK_TO/$destination"
    const val PIN_UNLOCK_TO_ROUTE = "$PIN_UNLOCK_TO/{destination}"

    // Main
    const val DASHBOARD = "dashboard"
    const val MARKET = "market"
    const val SETTINGS = "settings"
    const val SEND = "send"
    const val RECEIVE = "receive"
    const val SWAP = "swap"
    const val HISTORY = "history"
    const val NOTIFICATIONS = "notifications"

    // Token detail avec argument symbol
    private const val TOKEN_DETAIL_BASE = "tokenDetail"
    fun tokenDetail(symbol: String) = "$TOKEN_DETAIL_BASE/$symbol"
    const val TOKEN_DETAIL = "$TOKEN_DETAIL_BASE/{symbol}"
}
