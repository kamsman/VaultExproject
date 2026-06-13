package com.vaultex.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ─── Palette réactive (clair / sombre) ─────────────────────────
data class AppColors(
    val bgPrimary: Color,
    val bgSecondary: Color,
    val bgTertiary: Color,
    val surface: Color,
    val surfaceLight: Color,
    val accentBlue: Color,
    val accentBlueDark: Color,
    val accentGreen: Color,
    val accentRed: Color,
    val accentOrange: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val borderColor: Color,
    val isDark: Boolean
)

val LightColors = AppColors(
    bgPrimary = Color(0xFFF5F8FF),
    bgSecondary = Color(0xFFFFFFFF),
    bgTertiary = Color(0xFFE8F1FD),
    surface = Color(0xFFFFFFFF),
    surfaceLight = Color(0xFFF1F5F9),
    accentBlue = Color(0xFF1A6FE8),
    accentBlueDark = Color(0xFF0C447C),
    accentGreen = Color(0xFF16A34A),
    accentRed = Color(0xFFEF4444),
    accentOrange = Color(0xFFF59E0B),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF64748B),
    textMuted = Color(0xFF94A3B8),
    borderColor = Color(0xFFE2E8F0),
    isDark = false
)

val DarkColors = AppColors(
    bgPrimary = Color(0xFF0B1120),
    bgSecondary = Color(0xFF111827),
    bgTertiary = Color(0xFF1E293B),
    surface = Color(0xFF1A2234),
    surfaceLight = Color(0xFF243044),
    accentBlue = Color(0xFF3B82F6),
    accentBlueDark = Color(0xFF1D4ED8),
    accentGreen = Color(0xFF22C55E),
    accentRed = Color(0xFFF87171),
    accentOrange = Color(0xFFFBBF24),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFF94A3B8),
    textMuted = Color(0xFF64748B),
    borderColor = Color(0xFF2A3650),
    isDark = true
)

val LocalAppColors = staticCompositionLocalOf { LightColors }

// ─── Constantes réactives (lues par les écrans existants) ──────
// Ces propriétés @Composable suivent automatiquement le thème actif.
val BgPrimary: Color    @Composable get() = LocalAppColors.current.bgPrimary
val BgSecondary: Color  @Composable get() = LocalAppColors.current.bgSecondary
val BgTertiary: Color   @Composable get() = LocalAppColors.current.bgTertiary
val Surface: Color      @Composable get() = LocalAppColors.current.surface
val SurfaceLight: Color @Composable get() = LocalAppColors.current.surfaceLight
val AccentBlue: Color     @Composable get() = LocalAppColors.current.accentBlue
val AccentBlueDark: Color @Composable get() = LocalAppColors.current.accentBlueDark
val AccentGreen: Color    @Composable get() = LocalAppColors.current.accentGreen
val AccentRed: Color      @Composable get() = LocalAppColors.current.accentRed
val AccentOrange: Color   @Composable get() = LocalAppColors.current.accentOrange
val TextPrimary: Color    @Composable get() = LocalAppColors.current.textPrimary
val TextSecondary: Color  @Composable get() = LocalAppColors.current.textSecondary
val TextMuted: Color      @Composable get() = LocalAppColors.current.textMuted
val BorderColor: Color    @Composable get() = LocalAppColors.current.borderColor

// ─── Couleurs fixes (indépendantes du thème) ──────────────────
val SplashNavyTop    = Color(0xFF080D22)
val SplashNavyBottom = Color(0xFF1A2858)

val NetworkBtc = Color(0xFFF7931A)
val NetworkEth = Color(0xFF627EEA)
val NetworkBnb = Color(0xFFF0B90B)
val NetworkSol = Color(0xFF9945FF)
val NetworkTrx = Color(0xFFFF060A)

// ─── Schémas Material 3 dérivés des palettes ──────────────────
internal val LightColorScheme = lightColorScheme(
    primary = LightColors.accentBlue,
    onPrimary = Color.White,
    primaryContainer = LightColors.bgTertiary,
    onPrimaryContainer = LightColors.accentBlueDark,
    secondary = LightColors.accentGreen,
    onSecondary = Color.White,
    background = LightColors.bgPrimary,
    onBackground = LightColors.textPrimary,
    surface = LightColors.surface,
    onSurface = LightColors.textPrimary,
    surfaceVariant = LightColors.surfaceLight,
    onSurfaceVariant = LightColors.textSecondary,
    error = LightColors.accentRed,
    onError = Color.White,
    outline = LightColors.borderColor
)

internal val DarkColorSchemeM3 = darkColorScheme(
    primary = DarkColors.accentBlue,
    onPrimary = Color.White,
    primaryContainer = DarkColors.bgTertiary,
    onPrimaryContainer = Color.White,
    secondary = DarkColors.accentGreen,
    onSecondary = Color.White,
    background = DarkColors.bgPrimary,
    onBackground = DarkColors.textPrimary,
    surface = DarkColors.surface,
    onSurface = DarkColors.textPrimary,
    surfaceVariant = DarkColors.surfaceLight,
    onSurfaceVariant = DarkColors.textSecondary,
    error = DarkColors.accentRed,
    onError = Color.White,
    outline = DarkColors.borderColor
)
