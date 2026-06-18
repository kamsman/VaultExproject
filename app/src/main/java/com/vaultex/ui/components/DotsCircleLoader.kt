package com.vaultex.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vaultex.ui.theme.AccentBlue
import kotlin.math.cos
import kotlin.math.sin

/**
 * Loader « points bleus en cercle » (même style que l'écran de démarrage).
 * Une lueur tourne autour de l'anneau. Animation autonome.
 */
@Composable
fun DotsCircleLoader(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    dotCount: Int = 7
) {
    val dotColor = AccentBlue
    val transition = rememberInfiniteTransition(label = "dots_loader")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = dotCount.toFloat(),
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "dots_phase"
    )
    Canvas(modifier = modifier.size(size)) {
        val ringRadius = this.size.minDimension / 2f - 6.dp.toPx()
        val dotRadius = this.size.minDimension * 0.07f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        repeat(dotCount) { i ->
            val angle = Math.toRadians((i * 360.0 / dotCount) - 90.0)
            val pos = Offset(
                x = center.x + ringRadius * cos(angle).toFloat(),
                y = center.y + ringRadius * sin(angle).toFloat()
            )
            // Lueur qui tourne : le point le plus proche de la phase est le plus vif.
            val distance = ((i - phase + dotCount) % dotCount) / dotCount
            val alpha = 0.2f + 0.8f * (1f - distance)
            drawCircle(color = dotColor, radius = dotRadius, center = pos, alpha = alpha)
        }
    }
}
