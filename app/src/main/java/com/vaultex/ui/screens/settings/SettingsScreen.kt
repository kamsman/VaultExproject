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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
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
                title = { Text("Réglages", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
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
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
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
                            Text("Mon Wallet", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("VaultEx · Multi-Chain", fontSize = 13.sp, color = VaultExColors.TextSecondary)
                        }
                    }
                }
            }

            item { SectionTitle("Wallet") }
            item { SettingsItem(Icons.Default.AccountBalanceWallet, "Mes wallets", "Gérer vos wallets") { navController.navigate(Routes.WALLET_MANAGER) } }
            item { SettingsItem(Icons.Default.Token, "Gérer les tokens", "Ajouter, masquer des tokens") { navController.navigate(Routes.TOKEN_MANAGER) } }
            item { SettingsItem(Icons.Default.Contacts, "Carnet d'adresses", "Adresses enregistrées") { navController.navigate(Routes.ADDRESS_BOOK) } }

            item { SectionTitle("Sécurité") }
            item { SettingsItem(Icons.Default.Security, "Sécurité", "PIN, biométrie, verrou auto") { navController.navigate(Routes.SECURITY) } }
            item { SettingsItem(Icons.Default.Backup, "Sauvegarde", "Phrase secrète, export") { navController.navigate(Routes.BACKUP) } }

            item { SectionTitle("Réseau & Affichage") }
            item { SettingsItem(Icons.Default.NetworkWifi, "Réseaux RPC", "Nœuds ETH, BSC, SOL...") { navController.navigate(Routes.NETWORK_SETTINGS) } }
            item { SettingsItem(Icons.Default.AttachMoney, "Devise", state.selectedCurrency) { } }
            item { SettingsItem(Icons.Default.Language, "Langue", "Français, Bambara...") { } }

            item { SectionTitle("Notifications") }
            item { SettingsItem(Icons.Default.Notifications, "Alertes prix", "Configurer les alertes") { navController.navigate(Routes.NOTIFICATIONS) } }

            item { SectionTitle("À propos") }
            item { SettingsItem(Icons.Default.Info, "Version", "VaultEx 1.0.0") { } }
            item { SettingsItem(Icons.Default.Help, "Aide & Support", "FAQ, contact") { } }
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
        colors = CardDefaults.cardColors(containerColor = Color.White)
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
