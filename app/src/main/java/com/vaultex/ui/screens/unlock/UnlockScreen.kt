package com.vaultex.ui.screens.unlock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vaultex.R
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.*
import com.vaultex.ui.viewmodel.UnlockViewModel

@Composable
fun UnlockScreen(navController: NavHostController) {
    val viewModel: UnlockViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    // Navigation post-unlock
    LaunchedEffect(state.isUnlocked) {
        if (state.isUnlocked) {
            navController.navigate(Routes.DASHBOARD) { popUpTo(0) { inclusive = true } }
        }
    }
    // Panic PIN → reset complet
    LaunchedEffect(state.panicTriggered) {
        if (state.panicTriggered) {
            navController.navigate(Routes.WELCOME) { popUpTo(0) { inclusive = true } }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SplashNavyTop, SplashNavyBottom))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize().padding(24.dp)
        ) {
            Spacer(Modifier.height(48.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("◆", color = AccentBlue, fontSize = 64.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.app_name), color = TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.pin_title), color = TextSecondary, fontSize = 15.sp)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Dots
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(6) { i ->
                        Box(
                            modifier = Modifier.size(14.dp).clip(CircleShape)
                                .background(if (i < state.pin.length) AccentBlue else Color.White.copy(alpha = 0.25f))
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Error / locked message
                val msg = when {
                    state.lockedSeconds > 0 -> {
                        val min = state.lockedSeconds / 60
                        val sec = state.lockedSeconds % 60
                        val duration = if (min > 0) "${min}min ${sec}s" else "${sec}s"
                        stringResource(R.string.pin_locked, duration)
                    }
                    state.error != null -> state.error
                    else -> ""
                }
                Text(
                    msg ?: "",
                    color = Color(0xFFEF4444),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.height(20.dp)
                )
            }

            // Numpad
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val digits = listOf("1","2","3","4","5","6","7","8","9","","0","⌫")
                digits.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        row.forEach { d ->
                            if (d.isEmpty()) {
                                Spacer(Modifier.size(72.dp))
                            } else {
                                Button(
                                    onClick = { if (d == "⌫") viewModel.onBackspace() else viewModel.onDigit(d) },
                                    modifier = Modifier.size(72.dp),
                                    shape = CircleShape,
                                    enabled = state.lockedSeconds == 0L,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White.copy(alpha = 0.12f),
                                        disabledContainerColor = Color.White.copy(alpha = 0.05f)
                                    )
                                ) {
                                    Text(d, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
