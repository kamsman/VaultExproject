package com.vaultex.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vaultex.ui.theme.VaultExColors

@Composable
fun VaultExDiamond(
    modifier: Modifier = Modifier,
    size: Dp = 80.dp
) {
    Canvas(modifier = modifier.size(size)) {
        drawDiamond3D(this)
    }
}

private fun drawDiamond3D(scope: DrawScope) {
    with(scope) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val topY = h * 0.05f
        val midY = h * 0.40f
        val botY = h * 0.95f

        // Top facet (light)
        val topPath = Path().apply {
            moveTo(cx, topY)
            lineTo(w * 0.82f, midY)
            lineTo(cx, midY * 1.1f)
            lineTo(w * 0.18f, midY)
            close()
        }
        drawPath(topPath, brush = Brush.linearGradient(
            colors = listOf(VaultExColors.Gold, Color(0xFFFFD700)),
            start = Offset(cx, topY),
            end = Offset(cx, midY)
        ))

        // Left facet
        val leftPath = Path().apply {
            moveTo(w * 0.18f, midY)
            lineTo(cx, midY * 1.1f)
            lineTo(cx, botY)
            close()
        }
        drawPath(leftPath, brush = Brush.linearGradient(
            colors = listOf(Color(0xFFB8860B), VaultExColors.Gold),
            start = Offset(w * 0.18f, midY),
            end = Offset(cx, botY)
        ))

        // Right facet
        val rightPath = Path().apply {
            moveTo(w * 0.82f, midY)
            lineTo(cx, midY * 1.1f)
            lineTo(cx, botY)
            close()
        }
        drawPath(rightPath, brush = Brush.linearGradient(
            colors = listOf(VaultExColors.Gold, Color(0xFFDAA520)),
            start = Offset(w * 0.82f, midY),
            end = Offset(cx, botY)
        ))
    }
}
