package com.vaultex.ui.screens.market

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.vaultex.R
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.VaultExColors

@Composable
fun MarketScreen(navController: NavController) {
    data class CoinRow(val id: String, val name: String, val symbol: String, val priceXof: String, val change24h: Double, val marketCap: String, val colorHex: Color)

    val coins = listOf(
        CoinRow("bitcoin", "Bitcoin", "BTC", "34 210 000", 2.4, "674 Md", VaultExColors.BitcoinOrange),
        CoinRow("ethereum", "Ethereum", "ETH", "2 776 000", 3.1, "333 Md", VaultExColors.EthereumBlue),
        CoinRow("binancecoin", "BNB", "BNB", "398 000", -0.8, "58 Md", VaultExColors.BnbYellow),
        CoinRow("solana", "Solana", "SOL", "62 500", 7.2, "28 Md", VaultExColors.SolanaGreen),
        CoinRow("tron", "Tron", "TRX", "152", 5.1, "13 Md", VaultExColors.TronRed),
        CoinRow("tether", "Tether", "USDT", "655", 0.01, "111 Md", Color(0xFF26A17B)),
        CoinRow("usd-coin", "USD Coin", "USDC", "655", 0.0, "32 Md", Color(0xFF2775CA)),
    )

    var searchQuery by remember { mutableStateOf("") }
    val filtered = coins.filter { it.name.contains(searchQuery, true) || it.symbol.contains(searchQuery, true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.market_title), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultExColors.Background)
            )
        },
        containerColor = VaultExColors.Background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = searchQuery, onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.market_search_placeholder)) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = RoundedCornerShape(12.dp), singleLine = true
            )
            // Header row
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(stringResource(R.string.market_header_name), fontSize = 11.sp, color = VaultExColors.TextSecondary, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.market_header_price), fontSize = 11.sp, color = VaultExColors.TextSecondary)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.market_header_24h), fontSize = 11.sp, color = VaultExColors.TextSecondary, modifier = Modifier.width(52.dp))
            }
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(filtered.withIndex().toList()) { (idx, coin) ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { navController.navigate(Routes.COIN_DETAIL) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = VaultExColors.CardBackground)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${idx + 1}", fontSize = 12.sp, color = VaultExColors.TextSecondary, modifier = Modifier.width(22.dp))
                            Box(Modifier.size(36.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                                Surface(color = coin.colorHex.copy(alpha = 0.15f), shape = CircleShape) {
                                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                        Text(coin.symbol.take(1), fontWeight = FontWeight.Bold, color = coin.colorHex, fontSize = 14.sp)
                                    }
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(coin.symbol, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(coin.name, fontSize = 11.sp, color = VaultExColors.TextSecondary)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${coin.priceXof} F", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Text(stringResource(R.string.market_cap_value, coin.marketCap), fontSize = 10.sp, color = VaultExColors.TextSecondary)
                            }
                            Spacer(Modifier.width(12.dp))
                            val isPos = coin.change24h >= 0
                            Surface(color = if (isPos) VaultExColors.Success.copy(alpha = 0.12f) else VaultExColors.Error.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    "${if (isPos) "+" else ""}${String.format("%.1f", coin.change24h)}%",
                                    fontSize = 11.sp, fontWeight = FontWeight.Medium,
                                    color = if (isPos) VaultExColors.Success else VaultExColors.Error,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp).width(46.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
