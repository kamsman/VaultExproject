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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.*

import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    navController: NavHostController
) {

    /*
    =========================
    ANIMATIONS
    =========================
     */

    val infiniteTransition = rememberInfiniteTransition(
        label = "logo_animation"
    )

    val logoScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1800,
                easing = EaseInOut
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1600,
                easing = EaseInOut
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    /*
    =========================
    NAVIGATION
    =========================
     */

    LaunchedEffect(Unit) {

        delay(3200)

        navController.navigate(Routes.ONBOARDING) {

            popUpTo(Routes.SPLASH) {
                inclusive = true
            }
        }
    }

    /*
    =========================
    UI
    =========================
     */

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        BgPrimary,
                        BgSecondary
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            /*
            =========================
            LOGO
            =========================
             */

            Text(
                text = "◆",
                color = AccentGold.copy(alpha = glowAlpha),
                fontSize = 120.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .scale(logoScale)
                    .alpha(glowAlpha)
            )

            Spacer(modifier = Modifier.height(26.dp))

            /*
            =========================
            BRAND
            =========================
             */

            Text(
                text = "VaultEx",
                color = TextPrimary,
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Secure Multi-Chain Wallet",
                color = TextSecondary,
                fontSize = 16.sp
            )
        }
    }
}