package com.vaultex.ui.screens.security

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

import com.vaultex.R
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.*
import com.vaultex.ui.viewmodel.OnboardingViewModel

@Composable
fun PinSetupScreen(
    navController: NavHostController,
    viewModel: OnboardingViewModel
) {
    // Phase 1 : saisie du PIN — Phase 2 : confirmation
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var confirmPhase by remember { mutableStateOf(false) }
    var mismatch by remember { mutableStateOf(false) }

    val saveState by viewModel.saveState.collectAsState()

    // Navigation automatique quand la sauvegarde réussit :
    // proposer la biométrie avant d'entrer dans l'app
    LaunchedEffect(saveState) {
        if (saveState is OnboardingViewModel.SaveState.Success) {
            viewModel.resetSaveState()
            // `inclusive = true` est INDISPENSABLE. Sans lui, `popUpTo(0)` ne
            // supprime rien : Android cherche une destination d'identifiant 0,
            // n'en trouve aucune, et abandonne en silence. La pile conservait
            // donc ONBOARDING et PIN_SETUP, et le bouton retour du téléphone
            // ramenait l'utilisateur sur la création de PIN — puis sur l'écran
            // d'installation — alors que son portefeuille venait d'être créé.
            navController.navigate(Routes.BIOMETRIC_SETUP) { popUpTo(0) { inclusive = true } }
        }
    }

    val isLoading = saveState is OnboardingViewModel.SaveState.Loading

    // Passage de phase / validation (PIN 6 chiffres)
    LaunchedEffect(pin) {
        if (!confirmPhase && pin.length == 6) confirmPhase = true
    }
    LaunchedEffect(confirmPin) {
        if (confirmPin.length == 6) {
            if (confirmPin == pin) {
                mismatch = false
                // Sauvegarde mnémonique chiffrée + PIN hashé PBKDF2 → Biométrie
                viewModel.saveWallet(pin)
            } else {
                mismatch = true
                pin = ""
                confirmPin = ""
                confirmPhase = false
            }
        }
    }

    val currentEntry = if (confirmPhase) confirmPin else pin

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgSecondary)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(modifier = Modifier.height(72.dp))

        // Diamant bleu
        Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .rotate(45f)
                    .background(AccentBlue, RoundedCornerShape(4.dp))
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (confirmPhase)
                stringResource(R.string.pin_confirm)
            else
                stringResource(R.string.pin_setup_create_title),
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.pin_setup_protect_subtitle),
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(52.dp))

        // 6 points
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(6) { i ->
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (i < currentEntry.length) AccentBlue else BgTertiary)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Messages d'état
        val message: String? = when {
            isLoading -> stringResource(R.string.pin_setup_saving)
            mismatch -> stringResource(R.string.panic_mismatch)
            saveState is OnboardingViewModel.SaveState.Error ->
                stringResource(
                    R.string.pin_setup_error,
                    (saveState as OnboardingViewModel.SaveState.Error).message
                )
            else -> null
        }
        Box(modifier = Modifier.height(22.dp), contentAlignment = Alignment.Center) {
            when {
                isLoading -> CircularProgressIndicator(
                    color = AccentBlue,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
                message != null -> Text(
                    text = message,
                    color = AccentRed,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Pavé numérique
        PinNumPad(
            enabled = !isLoading,
            onDigit = { d ->
                if (confirmPhase) {
                    if (confirmPin.length < 6) confirmPin += d
                } else {
                    if (pin.length < 6) pin += d
                }
                mismatch = false
            },
            onBackspace = {
                if (confirmPhase) {
                    if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
                } else {
                    if (pin.isNotEmpty()) pin = pin.dropLast(1)
                }
            }
        )

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun PinNumPad(
    enabled: Boolean,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                row.forEach { key ->
                    if (key.isEmpty()) {
                        Spacer(Modifier.size(70.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .clip(CircleShape)
                                .background(BgSecondary)
                                .border(1.dp, BorderColor, CircleShape)
                                .clickable(enabled = enabled) {
                                    if (key == "⌫") onBackspace() else onDigit(key)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (key == "⌫") {
                                Icon(
                                    imageVector = Icons.Outlined.Backspace,
                                    contentDescription = stringResource(R.string.back),
                                    tint = TextPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Text(
                                    text = key,
                                    color = TextPrimary,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
