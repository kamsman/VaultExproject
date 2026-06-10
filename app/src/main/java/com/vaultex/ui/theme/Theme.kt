package com.vaultex.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ─── COULEURS VAULTEX PREMIUM ─────────────────────────────────
val BgPrimary    = Color(0xFF1A1A2E)
val BgSecondary  = Color(0xFF16213E)
val BgTertiary   = Color(0xFF0F3460)
val Surface      = Color(0xFF1E2D45)
val SurfaceLight = Color(0xFF243657)

val AccentGold      = Color(0xFFC9A84C)
val AccentGoldDark  = Color(0xFFA88838)
val AccentGreen     = Color(0xFF00C896)
val AccentRed       = Color(0xFFFF4D6D)
val AccentOrange    = Color(0xFFFFA928)

val TextPrimary   = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFB0B7C3)
val TextMuted     = Color(0xFF6B7280)

val BorderColor   = Color(0xFF2A3F5F)

// Couleurs réseaux
val NetworkBtc = Color(0xFFF7931A)
val NetworkEth = Color(0xFF627EEA)
val NetworkBnb = Color(0xFFF0B90B)
val NetworkSol = Color(0xFF9945FF)
val NetworkTrx = Color(0xFFFF060A)

private val DarkColorScheme = darkColorScheme(
    primary = AccentGold,
    onPrimary = BgPrimary,
    secondary = AccentGreen,
    onSecondary = BgPrimary,
    background = BgPrimary,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceLight,
    onSurfaceVariant = TextSecondary,
    error = AccentRed,
    onError = TextPrimary,
    outline = BorderColor
)

@Composable
fun VaultExDarkTheme(
    darkTheme: Boolean = true,  // Toujours dark - design premium
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = VaultExTypography,
        content = content
    )
}
