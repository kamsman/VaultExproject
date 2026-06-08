package com.vaultex.ui.screens.history

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.vaultex.ui.theme.VaultExColors

@Composable
fun HistoryScreen(navController: NavController) {
    val chains = listOf("Tous", "BTC", "ETH", "BNB", "TRX", "SOL")
    var selectedChain by remember { mutableStateOf("Tous") }

    data class TxItem(val hash: String, val type: String, val chain: String, val amount: String, val valueXof: String, val date: String, val isIncoming: Boolean)

    val txList = listOf(
        TxItem("0xabc...123", "Reçu", "ETH", "+0.12 ETH", "+330 000 FCFA", "Auj. 14:32", true),
        TxItem("0xdef...456", "Envoyé", "BTC", "-0.005 BTC", "-342 000 FCFA", "Auj. 09:15", false),
        TxItem("0x789...abc", "Swap", "BNB", "2 BNB → USDT", "-4 FCFA frais", "Hier 18:44", false),
        TxItem("0xfed...321", "Reçu", "SOL", "+5 SOL", "+312 500 FCFA", "Hier 11:20", true),
        TxItem("0x111...222", "Envoyé", "TRX", "-500 TRX", "-75 800 FCFA", "03/06 08:00", false),
        TxItem("0x333...444", "Reçu", "ETH", "+0.08 ETH", "+220 000 FCFA", "02/06 16:10", true),
    )

    val filtered = if (selectedChain == "Tous") txList else txList.filter { it.chain == selectedChain }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historique", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = VaultExColors.Background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Chain filter chips
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                chains.forEach { chain ->
                    FilterChip(
                        selected = selectedChain == chain,
                        onClick = { selectedChain = chain },
                        label = { Text(chain, fontSize = 12.sp) }
                    )
                }
            }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.History, null, modifier = Modifier.size(64.dp), tint = VaultExColors.Border)
                        Spacer(Modifier.height(12.dp))
                        Text("Aucune transaction", color = VaultExColors.TextSecondary)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered) { tx ->
                        TxCard(tx) { navController.navigate("history_detail") }
                    }
                }
            }
        }
    }
}

@Composable
private fun TxCard(tx: Any, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape)
                    .background(VaultExColors.BlueLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.SwapHoriz, null, tint = VaultExColors.BluePrimary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Transaction", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("ETH · Auj. 14:32", fontSize = 12.sp, color = VaultExColors.TextSecondary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("+0.12 ETH", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = VaultExColors.Success)
                Text("+330 000 FCFA", fontSize = 11.sp, color = VaultExColors.TextSecondary)
            }
        }
    }
}
