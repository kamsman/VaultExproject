package com.vaultex.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Alias hérités. Les couleurs dépendantes du thème sont des propriétés
 * @Composable qui suivent le thème actif (clair/sombre) ; les couleurs
 * fixes (réseaux, splash) restent de simples constantes.
 */
object VaultExColors {
    // Accent principal (réactifs)
    val BluePrimary: Color       @Composable get() = AccentBlue
    val BlueDark: Color          @Composable get() = AccentBlueDark
    val BlueLight: Color         @Composable get() = BgTertiary
    val BlueGradientStart: Color @Composable get() = AccentBlue
    val BlueGradientEnd: Color   @Composable get() = AccentBlueDark

    // Fonds (réactifs)
    val Background: Color     @Composable get() = BgPrimary
    val CardBackground: Color @Composable get() = Surface

    // Texte (réactifs)
    val TextPrimary: Color   @Composable get() = com.vaultex.ui.theme.TextPrimary
    val TextSecondary: Color @Composable get() = com.vaultex.ui.theme.TextSecondary

    // Statuts (réactifs)
    val Success: Color @Composable get() = AccentGreen
    val Error: Color   @Composable get() = AccentRed
    val Warning: Color @Composable get() = AccentOrange

    // Bordures (réactifs)
    val Border: Color        @Composable get() = BorderColor
    val DividerColor: Color  @Composable get() = SurfaceLight

    // Fixes
    val SplashBackground = SplashNavyTop
    val Gold = Color(0xFFD4A843)
    val TextOnPrimary = Color.White

    // Chaînes (fixes)
    val BitcoinOrange = NetworkBtc
    val EthereumBlue = NetworkEth
    val BnbYellow = NetworkBnb
    val TronRed = NetworkTrx
    val SolanaGreen = NetworkSol
}
