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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vaultex.R
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
                title = { Text(stringResource(R.string.security), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
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
                    title = stringResource(R.string.security_biometric_title),
                    subtitle = if (state.isBiometricEnabled) stringResource(R.string.security_enabled) else stringResource(R.string.security_disabled)
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
                    title = stringResource(R.string.security_auto_lock),
                    subtitle = stringResource(R.string.security_minutes_format, state.autoLockMinutes)
                ) {
                    Box {
                        TextButton(onClick = { expanded = true }) {
                            Text(stringResource(R.string.security_minutes_format, state.autoLockMinutes), color = VaultExColors.BluePrimary)
                            Icon(Icons.Default.ArrowDropDown, null, tint = VaultExColors.BluePrimary)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            autoLockOptions.forEach { minutes ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.security_minutes_format, minutes)) },
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
                    title = stringResource(R.string.security_change_pin),
                    subtitle = stringResource(R.string.security_change_pin_subtitle)
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
                    title = stringResource(R.string.panic_pin_title),
                    subtitle = if (state.hasPanicPin) stringResource(R.string.security_panic_configured) else stringResource(R.string.security_panic_not_configured)
                ) {
                    IconButton(onClick = { navController.navigate(Routes.PANIC_PIN) }) {
                        Icon(Icons.Default.ChevronRight, null, tint = VaultExColors.TextSecondary)
                    }
                }
            }

            // Notifications de sécurité
            SecurityCard {
                SecurityRow(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.notifications),
                    subtitle = stringResource(R.string.security_notifications_subtitle)
                ) {
                    IconButton(onClick = { navController.navigate(Routes.SECURITY_NOTIFICATIONS) }) {
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
