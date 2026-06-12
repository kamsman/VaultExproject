package com.vaultex.ui.screens.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultex.R
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.SplashNavyBottom
import com.vaultex.ui.theme.SplashNavyTop
import com.vaultex.ui.theme.TextMuted
import kotlinx.coroutines.delay

@Composable
fun FirstLaunchSplash(onFinished: () -> Unit) {
    val scale = remember { Animatable(0.3f) }
    val alpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Diamond entrance
        scale.animateTo(1f, animationSpec = spring(dampingRatio = 0.6f, stiffness = 200f))
        alpha.animateTo(1f, animationSpec = tween(400))
        textAlpha.animateTo(1f, animationSpec = tween(500))
        delay(1800)
        // Fade out
        alpha.animateTo(0f, animationSpec = tween(400))
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SplashNavyTop, SplashNavyBottom))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Diamant bleu
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .scale(scale.value)
                    .alpha(alpha.value),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .rotate(45f)
                        .background(AccentBlue, RoundedCornerShape(6.dp))
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(textAlpha.value)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.first_launch_tagline),
                fontSize = 13.sp,
                color = TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(textAlpha.value)
            )
        }
    }
}
