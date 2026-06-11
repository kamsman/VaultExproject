package com.vaultex.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Alias hérités, alignés sur la palette claire du prototype v2.
 * Les écrans doivent à terme référencer MaterialTheme.colorScheme ;
 * ne pas ajouter de nouvelle couleur ici.
 */
object VaultExColors {
    // Accent principal
    val BluePrimary = AccentBlue
    val BlueDark = AccentBlueDark
    val BlueLight = BgTertiary
    val BlueGradientStart = AccentBlue
    val BlueGradientEnd = AccentBlueDark

    // Fonds
    val Background = BgPrimary
    val SplashBackground = SplashNavyTop
    val CardBackground = Surface

    // Gold (logo/splash hérité)
    val Gold = Color(0xFFD4A843)

    // Texte
    val TextPrimary = com.vaultex.ui.theme.TextPrimary
    val TextSecondary = com.vaultex.ui.theme.TextSecondary
    val TextOnPrimary = Color.White

    // Statuts
    val Success = AccentGreen
    val Error = AccentRed
    val Warning = AccentOrange

    // Bordures
    val Border = BorderColor
    val DividerColor = SurfaceLight

    // Chaînes
    val BitcoinOrange = NetworkBtc
    val EthereumBlue = NetworkEth
    val BnbYellow = NetworkBnb
    val TronRed = NetworkTrx
    val SolanaGreen = NetworkSol
}
