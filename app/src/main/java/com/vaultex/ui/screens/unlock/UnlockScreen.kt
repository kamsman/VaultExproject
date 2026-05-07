package com.vaultex.ui.screens.unlock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.AccentGold
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.BgSecondary
import com.vaultex.ui.theme.TextPrimary

@Composable
fun UnlockScreen(
    navController: NavHostController
) {

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
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

            Text(
                text = "VaultEx",
                color = AccentGold,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Déverrouillez votre wallet",
                color = TextPrimary,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = {
                    navController.navigate(
                        Routes.DASHBOARD
                    ) {
                        popUpTo(0)
                    }
                }
            ) {

                Text("Unlock")
            }
        }
    }
}