package com.vaultex.ui.screens.unlock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vaultex.R
import com.vaultex.core.security.BiometricHelper
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.*
import com.vaultex.ui.viewmodel.UnlockViewModel

@Composable
fun UnlockScreen(navController: NavHostController) {
    val viewModel: UnlockViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val activity = LocalContext.current as? FragmentActivity

    // Retour arrière sur l'écran de verrouillage : mettre l'app en arrière-plan
    // (comme le bouton Accueil) SANS détruire l'Activity ni contourner le verrou.
    androidx.activity.compose.BackHandler(enabled = true) {
        activity?.moveTaskToBack(true)
    }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgSecondary)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(72.dp))

        // Diamant bleu + titre
        Box(Modifier.size(52.dp).rotate(45f).clip(RoundedCornerShape(6.dp)).background(AccentBlue))
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.app_name),
            color = TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.unlock_enter_pin), color = TextSecondary, fontSize = 14.sp)

        Spacer(Modifier.height(36.dp))

        // Points du PIN
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(6) { i ->
                Box(
                    modifier = Modifier.size(14.dp).clip(CircleShape)
                        .background(if (i < state.pin.length) AccentBlue else BgTertiary)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Erreur / verrouillage
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
            color = AccentRed,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.height(20.dp)
        )

        Spacer(Modifier.height(14.dp))

        // Pavé numérique — cercles clairs bordés
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val digits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
            digits.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                    row.forEach { d ->
                        if (d.isEmpty()) {
                            Spacer(Modifier.size(70.dp))
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(70.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, BorderColor, CircleShape)
                                    .background(BgSecondary)
                                    .clickable(enabled = state.lockedSeconds == 0L) {
                                        if (d == "⌫") viewModel.onBackspace() else viewModel.onDigit(d)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(d, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Biométrie — icône d'empreinte (au lieu d'un texte)
        if (viewModel.isBiometricEnabled() && activity != null) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .border(1.dp, BorderColor, CircleShape)
                    .clickable {
                        BiometricHelper(activity).authenticate(
                            title = activity.getString(R.string.biometric_title),
                            subtitle = activity.getString(R.string.biometric_subtitle),
                            onSuccess = { viewModel.onBiometricSuccess() },
                            onError = { _, _ -> }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = stringResource(R.string.unlock_use_fingerprint),
                    tint = AccentBlue,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
