package com.vaultex.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Donut chart simple (anneau de parts) dessiné au Canvas, sans dépendance
 * externe. Chaque part = (couleur, valeur). Les valeurs sont normalisées.
 */
@Composable
fun DonutChart(
    slices: List<Pair<Color, Float>>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 18.dp
) {
    val total = slices.sumOf { it.second.toDouble() }.toFloat()
    if (total <= 0f) return
    Canvas(modifier) {
        val sw = strokeWidth.toPx()
        val diameter = size.minDimension - sw
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)
        val stroke = Stroke(width = sw, cap = StrokeCap.Butt)
        var startAngle = -90f
        slices.forEach { (color, value) ->
            if (value > 0f) {
                val sweep = 360f * (value / total)
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweep - 1.5f, // léger espace entre les parts
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke
                )
                startAngle += sweep
            }
        }
    }
}
