package com.vaultex.ui.screens.walletmanager

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vaultex.R
import com.vaultex.data.local.entity.WalletEntity
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.VaultExColors
import com.vaultex.ui.viewmodel.WalletManagerViewModel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletManagerScreen(navController: NavController) {
    val viewModel: WalletManagerViewModel = hiltViewModel()
    val wallets by viewModel.wallets.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wallet_manager), fontWeight = FontWeight.Bold) },
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
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (wallets.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.wallet_mgr_empty), color = VaultExColors.TextSecondary)
                    }
                }
            }

            items(wallets, key = { it.id }) { wallet ->
                WalletCard(
                    wallet = wallet,
                    onActivate = { viewModel.activateWallet(wallet.id) },
                    onDelete = { viewModel.deleteWallet(wallet.id) }
                )
            }

            item {
                OutlinedButton(
                    onClick = { navController.navigate(Routes.ONBOARDING) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.create_wallet))
                }
            }
            item {
                OutlinedButton(
                    onClick = { navController.navigate(Routes.IMPORT_WALLET) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.import_wallet))
                }
            }
        }
    }
}

@Composable
private fun WalletCard(
    wallet: WalletEntity,
    onActivate: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val createdAt = remember(wallet.createdAt) {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(wallet.createdAt))
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = VaultExColors.CardBackground),
        border = if (wallet.isActive) BorderStroke(2.dp, VaultExColors.BluePrimary) else null,
        modifier = Modifier.clickable(enabled = !wallet.isActive, onClick = onActivate)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(if (wallet.isActive) VaultExColors.BlueLight else VaultExColors.Background),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AccountBalanceWallet, contentDescription = null,
                    tint = if (wallet.isActive) VaultExColors.BluePrimary else VaultExColors.TextSecondary
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(wallet.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(stringResource(R.string.wallet_mgr_created_on, createdAt), fontSize = 13.sp, color = VaultExColors.TextSecondary)
                if (wallet.isActive) {
                    Text(stringResource(R.string.active), fontSize = 11.sp, color = VaultExColors.BluePrimary, fontWeight = FontWeight.Medium)
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.wallet_mgr_options), tint = VaultExColors.TextSecondary)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    if (!wallet.isActive) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.wallet_mgr_activate)) },
                            onClick = {
                                menuExpanded = false
                                onActivate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete), color = VaultExColors.Error) },
                            onClick = {
                                menuExpanded = false
                                showDeleteConfirm = true
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.wallet_mgr_active_wallet), color = VaultExColors.TextSecondary) },
                            onClick = { menuExpanded = false }
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.wallet_mgr_delete_title)) },
            text = { Text(stringResource(R.string.wallet_mgr_delete_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) { Text(stringResource(R.string.delete), color = VaultExColors.Error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
