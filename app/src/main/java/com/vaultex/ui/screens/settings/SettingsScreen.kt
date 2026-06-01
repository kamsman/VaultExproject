package com.vaultex.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController

import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.*
import com.vaultex.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavHostController) {

    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    var showPanicPinDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paramètres", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgPrimary)
            )
        },
        containerColor = BgPrimary
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(BgPrimary, BgSecondary)))
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Sécurité ──────────────────────────────────────────────────────
            SectionHeader("Sécurité")

            SettingsCard {
                SettingsSwitch(
                    icon = Icons.Default.Fingerprint,
                    title = "Authentification biométrique",
                    subtitle = "Déverrouiller avec empreinte ou Face ID",
                    checked = state.biometricEnabled,
                    onCheckedChange = { viewModel.setBiometricEnabled(it) }
                )

                HorizontalDivider(color = BgPrimary.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))

                // Auto-lock
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = AccentGold, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Verrouillage auto", color = TextPrimary, fontSize = 15.sp)
                            Text("${state.autoLockMinutes} min d'inactivité", color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 5, 15, 30).forEach { min ->
                            FilterChip(
                                selected = state.autoLockMinutes == min,
                                onClick = { viewModel.setAutoLockMinutes(min) },
                                label = { Text("${min}m", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentGold,
                                    selectedLabelColor = BgPrimary,
                                    containerColor = BgPrimary,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }
                }
            }

            // PIN de panique
            SettingsCard {
                SettingsAction(
                    icon = Icons.Default.Warning,
                    iconTint = AccentRed,
                    title = "PIN de panique",
                    subtitle = if (state.hasPanicPin) "Configuré ✓" else "Efface le wallet si saisi",
                    onClick = { showPanicPinDialog = true }
                )
            }

            // ── Notifications ─────────────────────────────────────────────────
            SectionHeader("Notifications")

            SettingsCard {
                SettingsAction(
                    icon = Icons.Default.NotificationsActive,
                    title = "Alertes de prix",
                    subtitle = "Notifier quand un prix atteint votre cible",
                    onClick = { navController.navigate(Routes.PRICE_ALERTS) }
                )
            }

            // ── Wallet ────────────────────────────────────────────────────────
            SectionHeader("Wallet")

            SettingsCard {
                SettingsAction(
                    icon = Icons.Default.VpnKey,
                    title = "Voir la phrase mnémonique",
                    subtitle = "Nécessite le PIN",
                    onClick = { navController.navigate(Routes.pinUnlockTo(Routes.SHOW_MNEMONIC)) }
                )

                HorizontalDivider(color = BgPrimary.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))

                SettingsAction(
                    icon = Icons.Default.AddCircleOutline,
                    title = "Importer un wallet",
                    subtitle = "Ajouter via phrase mnémonique",
                    onClick = { navController.navigate(Routes.IMPORT_WALLET) }
                )

                HorizontalDivider(color = BgPrimary.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))

                SettingsAction(
                    icon = Icons.Default.AccountBalanceWallet,
                    title = "Gestion du wallet",
                    subtitle = "Voir vos adresses par chaîne",
                    onClick = { navController.navigate(Routes.WALLET_MANAGEMENT) }
                )

                HorizontalDivider(color = BgPrimary.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))

                SettingsAction(
                    icon = Icons.Default.List,
                    title = "Gérer les tokens",
                    subtitle = "Tokens natifs et ERC-20 custom",
                    onClick = { navController.navigate(Routes.MANAGE_TOKENS) }
                )
            }

            // ── Avancé ────────────────────────────────────────────────────────
            SectionHeader("Avancé")

            SettingsCard {
                SettingsAction(
                    icon = Icons.Default.Contacts,
                    title = "Carnet d'adresses",
                    subtitle = "Gérer vos contacts crypto",
                    onClick = { navController.navigate(Routes.ADDRESS_BOOK) }
                )

                HorizontalDivider(color = BgPrimary.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))

                SettingsAction(
                    icon = Icons.Default.Dns,
                    title = "Paramètres réseau",
                    subtitle = "Endpoints RPC par blockchain",
                    onClick = { navController.navigate(Routes.NETWORK_SETTINGS) }
                )
            }

            // ── Danger ────────────────────────────────────────────────────────
            SectionHeader("Zone de danger")

            SettingsCard {
                SettingsAction(
                    icon = Icons.Default.DeleteForever,
                    iconTint = AccentRed,
                    title = "Réinitialiser le wallet",
                    subtitle = "Supprime toutes les données localement",
                    onClick = { showResetDialog = true }
                )
            }

            // Résultat action
            state.actionResult?.let {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AccentGold.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "✓ $it",
                        color = AccentGold,
                        modifier = Modifier.padding(14.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    // ── Dialog PIN de panique ─────────────────────────────────────────────────
    if (showPanicPinDialog) {
        PanicPinDialog(
            onDismiss = { showPanicPinDialog = false },
            onConfirm = { pin ->
                val ok = viewModel.setPanicPin(pin)
                if (ok) showPanicPinDialog = false
                ok
            }
        )
    }

    // ── Dialog reset ──────────────────────────────────────────────────────────
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor = BgSecondary,
            title = { Text("Réinitialiser le wallet ?", color = AccentRed, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Cette action supprimera toutes vos données localement. " +
                    "Sans votre phrase mnémonique, vos fonds seront inaccessibles définitivement.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.resetWallet(); showResetDialog = false; navController.navigate(Routes.WELCOME) { popUpTo(0) } }) {
                    Text("Réinitialiser", color = AccentRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Annuler", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun PanicPinDialog(onDismiss: () -> Unit, onConfirm: (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgSecondary,
        title = { Text("PIN de panique", color = AccentRed, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Ce PIN, s'il est saisi lors du déverrouillage, effacera immédiatement toutes les données du wallet.", color = TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) { pin = it; error = "" } },
                    label = { Text("6 chiffres", color = TextSecondary) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = error.isNotEmpty(),
                    supportingText = if (error.isNotEmpty()) ({ Text(error, color = AccentRed) }) else null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentRed, unfocusedBorderColor = TextSecondary,
                        focusedContainerColor = BgPrimary, unfocusedContainerColor = BgPrimary,
                        cursorColor = AccentRed
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (pin.length != 6) { error = "6 chiffres requis" }
                else { val ok = onConfirm(pin); if (!ok) error = "Erreur" }
            }) {
                Text("Confirmer", color = AccentRed, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler", color = TextSecondary) }
        }
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, color = AccentGold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BgSecondary), content = content)
}

@Composable
private fun SettingsSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = AccentGold, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 15.sp)
            Text(subtitle, color = TextSecondary, fontSize = 13.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = BgPrimary, checkedTrackColor = AccentGold))
    }
}

@Composable
private fun SettingsAction(
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color = AccentGold,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontSize = 15.sp)
                Text(subtitle, color = TextSecondary, fontSize = 13.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
        }
    }
}
