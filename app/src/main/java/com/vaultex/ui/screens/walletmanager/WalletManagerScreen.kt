package com.vaultex.ui.screens.walletmanager

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vaultex.R
import com.vaultex.data.local.entity.WalletEntity
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.AccentRed
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.BgTertiary
import com.vaultex.ui.theme.Surface as SurfaceColor
import com.vaultex.ui.theme.TextMuted
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary
import com.vaultex.ui.viewmodel.WalletManagerViewModel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletManagerScreen(navController: NavController) {
    val viewModel: WalletManagerViewModel = hiltViewModel()
    val wallets by viewModel.wallets.collectAsState()
    var fabMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.wallet_mgr_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back), tint = AccentBlue)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgPrimary)
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(
                    onClick = { fabMenuExpanded = true },
                    shape = CircleShape,
                    containerColor = AccentBlue,
                    contentColor = Color.White,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
                }
                DropdownMenu(
                    expanded = fabMenuExpanded,
                    onDismissRequest = { fabMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.create_wallet)) },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = AccentBlue) },
                        onClick = {
                            fabMenuExpanded = false
                            navController.navigate(Routes.ONBOARDING)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.import_wallet)) },
                        leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null, tint = AccentBlue) },
                        onClick = {
                            fabMenuExpanded = false
                            navController.navigate(Routes.IMPORT_WALLET)
                        }
                    )
                }
            }
        },
        containerColor = BgPrimary
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.wallet_mgr_hd_accounts),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextMuted
                )
            }

            if (wallets.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.wallet_mgr_empty), color = TextSecondary)
                    }
                }
            }

            itemsIndexed(wallets, key = { _, wallet -> wallet.id }) { index, wallet ->
                WalletCard(
                    number = index + 1,
                    wallet = wallet,
                    onActivate = { viewModel.activateWallet(wallet.id) },
                    onDelete = { viewModel.deleteWallet(wallet.id) }
                )
            }
        }
    }
}

@Composable
private fun WalletCard(
    number: Int,
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
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !wallet.isActive, onClick = onActivate)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pastille numérotée
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (wallet.isActive) AccentBlue else BgTertiary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    number.toString(),
                    color = if (wallet.isActive) Color.White else TextMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(wallet.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                Text(
                    stringResource(R.string.wallet_mgr_created_on, createdAt),
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            if (wallet.isActive) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(BgTertiary)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        stringResource(R.string.active),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentBlue
                    )
                }
            } else {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = stringResource(R.string.wallet_mgr_options),
                            tint = TextMuted
                        )
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.wallet_mgr_activate)) },
                            onClick = {
                                menuExpanded = false
                                onActivate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete), color = AccentRed) },
                            onClick = {
                                menuExpanded = false
                                showDeleteConfirm = true
                            }
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
                }) { Text(stringResource(R.string.delete), color = AccentRed) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
