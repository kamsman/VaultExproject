package com.vaultex.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vaultex.R
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.VaultExColors
import com.vaultex.ui.viewmodel.HistoryViewModel
import com.vaultex.ui.viewmodel.TxDisplay

@Composable
fun HistoryScreen(navController: NavController) {
    val viewModel: HistoryViewModel = hiltViewModel()
    val filtered by viewModel.filtered.collectAsState()
    val filteredChain by viewModel.filteredChain.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val chains = listOf("Tous", "BTC", "ETH", "BNB", "TRX", "USDT", "SOL")

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.history_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back), tint = VaultExColors.BluePrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.history_refresh), tint = VaultExColors.BluePrimary)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = VaultExColors.Background)
            )
        },
        containerColor = VaultExColors.Background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // Chain filter chips
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(chains) { chain ->
                    FilterChip(
                        selected = (if (chain == "Tous") null else chain) == filteredChain,
                        onClick = { viewModel.filterByChain(if (chain == "Tous") null else chain) },
                        label = {
                            Text(
                                if (chain == "Tous") stringResource(R.string.history_filter_all) else chain,
                                fontSize = 12.sp
                            )
                        }
                    )
                }
            }

            if (isLoading && filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VaultExColors.BluePrimary)
                }
            } else if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.History, null,
                            modifier = Modifier.size(64.dp),
                            tint = VaultExColors.Border
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.no_transactions), color = VaultExColors.TextSecondary)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { viewModel.refresh() }) {
                            Text(stringResource(R.string.history_refresh), color = VaultExColors.BluePrimary)
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isLoading) {
                        item {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                color = VaultExColors.BluePrimary
                            )
                        }
                    }
                    items(filtered, key = { it.hash }) { tx ->
                        TxCard(tx) { navController.navigate(Routes.historyDetail(tx.hash)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TxCard(tx: TxDisplay, onClick: () -> Unit) {
    val iconColor = if (tx.isIncoming) VaultExColors.Success else VaultExColors.Error
    val bgColor = if (tx.isIncoming) VaultExColors.Success.copy(alpha = 0.1f) else VaultExColors.Error.copy(alpha = 0.1f)
    val icon = if (tx.isIncoming) Icons.Default.CallReceived else Icons.Default.CallMade
    val chainColor = chainColor(tx.chain)

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = VaultExColors.CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape).background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tx.type, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(chainColor)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("${tx.chain} · ${tx.date}", fontSize = 12.sp, color = VaultExColors.TextSecondary)
                }
            }
            Text(
                tx.amount,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = if (tx.isIncoming) VaultExColors.Success else VaultExColors.Error
            )
        }
    }
}

private fun chainColor(chain: String): Color = when (chain) {
    "BTC"  -> Color(0xFFF7931A)
    "ETH"  -> Color(0xFF627EEA)
    "BNB"  -> Color(0xFFF0B90B)
    "SOL"  -> Color(0xFF9945FF)
    "TRX"  -> Color(0xFFFF060A)
    "USDT" -> Color(0xFF26A17B)
    else   -> VaultExColors.TextSecondary
}
