package com.vaultex.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController

import com.vaultex.data.local.entity.TransactionEntity
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.*
import com.vaultex.ui.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(navController: NavHostController) {

    val viewModel: HistoryViewModel = hiltViewModel()
    val transactions by viewModel.transactions.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historique", color = TextPrimary) },
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

        if (transactions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📭", fontSize = 52.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("Aucune transaction", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("Vos transactions apparaîtront ici", color = TextSecondary, fontSize = 14.sp)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(transactions, key = { it.hash }) { tx ->
                TxRow(tx, onClick = { navController.navigate(Routes.txDetail(tx.hash)) })
            }
        }
    }
}

@Composable
private fun TxRow(tx: TransactionEntity, onClick: () -> Unit = {}) {
    val date = remember(tx.timestamp) {
        SimpleDateFormat("dd MMM yyyy HH:mm", Locale.FRANCE).format(Date(tx.timestamp))
    }
    val isSend = tx.type.equals("send", ignoreCase = true)
    val isSwap = tx.type.equals("swap", ignoreCase = true)

    val icon = when {
        isSwap -> Icons.Default.SwapHoriz
        isSend -> Icons.Default.CallMade
        else   -> Icons.Default.CallReceived
    }
    val iconTint = when {
        isSwap -> AccentGold
        isSend -> AccentRed
        else   -> AccentGreen
    }
    val amountPrefix = if (isSend) "-" else "+"
    val statusColor = when (tx.status.lowercase()) {
        "confirmed" -> AccentGreen
        "pending"   -> AccentGold
        else        -> AccentRed
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    tx.type.replaceFirstChar { it.uppercaseChar() },
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(date, color = TextSecondary, fontSize = 12.sp)
                Text(
                    tx.blockchain,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "$amountPrefix${tx.amount} ${tx.tokenSymbol}",
                    color = if (isSend) AccentRed else AccentGreen,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    tx.status.replaceFirstChar { it.uppercaseChar() },
                    color = statusColor,
                    fontSize = 11.sp
                )
            }
        }
    }
}
