package com.vaultex.ui.screens.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
            )
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),

            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            /*
            =========================
            TOP SPACE
            =========================
             */

            Spacer(
                modifier = Modifier.height(80.dp)
            )

            /*
            =========================
            LOGO
            =========================
             */

            Text(
                text = "◆",
                color = AccentBlue,
                fontSize = 100.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(40.dp)
            )

            /*
            =========================
            TITLE
            =========================
             */

            Text(
                text = stringResource(R.string.app_tagline),
                color = TextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            /*
            =========================
            DESCRIPTION
            =========================
             */

            Text(
                text = stringResource(R.string.welcome_description),

                color = TextSecondary,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(
                modifier = Modifier.weight(1f)
            )

            /*
            =========================
            CREATE WALLET
            =========================
             */

            PrimaryButton(
                text = stringResource(R.string.create_wallet),
                onClick = {

                    navController.navigate(
                        Routes.MNEMONIC_DISPLAY
                    )
                }
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            /*
            =========================
            IMPORT WALLET
            =========================
             */

            SecondaryButton(
                text = stringResource(R.string.import_wallet),
                onClick = {

                    navController.navigate(
                        Routes.IMPORT_WALLET
                    )
                }
            )

            Spacer(
                modifier = Modifier.height(24.dp)
            )
        }
    }
}