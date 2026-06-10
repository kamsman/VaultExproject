package com.vaultex.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.VaultExColors
import com.vaultex.ui.viewmodel.BiometricSetupViewModel

@Composable
fun BiometricSetupScreen(navController: NavController) {
    val viewModel: BiometricSetupViewModel = hiltViewModel()

    fun goToDashboard() {
        navController.navigate(Routes.DASHBOARD) {
            popUpTo(navController.graph.startDestinationId) { inclusive = true }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Fingerprint,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = VaultExColors.BluePrimary
        )
        Spacer(Modifier.height(32.dp))
        Text("Activer la biométrie", fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "Utilisez votre empreinte digitale ou Face ID pour déverrouiller VaultEx rapidement et en toute sécurité.",
            fontSize = 15.sp,
            color = VaultExColors.TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = {
                viewModel.enableBiometric()   // ← sauvegarde dans SecureStorage
                goToDashboard()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VaultExColors.BluePrimary)
        ) {
            Text("Activer la biométrie", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { goToDashboard() }) {
            Text("Ignorer pour l'instant", color = VaultExColors.TextSecondary)
        }
    }
}
