package com.vaultex.ui.screens.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vaultex.data.local.entity.PriceAlertEntity
import com.vaultex.data.local.entity.TransactionEntity
import com.vaultex.ui.theme.*
import com.vaultex.ui.viewmodel.HistoryViewModel
import com.vaultex.ui.viewmodel.PriceAlertViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(navController: NavHostController) {

    val historyVm: HistoryViewModel = hiltViewModel()
    val alertVm: PriceAlertViewModel = hiltViewModel()

    val transactions by historyVm.transactions.collectAsState(initial = emptyList())
    val alerts by alertVm.alerts.collectAsState()

    val recentTxs = transactions.take(20)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications", color = TextPrimary) },
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

        if (recentTxs.isEmpty() && alerts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.NotificationsNone, contentDescription = null,
                        tint = TextMuted, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Aucune notification", color = TextSecondary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text("Les alertes prix et confirmations apparaîtront ici", color = TextMuted, fontSize = 13.sp)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(BgPrimary, BgSecondary)))
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Alertes prix actives ──────────────────────────────────────────
            if (alerts.isNotEmpty()) {
                item {
                    Text("Alertes prix actives", color = AccentGold, fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                }
                items(alerts, key = { "alert_${it.id}" }) { alert ->
                    AlertNotifRow(alert = alert)
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            // ── Transactions récentes ─────────────────────────────────────────
            if (recentTxs.isNotEmpty()) {
                item {
                    Text("Transactions récentes", color = AccentGold, fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                }
                items(recentTxs, key = { "tx_${it.hash}" }) { tx ->
                    TxNotifRow(tx = tx)
                }
            }
        }
    }
}

@Composable
private fun AlertNotifRow(alert: PriceAlertEntity) {
    val conditionLabel = if (alert.condition == "ABOVE") "dépasse" else "passe sous"
    val conditionColor = if (alert.condition == "ABOVE") AccentGreen else AccentRed
    val icon = if (alert.condition == "ABOVE") Icons.Default.TrendingUp else Icons.Default.TrendingDown

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape)
                    .background(conditionColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = conditionColor, modifier = Modifier.size(22.dp))
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(alert.tokenSymbol, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Row {
                    Text("Alerte si ", color = TextSecondary, fontSize = 13.sp)
                    Text(conditionLabel, color = conditionColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(" ${"$"}${alert.targetPrice}", color = TextPrimary, fontSize = 13.sp)
                }
            }

            Text("Actif", color = AccentGold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TxNotifRow(tx: TransactionEntity) {
    val date = remember(tx.timestamp) {
        SimpleDateFormat("dd MMM HH:mm", Locale.FRANCE).format(Date(tx.timestamp))
    }
    val isSend = tx.type.equals("send", ignoreCase = true)
    val isSwap = tx.type.equals("swap", ignoreCase = true)

    val icon: ImageVector = when {
        isSwap -> Icons.Default.SwapHoriz
        isSend -> Icons.Default.CallMade
        else   -> Icons.Default.CallReceived
    }
    val iconTint = when {
        isSwap -> AccentGold
        isSend -> AccentRed
        else   -> AccentGreen
    }
    val statusColor = when (tx.status.lowercase()) {
        "confirmed" -> AccentGreen
        "pending"   -> AccentGold
        else        -> AccentRed
    }
    val statusLabel = when (tx.status.lowercase()) {
        "confirmed" -> "Confirmé ✓"
        "pending"   -> "En attente…"
        else        -> "Échoué"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${tx.type.replaceFirstChar { it.uppercaseChar() }} · ${tx.blockchain}",
                    color = TextPrimary, fontWeight = FontWeight.SemiBold
                )
                Text("${tx.amount} ${tx.tokenSymbol}", color = TextSecondary, fontSize = 13.sp)
                Text(date, color = TextMuted, fontSize = 11.sp)
            }

            Text(statusLabel, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
