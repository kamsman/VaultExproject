package com.vaultex.ui.screens.security

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.VaultExColors
import com.vaultex.ui.viewmodel.SecurityViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(navController: NavHostController) {
    val viewModel: SecurityViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    val autoLockOptions = listOf(1, 5, 15, 30, 60)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sécurité", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Biometric
            SecurityCard {
                SecurityRow(
                    icon = Icons.Default.Fingerprint,
                    title = "Déverrouillage biométrique",
                    subtitle = if (state.isBiometricEnabled) "Activé" else "Désactivé"
                ) {
                    Switch(
                        checked = state.isBiometricEnabled,
                        onCheckedChange = { viewModel.setBiometricEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = VaultExColors.BluePrimary)
                    )
                }
            }

            // Auto-lock
            SecurityCard {
                var expanded by remember { mutableStateOf(false) }
                SecurityRow(
                    icon = Icons.Default.Timer,
                    title = "Verrouillage automatique",
                    subtitle = "${state.autoLockMinutes} min"
                ) {
                    Box {
                        TextButton(onClick = { expanded = true }) {
                            Text("${state.autoLockMinutes} min", color = VaultExColors.BluePrimary)
                            Icon(Icons.Default.ArrowDropDown, null, tint = VaultExColors.BluePrimary)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            autoLockOptions.forEach { minutes ->
                                DropdownMenuItem(
                                    text = { Text("$minutes min") },
                                    onClick = { viewModel.setAutoLockMinutes(minutes); expanded = false }
                                )
                            }
                        }
                    }
                }
            }

            // Change PIN
            SecurityCard {
                SecurityRow(
                    icon = Icons.Default.Pin,
                    title = "Changer le PIN",
                    subtitle = "Modifier votre code PIN actuel"
                ) {
                    IconButton(onClick = { navController.navigate(Routes.PIN_SETUP) }) {
                        Icon(Icons.Default.ChevronRight, null, tint = VaultExColors.TextSecondary)
                    }
                }
            }

            // Panic PIN
            SecurityCard {
                SecurityRow(
                    icon = Icons.Default.Warning,
                    title = "PIN de panique",
                    subtitle = if (state.hasPanicPin) "Configuré" else "Non configuré — efface toutes les données"
                ) {
                    IconButton(onClick = { navController.navigate(Routes.PANIC_PIN) }) {
                        Icon(Icons.Default.ChevronRight, null, tint = VaultExColors.TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = VaultExColors.CardBackground),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(4.dp), content = content)
    }
}

@Composable
private fun SecurityRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = VaultExColors.BluePrimary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(subtitle, fontSize = 12.sp, color = VaultExColors.TextSecondary)
        }
        trailing()
    }
}
