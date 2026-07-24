package com.vaultex.ui.screens.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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

                com.vaultex.ui.components.VaultexWordmark(height = 34.dp)

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

            Spacer(modifier = Modifier.height(32.dp))

            // ─── Acceptation des CGU (obligatoire avant de créer/importer) ───
            // Preuve de consentement : les deux boutons restent DÉSACTIVÉS tant
            // que la case n'est pas cochée. Les liens ouvrent les documents
            // embarqués (consultables hors connexion).
            var termsAccepted by rememberSaveable { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = termsAccepted,
                    onCheckedChange = { termsAccepted = it },
                    colors = CheckboxDefaults.colors(checkedColor = AccentBlue)
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.welcome_terms_accept),
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = stringResource(R.string.legal_terms_title),
                            color = AccentBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { navController.navigate(Routes.legal("terms")) }
                        )
                        Text(
                            text = stringResource(R.string.legal_privacy_title),
                            color = AccentBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { navController.navigate(Routes.legal("privacy")) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            PrimaryButton(
                text = stringResource(R.string.welcome_create),
                enabled = termsAccepted,
                onClick = {

                    navController.navigate(
                        Routes.MNEMONIC_DISPLAY
                    )
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            SecondaryButton(
                text = stringResource(R.string.welcome_import),
                enabled = termsAccepted,
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
