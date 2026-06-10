package com.vaultex.ui.screens.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultex.ui.components.VaultExDiamond
import com.vaultex.ui.theme.VaultExColors
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
            .background(VaultExColors.SplashBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            VaultExDiamond(
                modifier = Modifier
                    .size(100.dp)
                    .scale(scale.value)
                    .alpha(alpha.value)
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = buildVaultExWordmark(),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(textAlpha.value)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Non-Custodial · Multi-Chain",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(textAlpha.value)
            )
        }
    }
}

private fun buildVaultExWordmark(): String = "Vaultξx"
