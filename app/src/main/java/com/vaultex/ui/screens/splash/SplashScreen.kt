package com.vaultex.ui.screens.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vaultex.R
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.*
import com.vaultex.ui.viewmodel.SplashViewModel
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SplashScreen(navController: NavHostController) {
    val viewModel: SplashViewModel = hiltViewModel()

    val infiniteTransition = rememberInfiniteTransition(label = "splash_animation")
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 0.96f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1800, easing = EaseInOut), RepeatMode.Reverse),
        label = "scale"
    )
    val loaderPhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "loader"
    )

    LaunchedEffect(Unit) {
        delay(3200)
        // Route vers PIN_UNLOCK si wallet existe, animation de premier lancement sinon
        val destination = if (viewModel.hasWallet()) Routes.PIN_UNLOCK else Routes.FIRST_LAUNCH
        navController.navigate(destination) {
            popUpTo(Routes.SPLASH) { inclusive = true }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SplashNavyTop, SplashNavyBottom))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            // Diamant bleu dans un cercle bordé
            Box(
                modifier = Modifier
                    .size(124.dp)
                    .scale(logoScale)
                    .border(1.dp, AccentBlue.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .rotate(45f)
                        .background(AccentBlue, RoundedCornerShape(6.dp))
                )
            }

            Spacer(Modifier.height(28.dp))

            com.vaultex.ui.components.VaultexWordmark(height = 40.dp)

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.splash_tagline),
                color = TextMuted,
                fontSize = 16.sp
            )

            Spacer(Modifier.height(36.dp))

            // Loader : points bleus en cercle
            DotsLoader(phase = loaderPhase)
        }
    }
}

@Composable
private fun DotsLoader(phase: Float, dotCount: Int = 6) {
    // Lue dans le contexte @Composable, puis utilisée dans le lambda DrawScope.
    val dotColor = AccentBlue
    Canvas(modifier = Modifier.size(96.dp)) {
        val radius = size.minDimension / 2f - 8.dp.toPx()
        val dotRadius = 7.dp.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)
        repeat(dotCount) { i ->
            val angle = Math.toRadians((i * 360.0 / dotCount) - 90.0)
            val pos = Offset(
                x = center.x + radius * cos(angle).toFloat(),
                y = center.y + radius * sin(angle).toFloat()
            )
            val distance = ((i - phase + dotCount) % dotCount) / dotCount
            val alpha = 0.25f + 0.75f * (1f - distance)
            drawCircle(color = dotColor, radius = dotRadius, center = pos, alpha = alpha)
        }
    }
}
