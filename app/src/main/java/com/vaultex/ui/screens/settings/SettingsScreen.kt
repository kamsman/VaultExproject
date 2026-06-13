package com.vaultex.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vaultex.R
import com.vaultex.ui.components.VaultExBottomBar
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.BgTertiary
import com.vaultex.ui.theme.BorderColor
import com.vaultex.ui.theme.Surface as SurfaceColor
import com.vaultex.ui.theme.TextMuted
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary
import com.vaultex.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavHostController) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val currentLang = remember { com.vaultex.core.session.LocaleManager.getLanguage(context) }

    if (showThemeDialog) {
        ThemePickerDialog(
            current = themeMode,
            onPick = { viewModel.setThemeMode(it); showThemeDialog = false },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showLanguageDialog) {
        LanguagePickerDialog(
            current = currentLang,
            onPick = { lang ->
                showLanguageDialog = false
                if (lang != currentLang) {
                    com.vaultex.core.session.LocaleManager.setLanguage(context, lang)
                    (context as? android.app.Activity)?.recreate()
                }
            },
            onDismiss = { showLanguageDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = TextPrimary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgPrimary)
            )
        },
        bottomBar = { VaultExBottomBar(navController) },
        containerColor = BgPrimary
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ─── Carte profil ───
            item {
                val walletName = stringResource(R.string.settings_my_wallet)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(52.dp).clip(CircleShape).background(AccentBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                walletName.take(2).uppercase(),
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(walletName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                            Text(
                                stringResource(R.string.settings_wallet_subtitle),
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // ─── Sécurité ───
            item { SectionTitle(stringResource(R.string.security)) }
            item {
                SectionCard {
                    SettingsRow(Icons.Default.Pin, stringResource(R.string.security_change_pin)) {
                        navController.navigate(Routes.PIN_SETUP)
                    }
                    RowDivider()
                    SettingsToggleRow(
                        icon = Icons.Default.Fingerprint,
                        title = stringResource(R.string.settings_biometric),
                        checked = state.isBiometricEnabled,
                        onCheckedChange = viewModel::setBiometric
                    )
                    RowDivider()
                    SettingsRow(Icons.Default.Warning, stringResource(R.string.panic_pin_title)) {
                        navController.navigate(Routes.PANIC_PIN)
                    }
                    RowDivider()
                    SettingsRow(Icons.Default.Security, stringResource(R.string.security)) {
                        navController.navigate(Routes.SECURITY)
                    }
                    RowDivider()
                    SettingsRow(Icons.Default.Backup, stringResource(R.string.backup)) {
                        navController.navigate(Routes.BACKUP)
                    }
                }
            }

            // ─── Wallet ───
            item { SectionTitle(stringResource(R.string.settings_section_wallet)) }
            item {
                SectionCard {
                    SettingsRow(Icons.Default.AccountBalanceWallet, stringResource(R.string.wallet_mgr_title)) {
                        navController.navigate(Routes.WALLET_MANAGER)
                    }
                    RowDivider()
                    SettingsRow(Icons.Default.Token, stringResource(R.string.settings_tokens_item)) {
                        navController.navigate(Routes.TOKEN_MANAGER)
                    }
                    RowDivider()
                    SettingsRow(Icons.Default.Contacts, stringResource(R.string.address_book)) {
                        navController.navigate(Routes.ADDRESS_BOOK)
                    }
                }
            }

            // ─── Réseau ───
            item { SectionTitle(stringResource(R.string.settings_section_network)) }
            item {
                SectionCard {
                    SettingsRow(Icons.Default.NetworkWifi, stringResource(R.string.settings_rpc_item)) {
                        navController.navigate(Routes.NETWORK_SETTINGS)
                    }
                    RowDivider()
                    SettingsValueRow(Icons.Default.AttachMoney, stringResource(R.string.currency), state.selectedCurrency)
                    RowDivider()
                    SettingsRow(Icons.Default.Notifications, stringResource(R.string.settings_price_alerts)) {
                        navController.navigate(Routes.NOTIFICATIONS)
                    }
                }
            }

            // ─── App ───
            item { SectionTitle(stringResource(R.string.settings_section_app)) }
            item {
                SectionCard {
                    SettingsClickableValueRow(
                        Icons.Default.DarkMode,
                        stringResource(R.string.settings_theme),
                        themeModeLabel(themeMode)
                    ) { showThemeDialog = true }
                    RowDivider()
                    SettingsClickableValueRow(
                        Icons.Default.Language,
                        stringResource(R.string.language),
                        languageLabel(currentLang)
                    ) { showLanguageDialog = true }
                    RowDivider()
                    SettingsRow(Icons.Default.Help, stringResource(R.string.settings_help)) {
                        navController.navigate(Routes.HELP)
                    }
                    RowDivider()
                    SettingsValueRow(
                        Icons.Default.Info,
                        stringResource(R.string.settings_about),
                        stringResource(R.string.settings_version_short)
                    )
                }
            }

            // ─── Prototypes (en développement) ───
            item { SectionTitle(stringResource(R.string.settings_section_prototypes)) }
            item {
                SectionCard {
                    SettingsRow(Icons.Default.Home, stringResource(R.string.proto_home)) {
                        navController.navigate(Routes.HOME)
                    }
                    RowDivider()
                    SettingsRow(Icons.Default.PieChart, stringResource(R.string.proto_portfolio)) {
                        navController.navigate(Routes.PORTFOLIO)
                    }
                    RowDivider()
                    SettingsRow(Icons.Default.Send, stringResource(R.string.proto_send_v2)) {
                        navController.navigate(Routes.SEND_FORM)
                    }
                    RowDivider()
                    SettingsRow(Icons.Default.SwapHoriz, stringResource(R.string.proto_swap_v2)) {
                        navController.navigate(Routes.SWAP_CONFIRM)
                    }
                    RowDivider()
                    SettingsRow(Icons.Default.QrCode, stringResource(R.string.proto_receive_v2)) {
                        navController.navigate(Routes.RECEIVE_NETWORK)
                    }
                    RowDivider()
                    SettingsRow(Icons.Default.AccountBalanceWallet, stringResource(R.string.proto_wallet_mgmt)) {
                        navController.navigate(Routes.WALLET_MANAGEMENT)
                    }
                    RowDivider()
                    SettingsRow(Icons.Default.Shield, stringResource(R.string.proto_security_setup)) {
                        navController.navigate(Routes.SECURITY_SETUP)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        color = TextSecondary,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp, start = 4.dp)
    )
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(color = BorderColor, thickness = 0.5.dp, modifier = Modifier.padding(start = 60.dp))
}

@Composable
private fun RowIcon(icon: ImageVector) {
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(BgTertiary),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowIcon(icon)
        Spacer(Modifier.width(12.dp))
        Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun themeModeLabel(mode: com.vaultex.ui.theme.ThemeMode): String = when (mode) {
    com.vaultex.ui.theme.ThemeMode.LIGHT -> stringResource(R.string.theme_light)
    com.vaultex.ui.theme.ThemeMode.DARK -> stringResource(R.string.theme_dark)
    com.vaultex.ui.theme.ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
}

@Composable
private fun SettingsClickableValueRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowIcon(icon)
        Spacer(Modifier.width(12.dp))
        Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, color = TextSecondary)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun languageLabel(code: String): String = when (code) {
    "en" -> stringResource(R.string.lang_english)
    "ar" -> stringResource(R.string.lang_arabic)
    else -> stringResource(R.string.lang_french)
}

@Composable
private fun LanguagePickerDialog(
    current: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        "fr" to stringResource(R.string.lang_french),
        "en" to stringResource(R.string.lang_english),
        "ar" to stringResource(R.string.lang_arabic)
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language)) },
        text = {
            Column {
                options.forEach { (code, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(code) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = current == code, onClick = { onPick(code) })
                        Spacer(Modifier.width(8.dp))
                        Text(label, fontSize = 15.sp, color = TextPrimary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}

@Composable
private fun ThemePickerDialog(
    current: com.vaultex.ui.theme.ThemeMode,
    onPick: (com.vaultex.ui.theme.ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        com.vaultex.ui.theme.ThemeMode.LIGHT to stringResource(R.string.theme_light),
        com.vaultex.ui.theme.ThemeMode.DARK to stringResource(R.string.theme_dark),
        com.vaultex.ui.theme.ThemeMode.SYSTEM to stringResource(R.string.theme_system)
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_theme)) },
        text = {
            Column {
                options.forEach { (mode, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(mode) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = current == mode, onClick = { onPick(mode) })
                        Spacer(Modifier.width(8.dp))
                        Text(label, fontSize = 15.sp, color = TextPrimary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}

@Composable
private fun SettingsValueRow(icon: ImageVector, title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowIcon(icon)
        Spacer(Modifier.width(12.dp))
        Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, color = TextSecondary)
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowIcon(icon)
        Spacer(Modifier.width(12.dp))
        Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccentBlue,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = BorderColor
            )
        )
    }
}
