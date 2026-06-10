package com.vaultex.ui.screens.settings

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vaultex.R
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.VaultExColors
import com.vaultex.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(navController: NavController) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultExColors.Background)
            )
        },
        containerColor = VaultExColors.Background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                // Profile card
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = VaultExColors.CardBackground)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(52.dp).clip(CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(color = VaultExColors.BluePrimary, shape = CircleShape) {
                                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                                    Text("M", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(stringResource(R.string.settings_my_wallet), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(stringResource(R.string.settings_wallet_subtitle), fontSize = 13.sp, color = VaultExColors.TextSecondary)
                        }
                    }
                }
            }

            item { SectionTitle(stringResource(R.string.settings_section_wallet)) }
            item { SettingsItem(Icons.Default.AccountBalanceWallet, stringResource(R.string.wallet_manager), stringResource(R.string.settings_wallets_subtitle)) { navController.navigate(Routes.WALLET_MANAGER) } }
            item { SettingsItem(Icons.Default.Token, stringResource(R.string.token_manager), stringResource(R.string.settings_tokens_subtitle)) { navController.navigate(Routes.TOKEN_MANAGER) } }
            item { SettingsItem(Icons.Default.Contacts, stringResource(R.string.address_book), stringResource(R.string.settings_address_book_subtitle)) { navController.navigate(Routes.ADDRESS_BOOK) } }

            item { SectionTitle(stringResource(R.string.security)) }
            item { SettingsItem(Icons.Default.Security, stringResource(R.string.security), stringResource(R.string.settings_security_subtitle)) { navController.navigate(Routes.SECURITY) } }
            item { SettingsItem(Icons.Default.Backup, stringResource(R.string.backup), stringResource(R.string.settings_backup_subtitle)) { navController.navigate(Routes.BACKUP) } }

            item { SectionTitle(stringResource(R.string.settings_section_network_display)) }
            item { SettingsItem(Icons.Default.NetworkWifi, stringResource(R.string.settings_rpc_networks), stringResource(R.string.settings_rpc_subtitle)) { navController.navigate(Routes.NETWORK_SETTINGS) } }
            item { SettingsItem(Icons.Default.AttachMoney, stringResource(R.string.currency), state.selectedCurrency) { } }
            item { SettingsItem(Icons.Default.Language, stringResource(R.string.language), stringResource(R.string.settings_language_subtitle)) { } }

            item { SectionTitle(stringResource(R.string.notifications)) }
            item { SettingsItem(Icons.Default.Notifications, stringResource(R.string.settings_price_alerts), stringResource(R.string.settings_price_alerts_subtitle)) { navController.navigate(Routes.NOTIFICATIONS) } }

            item { SectionTitle(stringResource(R.string.settings_section_about)) }
            item { SettingsItem(Icons.Default.Info, stringResource(R.string.settings_version), stringResource(R.string.settings_version_value)) { } }
            item { SettingsItem(Icons.Default.Help, stringResource(R.string.settings_help), stringResource(R.string.settings_help_subtitle)) { } }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = VaultExColors.TextSecondary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp, start = 4.dp))
}

@Composable
private fun SettingsItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = VaultExColors.CardBackground)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Surface(color = VaultExColors.BlueLight, shape = RoundedCornerShape(10.dp)) {
                    Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = VaultExColors.BluePrimary, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(subtitle, fontSize = 12.sp, color = VaultExColors.TextSecondary)
            }
            Icon(Icons.Default.ChevronRight, null, tint = VaultExColors.Border)
        }
    }
}
