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

    // Sélection de la monnaie à envoyer (réseau + liste) AVANT le formulaire.
    const val SEND_SELECT = "sendSelect"

    const val RECEIVE = "receive"

    const val SWAP = "swap"

    const val HISTORY = "history"

    const val NOTIFICATIONS = "notifications"

    const val TOKEN_DETAIL = "tokenDetail/{symbol}"

    fun tokenDetail(symbol: String) = "tokenDetail/$symbol"

    const val HOME = "home"

    const val COIN_DETAIL = "coinDetail/{coinId}"

    fun coinDetail(coinId: String) = "coinDetail/$coinId"

    const val MOBILE_MONEY = "mobileMoney"

    const val BACKUP = "backup"

    const val PANIC_PIN = "panicPin"

    const val BIOMETRIC_SETUP = "biometricSetup"

    const val WALLET_MANAGER = "walletManager"

    const val TOKEN_MANAGER = "tokenManager"

    const val ADDRESS_BOOK = "addressBook"

    const val NETWORK_SETTINGS = "networkSettings"

    const val SECURITY = "security"

    const val PENDING_SENDS = "pendingSends"

    const val SCANNER = "scanner"

    const val HISTORY_DETAIL = "historyDetail/{hash}"

    fun historyDetail(hash: String) = "historyDetail/$hash"

    /*
    =========================
    ÉCRANS SECONDAIRES / PROTOTYPES
    =========================
     */

    const val FIRST_LAUNCH = "firstLaunch"

    const val HELP = "help"

    const val PORTFOLIO = "portfolio"

    const val PORTFOLIO_TOKEN_DETAIL = "portfolioTokenDetail"

    const val SEND_FORM = "sendForm"

    const val SEND_CONFIRM = "sendConfirm"

    const val SEND_RESULT = "sendResult/{hash}"

    fun sendResult(hash: String) = "sendResult/$hash"

    const val SWAP_CONFIRM = "swapConfirm"

    const val SWAP_RESULT = "swapResult/{hash}"

    fun swapResult(hash: String) = "swapResult/$hash"

    const val RECEIVE_NETWORK = "receiveNetwork"

    const val RECEIVE_ADDRESS = "receiveAddress/{blockchain}"

    fun receiveAddress(blockchain: String) = "receiveAddress/$blockchain"

    // Réception d'un actif précis : symbole affiché + chaîne de l'adresse.
    const val RECEIVE_ASSET = "receiveAsset/{symbol}/{chain}"

    fun receiveAsset(symbol: String, chain: String) = "receiveAsset/$symbol/$chain"

    const val ADD_TOKEN = "addToken"

    const val MANAGE_TOKENS = "manageTokens"

    const val MANAGE_ASSETS = "manageAssets"

    const val SECURITY_SETUP = "securitySetup"

    const val SETTINGS_IMPORT_WALLET = "settingsImportWallet"

    const val WALLET_MANAGEMENT = "walletManagement"

    const val SECURITY_NOTIFICATIONS = "securityNotifications"
}