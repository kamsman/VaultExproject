package com.vaultex.ui.screens.home

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.VaultExColors

@Composable
fun HomeScreen(navController: NavController) {
    val totalBalance = "2 847 350 FCFA"
    val changePercent = "+2.4%"
    val isPositive = true

    val tokens = listOf(
        TokenRow("Bitcoin", "BTC", "0.0124 BTC", "850 200 FCFA", "+1.8%", true, VaultExColors.BitcoinOrange),
        TokenRow("Ethereum", "ETH", "0.45 ETH", "1 245 000 FCFA", "+3.2%", true, VaultExColors.EthereumBlue),
        TokenRow("BNB", "BNB", "2.1 BNB", "450 000 FCFA", "-0.5%", false, VaultExColors.BnbYellow),
        TokenRow("Tron", "TRX", "1250 TRX", "189 750 FCFA", "+5.1%", true, VaultExColors.TronRed),
        TokenRow("Solana", "SOL", "1.8 SOL", "112 400 FCFA", "+7.3%", true, VaultExColors.SolanaGreen),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VaultEx", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { navController.navigate(Routes.NOTIFICATIONS) }) {
                        Icon(Icons.Default.Notifications, contentDescription = "Alertes")
                    }
                    IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Paramètres")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultExColors.Background)
            )
        },
        containerColor = VaultExColors.Background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                // Balance card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.linearGradient(listOf(VaultExColors.BlueGradientStart, VaultExColors.BlueGradientEnd)))
                        .padding(24.dp)
                ) {
                    Column {
                        Text("Portefeuille total", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(totalBalance, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            changePercent + " aujourd'hui",
                            color = if (isPositive) Color(0xFF86EFAC) else Color(0xFFFCA5A5),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            item {
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ActionButton("Envoyer", Icons.Default.Send) { navController.navigate(Routes.SEND) }
                    ActionButton("Recevoir", Icons.Default.Download) { navController.navigate(Routes.RECEIVE) }
                    ActionButton("Échanger", Icons.Default.SwapHoriz) { navController.navigate(Routes.SWAP) }
                    ActionButton("Mobile\nMoney", Icons.Default.PhoneAndroid) { navController.navigate(Routes.MOBILE_MONEY) }
                }
            }

            item {
                Text("Mes actifs", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = VaultExColors.TextPrimary)
            }

            items(tokens) { token ->
                TokenCard(token) { navController.navigate(Routes.tokenDetail(token.symbol)) }
            }
        }
    }
}

@Composable
private fun ActionButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(VaultExColors.BlueLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = VaultExColors.BluePrimary, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 11.sp, color = VaultExColors.TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun TokenCard(token: TokenRow, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = VaultExColors.CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(token.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(token.symbol.take(1), color = token.color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(token.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(token.amount, fontSize = 12.sp, color = VaultExColors.TextSecondary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(token.valueXof, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(
                    token.change,
                    fontSize = 12.sp,
                    color = if (token.isPositive) VaultExColors.Success else VaultExColors.Error
                )
            }
        }
    }
}

private data class TokenRow(
    val name: String, val symbol: String, val amount: String,
    val valueXof: String, val change: String, val isPositive: Boolean, val color: Color
)
