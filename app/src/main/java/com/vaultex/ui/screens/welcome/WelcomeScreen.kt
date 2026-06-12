package com.vaultex.ui.screens.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

import com.vaultex.R
import com.vaultex.ui.components.PrimaryButton
import com.vaultex.ui.components.SecondaryButton
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.*

@Composable
fun WelcomeScreen(
    navController: NavHostController
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgSecondary)
    ) {

        /*
        =========================
        HAUT : DÉGRADÉ NAVY
        =========================
         */

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(SplashNavyTop, SplashNavyBottom)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {

            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                // Diamant bleu
                Box(
                    modifier = Modifier.size(72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .rotate(45f)
                            .background(AccentBlue, RoundedCornerShape(5.dp))
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.app_name),
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.welcome_tagline),
                    color = TextMuted,
                    fontSize = 14.sp
                )
            }
        }

        /*
        =========================
        BAS : FOND BLANC
        =========================
         */

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(modifier = Modifier.height(56.dp))

            Text(
                text = stringResource(R.string.welcome_chains),
                color = TextSecondary,
                fontSize = 14.sp,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            PrimaryButton(
                text = stringResource(R.string.welcome_create),
                onClick = {

                    navController.navigate(
                        Routes.MNEMONIC_DISPLAY
                    )
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            SecondaryButton(
                text = stringResource(R.string.welcome_import),
                onClick = {

                    navController.navigate(
                        Routes.IMPORT_WALLET
                    )
                }
            )

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
