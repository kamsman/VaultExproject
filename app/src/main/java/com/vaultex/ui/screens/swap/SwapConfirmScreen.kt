package com.vaultex.ui.screens.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.vaultex.ui.theme.AccentGold
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.BgSecondary
import kotlinx.coroutines.delay

@Composable
fun SwapConfirmScreen(navController: NavHostController)  {

    LaunchedEffect(Unit) {
        delay(1500)

        // ✅ NAVIGATION SIMPLE
        navController.navigate("welcome")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(BgPrimary, BgSecondary)
                )
            ),
        contentAlignment = Alignment.Center
    ) {

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "◆",
                color = AccentGold,
                fontSize = 80.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "VAULTEX",
                color = AccentGold,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Crypto Wallet",
                color = AccentGold.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
        }
    }
}