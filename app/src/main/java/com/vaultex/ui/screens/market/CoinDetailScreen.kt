package com.vaultex.ui.screens.market

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.vaultex.ui.theme.VaultExColors

@Composable
fun CoinDetailScreen(navController: NavController, coinId: String = "bitcoin") {
    val isPositive = true
    val periods = listOf("1J", "1S", "1M", "3M", "1A")
    var selectedPeriod by remember { mutableStateOf("1S") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bitcoin", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultExColors.Background)
            )
        },
        containerColor = VaultExColors.Background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Price header
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = VaultExColors.CardBackground)) {
                Column(Modifier.padding(20.dp)) {
                    Text("Bitcoin", color = VaultExColors.TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("34 210 000 FCFA", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isPositive) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = if (isPositive) VaultExColors.Success else VaultExColors.Error
                        )
                        Text(
                            "+2.4% (24h)",
                            color = if (isPositive) VaultExColors.Success else VaultExColors.Error,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Period selector
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                periods.forEach { period ->
                    FilterChip(
                        selected = selectedPeriod == period,
                        onClick = { selectedPeriod = period },
                        label = { Text(period, fontSize = 12.sp) }
                    )
                }
            }

            // Chart placeholder
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = VaultExColors.CardBackground)) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Graphique $selectedPeriod", color = VaultExColors.TextSecondary)
                }
            }

            // Stats
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = VaultExColors.CardBackground)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Statistiques", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    StatRow("Cap. marché", "674 Md FCFA")
                    StatRow("Volume 24h", "18.3 Md FCFA")
                    StatRow("Plus haut 24h", "35 100 000 FCFA")
                    StatRow("Plus bas 24h", "33 200 000 FCFA")
                    StatRow("Rang", "#1")
                }
            }

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { navController.navigate("send") },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VaultExColors.BluePrimary)
                ) { Text("Acheter/Envoyer") }
                OutlinedButton(
                    onClick = { navController.navigate("receive") },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Recevoir") }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = VaultExColors.TextSecondary, fontSize = 14.sp)
        Text(value, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}
