package com.vaultex.ui.screens.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vaultex.R
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.BgSecondary
import com.vaultex.ui.theme.BgTertiary
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary
import com.vaultex.ui.viewmodel.BiometricSetupViewModel

@Composable
fun BiometricSetupScreen(navController: NavController) {
    val viewModel: BiometricSetupViewModel = hiltViewModel()

    fun goToDashboard() {
        /*
         * `popUpTo(0)` et NON `popUpTo(graph.startDestinationId)`.
         *
         * Remonter jusqu'à la destination de départ suppose qu'elle soit
         * encore dans la pile. Ce n'est plus le cas : l'étape précédente
         * (PinSetupScreen) l'a entièrement vidée. Android ne trouve alors pas
         * la destination visée, abandonne en silence, et ne supprime rien —
         * la pile devenait BIOMETRIC_SETUP → DASHBOARD, et le bouton retour
         * du téléphone ramenait sur l'écran de biométrie, déjà traité.
         *
         * `popUpTo(0) { inclusive = true }` ne dépend d'aucune destination
         * existante : il vide la pile dans tous les cas. C'est la forme à
         * utiliser pour un point d'entrée définitif comme le tableau de bord.
         */
        navController.navigate(Routes.DASHBOARD) {
            popUpTo(0) { inclusive = true }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgSecondary)
            .systemBarsPadding()
    ) {
        // ── Barre supérieure : retour + titre centré ─────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            // Retour = entrer dans l'application, PAS revenir en arrière.
            //
            // À ce stade le portefeuille est créé et le PIN enregistré : il n'y
            // a plus rien derrière. Un `popBackStack()` ramenait l'utilisateur
            // sur la création de PIN, ou sur l'écran d'installation, selon ce
            // qui trainait encore dans la pile. La biométrie étant facultative,
            // ce bouton doit se comporter comme « passer cette étape ».
            IconButton(
                onClick = { goToDashboard() },
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = AccentBlue
                )
            }
            Text(
                stringResource(R.string.biometric_screen_title),
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))

            // ── Illustration ─────────────────────────────────
            Box(
                modifier = Modifier
                    .size(116.dp)
                    .background(BgTertiary, RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Fingerprint,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = AccentBlue
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(
                stringResource(R.string.biometric_enable),
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.biometric_setup_quick_unlock),
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 21.sp
            )

            Spacer(Modifier.weight(1.3f))

            // ── Actions ──────────────────────────────────────
            Button(
                onClick = {
                    viewModel.enableBiometric()   // ← sauvegarde dans SecureStorage
                    goToDashboard()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text(
                    stringResource(R.string.biometric_activate),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(14.dp))
            OutlinedButton(
                onClick = { goToDashboard() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.5.dp, AccentBlue),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue)
            ) {
                Text(
                    stringResource(R.string.biometric_later),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(160.dp))
        }
    }
}
