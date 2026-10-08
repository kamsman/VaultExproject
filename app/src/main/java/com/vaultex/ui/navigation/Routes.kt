package com.vaultex.ui.navigation

object Routes {

    const val SPLASH = "splash"

    const val ONBOARDING = "onboarding"

    const val WELCOME = "welcome"

    const val MNEMONIC_DISPLAY = "mnemonicDisplay"

    const val MNEMONIC_VERIFY = "mnemonicVerify"

    const val IMPORT_WALLET = "importWallet"

    /**
     * Seed présent sur l'appareil mais que le Keystore ne sait plus
     * déchiffrer. Écran de dernier recours : il ne propose QUE la
     * restauration par phrase.
     */
    const val SEED_ILLISIBLE = "seedIllisible"

    /*
    =========================
    PIN
    =========================
     */

    // Documents légaux embarqués (type = terms | privacy).
    const val LEGAL = "legal/{doc}"
    fun legal(doc: String) = "legal/$doc"

    const val PIN_SETUP = "pinSetup"
    const val PIN_CHANGE_VERIFY = "pinChangeVerify"

    // Vérification PIN/biométrie AVANT d'ajouter un wallet. mode = create|import.
    const val WALLET_ADD_VERIFY = "walletAddVerify/{mode}"
    fun walletAddVerify(mode: String) = "walletAddVerify/$mode"

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

    /*
    ACHETER ET VENDRE EN FRANCS, AUPRES D'UN CHANGEUR

    VaultEx calcule le prix, donne la reference et transmet la demande. Elle
    ne detient JAMAIS les fonds : l'argent va d'Orange Money a Orange Money,
    la crypto d'un portefeuille a l'autre. C'est ce qui la tient a l'ecart
    de l'activite d'intermediation financiere.
    */
    const val CHANGE = "change"

    /** Pi Network — adresse et solde, en lecture seule. */
    const val PI = "pi"

    /*
    ENVOI DE PI — UN ÉCRAN À PART, ET POUR DE BONNES RAISONS

    L'écran d'envoi générique sait traiter cinq chaînes avec les mêmes
    champs : destination, montant, frais. Le Pi en demande trois de plus,
    et chacun existe parce que son absence coûte de l'argent.

    LA RÉSERVE. Le réseau Pi oblige à laisser un minimum sur le compte. Le
    « Max » générique proposerait le solde entier, et chaque appui
    produirait une transaction refusée dont les frais sont brûlés.

    LE MÉMO. Un dépôt en bourse sans mémo est perdu. Aucune autre chaîne de
    l'application n'a cette notion, et l'ajouter au formulaire commun
    afficherait un champ incompréhensible sur six écrans pour en servir un.

    LA CRÉATION DE COMPTE. Une adresse Pi jamais créditée ne peut pas être
    « payée », elle doit être créée, avec un montant plancher. C'est une
    règle sans équivalent ailleurs, et elle change le montant minimum.

    Plier le formulaire commun à ces trois règles l'aurait alourdi pour
    toutes les monnaies. Un écran dédié dit exactement ce que le Pi exige,
    et rien de plus.
    */
    const val PI_ENVOI = "piEnvoi"

    // Centre de notifications in-app (dépôts, alertes, annonces) + compteur non-lus.
    const val NOTIFICATION_CENTER = "notificationCenter"

    const val TOKEN_DETAIL = "tokenDetail/{symbol}"

    fun tokenDetail(symbol: String) = "tokenDetail/$symbol"

    const val HOME = "home"

    const val COIN_DETAIL = "coinDetail/{coinId}"

    fun coinDetail(coinId: String) = "coinDetail/$coinId"

    const val BACKUP = "backup"

    const val PANIC_PIN = "panicPin"

    const val BIOMETRIC_SETUP = "biometricSetup"

    const val WALLET_MANAGER = "walletManager"

    const val TOKEN_MANAGER = "tokenManager"

    const val ADDRESS_BOOK = "addressBook"

    const val NETWORK_SETTINGS = "networkSettings"

    const val SECURITY = "security"
    const val ANTI_PHISHING = "antiPhishing"

    // Journal des déverrouillages (Notifications sécurité → Historique des connexions).
    const val LOGIN_HISTORY = "loginHistory"

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