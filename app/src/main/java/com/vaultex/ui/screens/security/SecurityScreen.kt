package com.vaultex.ui.screens.security

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vaultex.R
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.AccentRed
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.Surface
import com.vaultex.ui.theme.SurfaceLight
import com.vaultex.ui.theme.TextMuted
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary
import com.vaultex.ui.viewmodel.SecurityViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(navController: NavHostController) {
    val viewModel: SecurityViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    val autoLockOptions = listOf(1, 5, 15, 30, 60)
    var showWipeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.security),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = AccentBlue
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgPrimary)
            )
        },
        containerColor = BgPrimary
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // ─── Accès ─────────────────────────────────────────────
            SecuritySectionLabel(stringResource(R.string.security_section_access))
            SecuritySectionCard {
                SecurityRow(
                    icon = Icons.Default.Pin,
                    title = stringResource(R.string.security_change_pin),
                    onClick = { navController.navigate(Routes.PIN_SETUP) }
                ) {
                    Icon(Icons.Default.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(20.dp))
                }
                SecurityRowDivider()
                SecurityRow(
                    icon = Icons.Default.Fingerprint,
                    title = stringResource(R.string.security_biometric_title)
                ) {
                    Switch(
                        checked = state.isBiometricEnabled,
                        onCheckedChange = { viewModel.setBiometricEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                            checkedTrackColor = AccentBlue,
                            uncheckedThumbColor = androidx.compose.ui.graphics.Color.White,
                            uncheckedTrackColor = SurfaceLight
                        )
                    )
                }
                SecurityRowDivider()
                var expanded by remember { mutableStateOf(false) }
                SecurityRow(
                    icon = Icons.Default.Timer,
                    title = stringResource(R.string.security_auto_lock),
                    onClick = { expanded = true }
                ) {
                    Box {
                        Text(
                            stringResource(R.string.security_minutes_format, state.autoLockMinutes),
                            fontSize = 14.sp,
                            color = TextSecondary
                        )
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

            Spacer(Modifier.height(20.dp))

            // ─── Protection ────────────────────────────────────────
            SecuritySectionLabel(stringResource(R.string.security_section_protection))
            SecuritySectionCard {
                SecurityRow(
                    icon = Icons.Default.Shield,
                    title = stringResource(R.string.panic_pin_title),
                    onClick = { navController.navigate(Routes.PANIC_PIN) }
                ) {
                    Icon(Icons.Default.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(20.dp))
                }
                SecurityRowDivider()
                SecurityRow(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.security_max_attempts)
                ) {
                    Text(MAX_PIN_ATTEMPTS.toString(), fontSize = 14.sp, color = TextSecondary)
                }
                SecurityRowDivider()
                SecurityRow(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.notif_security_title),
                    onClick = { navController.navigate(Routes.SECURITY_NOTIFICATIONS) }
                ) {
                    Icon(Icons.Default.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(20.dp))

            // ─── Avancé ────────────────────────────────────────────
            SecuritySectionLabel(stringResource(R.string.security_section_advanced))
            SecuritySectionCard {
                SecurityRow(
                    icon = Icons.Default.DeleteForever,
                    title = stringResource(R.string.security_wipe_wallet),
                    iconTint = AccentRed,
                    titleColor = AccentRed,
                    onClick = { showWipeDialog = true }
                ) {
                    Icon(Icons.Default.ChevronRight, null, tint = AccentRed, modifier = Modifier.size(20.dp))
                }
            }
        }
    }

    if (showWipeDialog) {
        AlertDialog(
            onDismissRequest = { showWipeDialog = false },
            title = { Text(stringResource(R.string.security_wipe_wallet), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.wallet_mgr_delete_warning)) },
            confirmButton = {
                TextButton(onClick = { showWipeDialog = false }) {
                    Text(stringResource(R.string.confirm), color = AccentRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeDialog = false }) {
                    Text(stringResource(R.string.cancel), color = TextSecondary)
                }
            },
            containerColor = Surface
        )
    }
}

/** Aligné sur PinManager.MAX_ATTEMPTS (valeur informative). */
private const val MAX_PIN_ATTEMPTS = 5

@Composable
private fun SecuritySectionLabel(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextMuted,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 8.dp)
    )
}

@Composable
private fun SecuritySectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

@Composable
private fun SecurityRowDivider() {
    HorizontalDivider(color = SurfaceLight, thickness = 1.dp)
}

@Composable
private fun SecurityRow(
    icon: ImageVector,
    title: String,
    iconTint: androidx.compose.ui.graphics.Color = AccentBlue,
    titleColor: androidx.compose.ui.graphics.Color = TextPrimary,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            title,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = titleColor,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}
