package com.vaultex.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ─── PALETTE VAULTEX V2 (prototype clair / bleu) ─────────────
val BgPrimary    = Color(0xFFF5F8FF)   // fond d'écran
val BgSecondary  = Color(0xFFFFFFFF)
val BgTertiary   = Color(0xFFE8F1FD)
val Surface      = Color(0xFFFFFFFF)   // cartes
val SurfaceLight = Color(0xFFF1F5F9)

val AccentBlue     = Color(0xFF1A6FE8) // accent principal
val AccentBlueDark = Color(0xFF0C447C)
val AccentGreen    = Color(0xFF16A34A)
val AccentRed      = Color(0xFFEF4444)
val AccentOrange   = Color(0xFFF59E0B)

val TextPrimary   = Color(0xFF0F172A)
val TextSecondary = Color(0xFF64748B)
val TextMuted     = Color(0xFF94A3B8)

val BorderColor   = Color(0xFFE2E8F0)

// Navy réservé au splash / welcome / premier lancement
val SplashNavyTop    = Color(0xFF080D22)
val SplashNavyBottom = Color(0xFF1A2858)

// Couleurs réseaux
val NetworkBtc = Color(0xFFF7931A)
val NetworkEth = Color(0xFF627EEA)
val NetworkBnb = Color(0xFFF0B90B)
val NetworkSol = Color(0xFF9945FF)
val NetworkTrx = Color(0xFFFF060A)

internal val VaultExColorScheme = lightColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    primaryContainer = BgTertiary,
    onPrimaryContainer = AccentBlueDark,
    secondary = AccentGreen,
    onSecondary = Color.White,
    background = BgPrimary,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceLight,
    onSurfaceVariant = TextSecondary,
    error = AccentRed,
    onError = Color.White,
    outline = BorderColor
)
