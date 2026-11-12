package com.vaultex.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vaultex.R

/**
 * Logo de marque « VAULTEX » — image réelle (drawable/logo_tex.png), utilisée
 * partout où le texte « VaultEx » servait auparavant de logo (splash, accueil,
 * déverrouillage…). Dimensionné par HAUTEUR (ratio conservé automatiquement).
 *
 * Chargé via Coil (et non painterResource) : le décodage se fait HORS du
 * thread principal et l'image est réduite si besoin → pas de blocage du
 * démarrage même avec un gros PNG, et pas de crash si le fichier est illisible.
 */
@Composable
fun VaultexWordmark(
    height: Dp,
    modifier: Modifier = Modifier
) {
    coil.compose.AsyncImage(
        model = R.drawable.logo_tex,
        contentDescription = "VaultEx",
        contentScale = ContentScale.Fit,
        modifier = modifier.height(height)
    )
}

/*
═══════════════════════════════════════════════════════════════════════════
LE NOM QUI SE RÉVÈLE, LETTRE APRÈS LETTRE
═══════════════════════════════════════════════════════════════════════════

Un volet passe de gauche à droite et découvre le logo, pendant qu'il monte
de quelques points. À la vitesse où ça se joue, l'œil lit « les lettres
arrivent l'une après l'autre ».

─── POURQUOI PAS DE VRAIES LETTRES ANIMÉES ─────────────────────────────

La maquette animait chaque lettre séparément, ce qui demande des tracés
vectoriels — V, A, U, L, T, les trois barres du E, X — au lieu de l'image.

Ces tracés ont été comparés au vrai `logo_tex.png` : ils en sont très
proches, mais pas identiques. Les barres du E y sont plus arrondies et les
traits un peu plus fins. Les adopter mettrait DEUX versions du logo dans
l'application — celle du splash et celle de l'accueil, du déverrouillage,
de l'assistance — et personne ne saurait plus laquelle fait foi.

Un volet sur la vraie image donne le même effet sans toucher à la marque.
Le jour où le logo existera en vectoriel officiel, l'animation lettre par
lettre redeviendra possible sans ce compromis.

─── LE VOLET NE FAIT PAS DE FONDU ──────────────────────────────────────

Un fondu ferait apparaître le mot entier en pâle, puis le durcirait : on
verrait « VAULTEX » dès le premier instant. Le volet le fait apparaître
pour de bon, de gauche à droite, ce qui est le mouvement voulu.
═══════════════════════════════════════════════════════════════════════════
*/
@Composable
fun VaultexWordmarkAnime(
    height: Dp,
    avancement: Float,
    modifier: Modifier = Modifier
) {
    val p = avancement.coerceIn(0f, 1f)
    if (p <= 0f) {
        // Place tenue dès le premier instant : sans ça, le nom pousserait le
        // reste de la colonne vers le bas en apparaissant.
        androidx.compose.foundation.layout.Spacer(modifier.height(height))
        return
    }

    VaultexWordmark(
        height = height,
        modifier = modifier
            .graphicsLayer {
                // Le mot finit de monter avant que le volet ne l'ait
                // entierement decouvert : il est pose quand on le lit.
                translationY = (1f - p) * 14.dp.toPx() * 1.4f
            }
            .drawWithContent {
                clipRect(right = size.width * p) { this@drawWithContent.drawContent() }
            }
    )
}

/** Bleu de la marque VAULTEX (repris du logo) — pour les endroits sans image. */
val VaultexBrandBlue = Color(0xFF1E7FF5)
