package com.vaultex.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Logo textuel « VAULTEX » — reproduit l'esprit du logo de marque : capitales,
 * graisse forte et lettres espacées. Utilisé partout où l'ancien texte
 * « VaultEx » servait de logo (splash, accueil, déverrouillage…), pour une
 * identité cohérente sans dépendre d'un fichier image.
 *
 * La couleur est passée par l'appelant : bleu de marque sur fond clair, blanc
 * sur fond sombre/coloré — pour rester lisible partout.
 */
@Composable
fun VaultexWordmark(
    fontSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier
) {
    Text(
        text = "VAULTEX",
        color = color,
        fontSize = fontSize,
        fontWeight = FontWeight.Black,
        letterSpacing = (fontSize.value * 0.06f).sp,
        modifier = modifier
    )
}

/** Bleu de la marque VAULTEX (proche du logo). */
val VaultexBrandBlue = Color(0xFF1E7FF5)
