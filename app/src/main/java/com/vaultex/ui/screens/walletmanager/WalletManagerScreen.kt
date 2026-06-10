package com.vaultex.ui.screens.walletmanager

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.vaultex.ui.theme.VaultExColors

@Composable
fun WalletManagerScreen(navController: NavController) {
    data class WalletItem(val id: String, val name: String, val balance: String, val isActive: Boolean)

    val wallets = remember {
        mutableStateListOf(
            WalletItem("1", "Wallet Principal", "2 847 350 FCFA", true),
            WalletItem("2", "Wallet Épargne", "450 000 FCFA", false),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wallets", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) } },
                actions = { IconButton(onClick = { }) { Icon(Icons.Default.Add, null, tint = VaultExColors.BluePrimary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = VaultExColors.Background
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(wallets) { wallet ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = if (wallet.isActive) BorderStroke(2.dp, VaultExColors.BluePrimary) else null
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape)
                                .background(if (wallet.isActive) VaultExColors.BlueLight else VaultExColors.Background),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AccountBalanceWallet, null,
                                tint = if (wallet.isActive) VaultExColors.BluePrimary else VaultExColors.TextSecondary)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(wallet.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(wallet.balance, fontSize = 13.sp, color = VaultExColors.TextSecondary)
                            if (wallet.isActive) Text("Actif", fontSize = 11.sp, color = VaultExColors.BluePrimary, fontWeight = FontWeight.Medium)
                        }
                        IconButton(onClick = { }) { Icon(Icons.Default.MoreVert, null, tint = VaultExColors.TextSecondary) }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = { },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Créer un nouveau wallet")
                }
            }
            item {
                OutlinedButton(
                    onClick = { },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.FileDownload, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Importer un wallet existant")
                }
            }
        }
    }
}
