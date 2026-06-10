package com.vaultex.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val VaultExColorScheme = lightColorScheme(
    primary = VaultExColors.BluePrimary,
    onPrimary = VaultExColors.TextOnPrimary,
    primaryContainer = VaultExColors.BlueLight,
    background = VaultExColors.Background,
    surface = VaultExColors.CardBackground,
    onBackground = VaultExColors.TextPrimary,
    onSurface = VaultExColors.TextPrimary,
    error = VaultExColors.Error,
    outline = VaultExColors.Border
)

@Composable
fun VaultExTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VaultExColorScheme,
        typography = VaultExTypography,
        content = content
    )
}
