package com.vaultex.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vaultex.ui.theme.AccentBlue
import kotlin.math.cos
import kotlin.math.sin

/*
═══════════════════════════════════════════════════════════════════════════
LOGO ANIMÉ — le losange se forme, l'anneau se referme
═══════════════════════════════════════════════════════════════════════════

Repris d'une animation vidéo de dix secondes : des particules convergent en
losange, un arc lumineux fait le tour jusqu'à fermer l'anneau, puis le nom
apparaît.

TROIS RAISONS DE LA REDESSINER PLUTÔT QUE DE JOUER LA VIDÉO :

· Dix secondes avant d'atteindre son portefeuille, c'est le genre de détail
  qui fait désinstaller. Celle-ci dure 1,2 s, et l'écran de code s'ouvre
  derrière pendant qu'elle finit.
· La vidéo est en 1280×720 paysage et se termine en blanc. Sur un téléphone
  en portrait, dans une application sombre, il aurait fallu des bandes noires
  puis un passage blanc → noir en pleine figure.
· Un fichier vidéo pèse 2,2 Mo, demande un décodeur et porte une piste audio.
  Un portefeuille qui fait du bruit au lancement, en public, est un problème.

Ici : quelques formes vectorielles, nettes à toute résolution, sans fichier.

Le mouvement conserve ce qui fait le sens de l'original — un cercle qui se
REFERME autour d'une forme dit « coffre » sans un mot. C'est pour cela que
l'anneau se dessine par un arc qui progresse, et non par une simple
apparition en fondu.
*/

/*
Durées de chaque temps, en millisecondes.

Allongées deux fois depuis que le chargeur à points a disparu : l'animation
était calibrée pour partager l'écran avec lui, elle porte maintenant le splash
à elle seule.

Le losange est le temps le plus long des trois, et volontairement. C'est lui
que l'on regarde : l'anneau qui suit ne fait que le cercler, l'étincelle ne
fait que ponctuer. Une forme qui se pose doit avoir le temps de se poser,
sinon elle passe pour un défaut d'affichage plutôt que pour un mouvement.
*/
private const val MS_LOSANGE = 1200
private const val MS_ANNEAU = 650
private const val MS_ETINCELLE = 300

/** Total de l'animation, utile à l'appelant pour caler sa navigation. */
const val LOGO_ANIME_DUREE_MS = MS_LOSANGE + MS_ANNEAU + MS_ETINCELLE

/**
 * Logo VaultEx qui se construit sous les yeux.
 *
 * [onTermine] est appelé une fois le dernier temps joué — l'appelant peut
 * enchaîner sans dupliquer les durées écrites ici.
 */
@Composable
fun LogoAnime(
    modifier: Modifier = Modifier,
    taille: Dp = 124.dp,
    onTermine: () -> Unit = {}
) {
    // Trois valeurs animées, jouées l'une après l'autre. Un Animatable par
    // temps plutôt qu'une seule progression découpée : chaque phase garde
    // ainsi sa propre courbe d'accélération.
    val losange = remember { Animatable(0f) }
    val anneau = remember { Animatable(0f) }
    val etincelle = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // EaseOutBack donne le léger dépassement d'échelle qui fait qu'une
        // forme « arrive » au lieu de simplement grossir.
        losange.animateTo(1f, tween(MS_LOSANGE, easing = EaseOutBack))
        anneau.animateTo(1f, tween(MS_ANNEAU, easing = EaseOutCubic))
        etincelle.animateTo(1f, tween(MS_ETINCELLE, easing = LinearEasing))
        onTermine()
    }

    val bleu = AccentBlue

    Box(modifier.size(taille), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(taille)) {
            val centre = Offset(size.width / 2f, size.height / 2f)
            val rayonAnneau = size.minDimension / 2f - 2.dp.toPx()

            /*
            L'ANNEAU, dessiné comme un arc qui progresse.

            Le départ à -90° le fait partir du haut : c'est là que l'œil se
            pose, et le point de fermeture y revient, ce qui rend le tour
            complet lisible.
            */
            if (anneau.value > 0f) {
                drawArc(
                    color = bleu,
                    startAngle = -90f,
                    sweepAngle = 360f * anneau.value,
                    useCenter = false,
                    topLeft = Offset(centre.x - rayonAnneau, centre.y - rayonAnneau),
                    size = Size(rayonAnneau * 2, rayonAnneau * 2),
                    style = Stroke(width = 2.5.dp.toPx())
                )
                // Tête lumineuse en bout d'arc : sans elle, l'anneau semble
                // apparaître plutôt que se tracer.
                if (anneau.value < 1f) {
                    val angle = Math.toRadians((-90f + 360f * anneau.value).toDouble())
                    drawCircle(
                        color = Color.White,
                        radius = 3.5.dp.toPx(),
                        center = Offset(
                            centre.x + rayonAnneau * cos(angle).toFloat(),
                            centre.y + rayonAnneau * sin(angle).toFloat()
                        ),
                        alpha = 0.9f
                    )
                }
            }

            /*
            LE LOSANGE, un carré tourné de 45° aux coins arrondis.

            Il apparaît en grandissant ET en tournant : la rotation part de
            60° pour finir à 45°, ce qui donne l'impression d'un objet qui se
            met en place plutôt que d'une image qui grossit.
            */
            if (losange.value > 0f) {
                val cote = size.minDimension * 0.55f * losange.value
                val angleFinal = 45f + 15f * (1f - losange.value)
                rotate(degrees = angleFinal, pivot = centre) {
                    val demi = cote / 2f
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(bleu.copy(alpha = 0.95f), bleu),
                            start = Offset(centre.x - demi, centre.y - demi),
                            end = Offset(centre.x + demi, centre.y + demi)
                        ),
                        topLeft = Offset(centre.x - demi, centre.y - demi),
                        size = Size(cote, cote),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cote * 0.12f),
                        alpha = losange.value.coerceIn(0f, 1f)
                    )
                }
            }

            /*
            L'ÉTINCELLE FINALE : un halo qui s'ouvre et s'efface.

            Elle ne dure qu'un quart de seconde et ne sert qu'à ponctuer —
            sans elle, l'animation s'arrête, elle ne se termine pas.
            */
            if (etincelle.value > 0f && etincelle.value < 1f) {
                drawCircle(
                    color = bleu,
                    radius = rayonAnneau * (1f + 0.35f * etincelle.value),
                    center = centre,
                    alpha = (1f - etincelle.value) * 0.35f,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}
