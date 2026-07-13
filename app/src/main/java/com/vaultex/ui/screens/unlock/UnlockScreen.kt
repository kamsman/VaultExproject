package com.vaultex.ui.screens.unlock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    // Panic PIN → reset complet PUIS redémarrage du process. Naviguer vers
    // l'accueil dans le MÊME process laissait des singletons morts (base Room
    // supprimée mais ouverte, prefs liées à la clé détruite) : tout ré-import
    // de seed échouait ensuite. Un process neuf répare tout ça.
    val appCtx = LocalContext.current.applicationContext
    LaunchedEffect(state.panicTriggered) {
        if (state.panicTriggered) {
            com.vaultex.core.session.AppRestart.restart(appCtx)
        }
    }

    val bioEnabled = viewModel.isBiometricEnabled() && activity != null
    fun launchBiometric() {
        if (activity == null) return
        BiometricHelper(activity).authenticate(
            title = activity.getString(R.string.biometric_title),
            subtitle = activity.getString(R.string.biometric_subtitle),
            onSuccess = { viewModel.onBiometricSuccess() },
            onError = { _, _ -> }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgSecondary)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(0.6f))

        // ─── Bouclier + cadenas (maquette) ───
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.Shield,
                contentDescription = null,
                tint = AccentBlue.copy(alpha = 0.14f),
                modifier = Modifier.size(104.dp)
            )
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier.size(42.dp)
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.unlock_enter_pin),
            color = AccentBlue,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(28.dp))

        // ─── 6 cercles (vides = bordés, remplis = pleins) ───
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            repeat(6) { i ->
                val filled = i < state.pin.length
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (filled) AccentBlue else Color.Transparent)
                        .border(1.5.dp, AccentBlue.copy(alpha = if (filled) 1f else 0.45f), CircleShape)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ─── Erreur / verrouillage ───
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

        Spacer(Modifier.height(20.dp))

        // ─── Pavé numérique 3×4 (empreinte · 0 · effacer sur la dernière ligne) ───
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("bio", "0", "del")
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                    row.forEach { key ->
                        when (key) {
                            "bio" -> {
                                if (bioEnabled) {
                                    PinKey(onClick = { launchBiometric() }) {
                                        Icon(
                                            Icons.Filled.Fingerprint,
                                            contentDescription = stringResource(R.string.unlock_use_fingerprint),
                                            tint = AccentBlue,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                } else {
                                    Spacer(Modifier.size(72.dp))
                                }
                            }
                            "del" -> {
                                PinKey(
                                    enabled = state.pin.isNotEmpty(),
                                    onClick = { viewModel.onBackspace() }
                                ) {
                                    Icon(
                                        Icons.Filled.Backspace,
                                        contentDescription = stringResource(R.string.back),
                                        tint = AccentBlue,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            else -> {
                                PinKey(
                                    enabled = state.lockedSeconds == 0L,
                                    onClick = { viewModel.onDigit(key) }
                                ) {
                                    Text(key, color = AccentBlue, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // ─── Pied de page « vaultex wallet » ───
        Text(
            stringResource(R.string.app_name).lowercase() + " wallet",
            color = TextMuted,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(20.dp))
    }
}

/** Touche ronde bordée du pavé PIN (maquette : cercle bleu clair, fond transparent). */
@Composable
private fun PinKey(
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .border(1.dp, AccentBlue.copy(alpha = 0.35f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
