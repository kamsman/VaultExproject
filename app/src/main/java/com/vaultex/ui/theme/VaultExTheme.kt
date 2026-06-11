package com.vaultex.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun VaultExTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VaultExColorScheme,
        typography = VaultExTypography,
        content = content
    )
}
