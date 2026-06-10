package com.vaultex.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vaultex.ui.theme.VaultExColors
import com.vaultex.ui.viewmodel.NetworkSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkSettingsScreen(navController: NavHostController) {
    val viewModel: NetworkSettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paramètres réseau", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::reset) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Réinitialiser", tint = VaultExColors.TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultExColors.Background)
            )
        },
        containerColor = VaultExColors.Background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Endpoints RPC (mainnet)",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = VaultExColors.TextSecondary
            )

            state.entries.forEach { entry ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = VaultExColors.CardBackground),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(entry.label, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        OutlinedTextField(
                            value = entry.current,
                            onValueChange = { viewModel.setUrl(entry.chain, it) },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VaultExColors.BluePrimary)
            ) {
                Text(if (state.saved) "Enregistré ✓" else "Enregistrer")
            }

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = VaultExColors.BlueLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Les modifications prennent effet immédiatement pour les nouvelles requêtes.",
                    fontSize = 12.sp,
                    color = VaultExColors.BluePrimary,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
