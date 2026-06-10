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

@Composable
fun SplashScreen(navController: NavHostController) {
    val viewModel: SplashViewModel = hiltViewModel()

    val infiniteTransition = rememberInfiniteTransition(label = "logo_animation")
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1800, easing = EaseInOut), RepeatMode.Reverse),
        label = "scale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = EaseInOut), RepeatMode.Reverse),
        label = "alpha"
    )

    LaunchedEffect(Unit) {
        delay(3200)
        // Route vers PIN_UNLOCK si wallet existe, ONBOARDING sinon
        val destination = if (viewModel.hasWallet()) Routes.PIN_UNLOCK else Routes.ONBOARDING
        navController.navigate(destination) {
            popUpTo(Routes.SPLASH) { inclusive = true }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgPrimary, BgSecondary))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "◆",
                color = AccentGold.copy(alpha = glowAlpha),
                fontSize = 120.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.scale(logoScale).alpha(glowAlpha)
            )
            Spacer(Modifier.height(26.dp))
            Text(stringResource(R.string.app_name), color = TextPrimary, fontSize = 38.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.tagline), color = TextSecondary, fontSize = 16.sp)
        }
    }
}
