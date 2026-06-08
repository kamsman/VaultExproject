package com.vaultex.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vaultex.ui.theme.VaultExColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun BlockchainHexLoader(
    modifier: Modifier = Modifier,
    size: Dp = 60.dp,
    color: Color = VaultExColors.BluePrimary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hex_loader")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Canvas(modifier = modifier.size(size)) {
        val cx = this.size.width / 2
        val cy = this.size.height / 2
        val radius = this.size.minDimension / 2.2f
        val strokeWidth = this.size.minDimension * 0.08f

        val hexPath = Path()
        for (i in 0..5) {
            val angle = (i * 60.0 + rotation) * PI / 180.0
            val x = cx + radius * cos(angle).toFloat()
            val y = cy + radius * sin(angle).toFloat()
            if (i == 0) hexPath.moveTo(x, y) else hexPath.lineTo(x, y)
        }
        hexPath.close()

        drawPath(
            path = hexPath,
            color = color.copy(alpha = alpha),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Center dot
        drawCircle(color = VaultExColors.Gold, radius = this.size.minDimension * 0.08f, center = Offset(cx, cy))
    }
}
