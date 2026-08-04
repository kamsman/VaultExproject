package com.vaultex.ui.screens.security

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
import com.vaultex.ui.viewmodel.ChangePinVerifyViewModel

/**
 * Confirme le PIN ACTUEL (ou la biométrie) avant une action sensible. Générique :
 * l'appelant décide de la suite via [onVerified] (changer le PIN, ajouter un
 * wallet…). Panique → l'app a été effacée → WELCOME (toujours interne).
 *
 * [title]/[subtitle] permettent d'adapter le message selon le contexte.
 */
@Composable
fun ChangePinVerifyScreen(
    navController: NavHostController,
    title: String? = null,
    subtitle: String? = null,
    onVerified: () -> Unit = {
        navController.navigate(Routes.PIN_SETUP) { popUpTo(Routes.SECURITY) { inclusive = false } }
    }
) {
    val viewModel: ChangePinVerifyViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val activity = LocalContext.current as? FragmentActivity

    LaunchedEffect(state.verified) {
        if (state.verified) onVerified()
    }
    LaunchedEffect(state.panicTriggered) {
        if (state.panicTriggered) {
            navController.navigate(Routes.WELCOME) { popUpTo(0) { inclusive = true } }
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
        modifier = Modifier.fillMaxSize().background(BgSecondary).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(0.55f))

        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Shield, null, tint = AccentBlue.copy(alpha = 0.14f), modifier = Modifier.size(104.dp))
            Icon(Icons.Filled.Lock, null, tint = AccentBlue, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title ?: stringResource(R.string.change_pin_verify_title), color = AccentBlue, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(subtitle ?: stringResource(R.string.change_pin_verify_subtitle), color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)

        Spacer(Modifier.height(26.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            repeat(6) { i ->
                val filled = i < state.pin.length
                Box(
                    Modifier.size(18.dp).clip(CircleShape)
                        .background(if (filled) AccentBlue else Color.Transparent)
                        .border(1.5.dp, AccentBlue.copy(alpha = if (filled) 1f else 0.45f), CircleShape)
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        val msg = when {
            state.lockedSeconds > 0 -> {
                val min = state.lockedSeconds / 60; val sec = state.lockedSeconds % 60
                stringResource(R.string.pin_locked, if (min > 0) "${min}min ${sec}s" else "${sec}s")
            }
            state.error != null -> state.error
            else -> ""
        }
        Text(msg ?: "", color = AccentRed, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.height(20.dp))

        Spacer(Modifier.height(18.dp))

        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("bio", "0", "del"))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                    row.forEach { key ->
                        when (key) {
                            "bio" -> if (bioEnabled) {
                                PinKey(onClick = { launchBiometric() }) {
                                    Icon(Icons.Filled.Fingerprint, null, tint = AccentBlue, modifier = Modifier.size(28.dp))
                                }
                            } else Spacer(Modifier.size(72.dp))
                            "del" -> PinKey(enabled = state.pin.isNotEmpty(), onClick = { viewModel.onBackspace() }) {
                                Icon(Icons.Filled.Backspace, null, tint = AccentBlue, modifier = Modifier.size(24.dp))
                            }
                            else -> PinKey(enabled = state.lockedSeconds == 0L, onClick = { viewModel.onDigit(key) }) {
                                Text(key, color = AccentBlue, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun PinKey(enabled: Boolean = true, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.size(72.dp).clip(CircleShape)
            .border(1.dp, AccentBlue.copy(alpha = 0.35f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}
