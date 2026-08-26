package com.vaultex.ui.screens.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text

/*
═══════════════════════════════════════════════════════════════════════════
ILLUSTRATIONS D'ACCUEIL — DESSINÉES, PAS IMPORTÉES
═══════════════════════════════════════════════════════════════════════════

Ces trois images sont tracées à l'exécution plutôt que livrées en fichiers.
Trois raisons, dans l'ordre d'importance :

1. ELLES RESTENT NETTES PARTOUT. Une image matricielle doit être fournie en
   quatre ou cinq définitions pour couvrir le parc Android, et paraîtra
   quand même floue sur les écrans les plus fins. Un tracé se calcule à la
   taille demandée : il est exact sur tous les appareils.

2. ELLES NE PÈSENT RIEN. Cinq déclinaisons de trois illustrations, c'est
   facilement deux mégaoctets dans l'APK. Sur le marché visé, où l'on
   s'échange les applications par Bluetooth et WhatsApp sur des forfaits
   comptés, le poids du fichier est un vrai sujet.

3. ELLES SUIVENT LA MARQUE. Les couleurs viennent des mêmes constantes que
   le reste de l'application : changer la teinte d'accent les met toutes à
   jour, sans repasser par un outil graphique.

Aucun texte n'est tracé dans le canevas — les sigles des monnaies sont de
vrais composants texte posés par-dessus, ce qui les rend traduisibles et
lisibles par les outils d'accessibilité.
═══════════════════════════════════════════════════════════════════════════
*/

/** Bleu clair de la marque, repris des écrans d'accueil. */
private val Cyan = Color(0xFF60A5FA)
private val Violet = Color(0xFF8B5CF6)
private val BleuVif = Color(0xFF3B82F6)

/**
 * Écran 1 — un bouclier portant un cadenas.
 *
 * La sécurité est la première promesse de l'application ; c'est donc la
 * première image, avant même la liste des chaînes prises en charge.
 */
@Composable
fun IllustrationBouclier(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val l = size.minDimension
        val cx = size.width / 2f
        val cy = size.height / 2f

        // Halo : donne la profondeur du fond sombre sans image de fond.
        drawCircle(
            brush = Brush.radialGradient(
                listOf(BleuVif.copy(alpha = 0.28f), Color.Transparent),
                center = Offset(cx, cy),
                radius = l * 0.62f
            ),
            radius = l * 0.62f,
            center = Offset(cx, cy)
        )

        val larg = l * 0.52f
        val haut = l * 0.62f
        val gauche = cx - larg / 2f
        val sommet = cy - haut / 2f
        val bouclier = Path().apply {
            moveTo(cx, sommet)
            lineTo(gauche + larg, sommet + haut * 0.17f)
            lineTo(gauche + larg, sommet + haut * 0.55f)
            cubicTo(
                gauche + larg, sommet + haut * 0.83f,
                cx + larg * 0.28f, sommet + haut * 0.96f,
                cx, sommet + haut
            )
            cubicTo(
                cx - larg * 0.28f, sommet + haut * 0.96f,
                gauche, sommet + haut * 0.83f,
                gauche, sommet + haut * 0.55f
            )
            lineTo(gauche, sommet + haut * 0.17f)
            close()
        }
        drawPath(
            bouclier,
            Brush.linearGradient(
                listOf(Cyan.copy(alpha = 0.55f), Violet.copy(alpha = 0.55f)),
                start = Offset(gauche, sommet),
                end = Offset(gauche + larg, sommet + haut)
            )
        )
        drawPath(bouclier, Cyan, style = Stroke(width = l * 0.012f))

        // Cadenas : anse en arc, corps en rectangle arrondi.
        val cadL = larg * 0.34f
        val cadH = cadL * 0.82f
        val cadX = cx - cadL / 2f
        val cadY = cy - cadH * 0.18f
        val epais = l * 0.022f
        drawArc(
            color = Color.White,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(cx - cadL * 0.30f, cadY - cadH * 0.62f),
            size = Size(cadL * 0.60f, cadH * 0.62f),
            style = Stroke(width = epais)
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(cadX, cadY),
            size = Size(cadL, cadH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(epais, epais)
        )
        // Trou de serrure, évidé dans le corps.
        drawCircle(
            color = Color(0xFF0C1330),
            radius = cadL * 0.11f,
            center = Offset(cx, cadY + cadH * 0.42f)
        )

        etincelles(l, cx, cy)
    }
}

/**
 * Écran 2 — une clé dans un anneau lumineux.
 *
 * L'anneau isole la clé : elle est à l'utilisateur, et à personne d'autre.
 */
@Composable
fun IllustrationCle(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val l = size.minDimension
        val cx = size.width / 2f
        val cy = size.height / 2f

        drawCircle(
            brush = Brush.radialGradient(
                listOf(Cyan.copy(alpha = 0.22f), Color.Transparent),
                center = Offset(cx, cy),
                radius = l * 0.55f
            ),
            radius = l * 0.55f,
            center = Offset(cx, cy)
        )

        val rayon = l * 0.34f
        drawCircle(
            brush = Brush.sweepGradient(listOf(Cyan, Violet, BleuVif, Cyan), Offset(cx, cy)),
            radius = rayon,
            center = Offset(cx, cy),
            style = Stroke(width = l * 0.018f)
        )

        // Clé : anneau évidé à gauche, tige vers la droite, deux dents dessous.
        val epais = l * 0.030f
        val rTete = rayon * 0.34f
        val teteX = cx - rayon * 0.34f
        drawCircle(Color.White, rTete, Offset(teteX, cy), style = Stroke(width = epais))
        drawCircle(Color(0xFF0C1330), rTete * 0.34f, Offset(teteX, cy))

        val tigeFin = cx + rayon * 0.62f
        drawLine(
            Color.White,
            start = Offset(teteX + rTete, cy),
            end = Offset(tigeFin, cy),
            strokeWidth = epais
        )
        drawLine(
            Color.White,
            start = Offset(tigeFin - rayon * 0.30f, cy),
            end = Offset(tigeFin - rayon * 0.30f, cy + rayon * 0.26f),
            strokeWidth = epais
        )
        drawLine(
            Color.White,
            start = Offset(tigeFin - rayon * 0.04f, cy),
            end = Offset(tigeFin - rayon * 0.04f, cy + rayon * 0.18f),
            strokeWidth = epais
        )

        etincelles(l, cx, cy)
    }
}

/**
 * Écran 3 — un globe entouré des chaînes prises en charge.
 *
 * Les cinq pastilles portent le sigle de chaque monnaie, en vrai texte posé
 * au-dessus du canevas : traduisible, et lisible par un lecteur d'écran.
 */
@Composable
fun IllustrationChaines(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val l = size.minDimension
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = l * 0.19f

            drawCircle(
                brush = Brush.radialGradient(
                    listOf(BleuVif.copy(alpha = 0.30f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = l * 0.5f
                ),
                radius = l * 0.5f,
                center = Offset(cx, cy)
            )

            // Globe : disque plein, deux méridiens et deux parallèles.
            drawCircle(
                brush = Brush.linearGradient(
                    listOf(BleuVif.copy(alpha = 0.75f), Violet.copy(alpha = 0.55f)),
                    start = Offset(cx - r, cy - r),
                    end = Offset(cx + r, cy + r)
                ),
                radius = r,
                center = Offset(cx, cy)
            )
            val trait = Stroke(width = l * 0.007f)
            val clair = Cyan.copy(alpha = 0.55f)
            drawCircle(clair, r, Offset(cx, cy), style = trait)
            for (facteur in listOf(0.42f, 0.78f)) {
                drawOval(
                    color = clair,
                    topLeft = Offset(cx - r * facteur, cy - r),
                    size = Size(r * 2f * facteur, r * 2f),
                    style = trait
                )
            }
            for (dy in listOf(-r * 0.45f, r * 0.45f)) {
                val demi = kotlin.math.sqrt(1f - (dy / r) * (dy / r)) * r
                drawLine(clair, Offset(cx - demi, cy + dy), Offset(cx + demi, cy + dy), trait.width)
            }

            // Orbite en pointillés reliant les cinq chaînes.
            drawCircle(
                color = Cyan.copy(alpha = 0.40f),
                radius = l * 0.36f,
                center = Offset(cx, cy),
                style = Stroke(
                    width = l * 0.006f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(l * 0.025f, l * 0.025f))
                )
            )
        }

        // Pastilles des chaînes, disposées sur l'orbite.
        val chaines = listOf(
            Triple("₿", Color(0xFFF7931A), -90f),
            Triple("Ξ", Color(0xFF627EEA), -18f),
            Triple("S", Color(0xFF14F195), 54f),
            Triple("B", Color(0xFFF3BA2F), 126f),
            Triple("T", Color(0xFFEF3E42), 198f)
        )
        chaines.forEach { (sigle, teinte, angle) ->
            val rad = Math.toRadians(angle.toDouble())
            Box(
                modifier = Modifier
                    .offset(
                        x = (kotlin.math.cos(rad) * 92f).dp,
                        y = (kotlin.math.sin(rad) * 92f).dp
                    )
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(teinte),
                contentAlignment = Alignment.Center
            ) {
                Text(sigle, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        }
    }
}

/**
 * Quelques points lumineux autour du sujet.
 *
 * Détail purement décoratif, repris des maquettes : sur un fond très sombre,
 * il évite l'impression d'un objet posé dans le vide.
 */
private fun DrawScope.etincelles(l: Float, cx: Float, cy: Float) {
    val points = listOf(
        Offset(cx - l * 0.36f, cy - l * 0.28f) to l * 0.010f,
        Offset(cx + l * 0.33f, cy - l * 0.33f) to l * 0.007f,
        Offset(cx + l * 0.38f, cy + l * 0.16f) to l * 0.009f,
        Offset(cx - l * 0.31f, cy + l * 0.30f) to l * 0.006f
    )
    points.forEach { (position, rayon) ->
        drawCircle(Color.White.copy(alpha = 0.55f), rayon, position)
    }
}
