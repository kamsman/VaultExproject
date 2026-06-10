package com.vaultex.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Alias hérités de l'ancienne charte claire, remappés sur la palette
 * sombre "premium" de Theme.kt pour garantir un rendu cohérent.
 * À terme, les écrans doivent référencer MaterialTheme.colorScheme ;
 * ne pas ajouter de nouvelle couleur ici.
 */
object VaultExColors {
    // Accent principal (ex-bleus → or premium)
    val BluePrimary = AccentGold
    val BlueDark = AccentGoldDark
    val BlueLight = SurfaceLight
    val BlueGradientStart = BgTertiary
    val BlueGradientEnd = BgSecondary

    // Fonds
    val Background = BgPrimary
    val SplashBackground = Color(0xFF0A0E1A)
    val CardBackground = Surface

    // Gold (logo/splash)
    val Gold = AccentGold

    // Texte
    val TextPrimary = com.vaultex.ui.theme.TextPrimary
    val TextSecondary = com.vaultex.ui.theme.TextSecondary
    val TextOnPrimary = BgPrimary

    // Statuts
    val Success = AccentGreen
    val Error = AccentRed
    val Warning = AccentOrange

    // Bordures
    val Border = BorderColor
    val DividerColor = BorderColor

    // Chaînes
    val BitcoinOrange = NetworkBtc
    val EthereumBlue = NetworkEth
    val BnbYellow = NetworkBnb
    val TronRed = NetworkTrx
    val SolanaGreen = NetworkSol
}
