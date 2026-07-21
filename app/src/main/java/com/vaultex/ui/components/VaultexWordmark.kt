package com.vaultex.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vaultex.R

/**
 * Logo de marque « VAULTEX » — image réelle (drawable/logo_tex.png), utilisée
 * partout où le texte « VaultEx » servait auparavant de logo (splash, accueil,
 * déverrouillage…). Dimensionné par HAUTEUR (ratio conservé automatiquement).
 */
@Composable
fun VaultexWordmark(
    height: Dp,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(R.drawable.logo_tex),
        contentDescription = "VaultEx",
        contentScale = ContentScale.Fit,
        modifier = modifier.height(height)
    )
}

/** Bleu de la marque VAULTEX (repris du logo) — pour les endroits sans image. */
val VaultexBrandBlue = Color(0xFF1E7FF5)
