package com.vaultex.ui.screens.market

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.vaultex.R
import com.vaultex.ui.components.VaultExBottomBar
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.AccentGreen
import com.vaultex.ui.theme.AccentRed
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.BorderColor
import com.vaultex.ui.theme.Surface
import com.vaultex.ui.theme.TextMuted
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary
import com.vaultex.ui.theme.VaultExColors
import kotlin.random.Random

private data class CoinRow(
    val id: String,
    val name: String,
    val symbol: String,
    val priceXof: String,
    val change24h: Double,
    val marketCap: String,
    val color: Color
)

private enum class MarketFilter { ALL, GAINERS, LOSERS }

@Composable
fun MarketScreen(navController: NavHostController) {

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
    var filter by remember { mutableStateOf(MarketFilter.ALL) }

    val filtered = coins
        .filter { it.name.contains(searchQuery, true) || it.symbol.contains(searchQuery, true) }
        .filter {
            when (filter) {
                MarketFilter.ALL -> true
                MarketFilter.GAINERS -> it.change24h > 0
                MarketFilter.LOSERS -> it.change24h < 0
            }
        }

    Scaffold(
        bottomBar = { VaultExBottomBar(navController) },
        containerColor = BgPrimary
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // ─── Titre large ───
            Text(
                stringResource(R.string.market_title),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp)
            )

            // ─── Recherche ───
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.market_search_hint), color = TextMuted, fontSize = 14.sp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                leadingIcon = { Icon(Icons.Default.Search, null, tint = TextMuted) },
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Surface,
                    unfocusedContainerColor = Surface,
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = BorderColor
                )
            )

            // ─── Filtres Tout / Gainers / Losers ───
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterPill(stringResource(R.string.market_filter_all), filter == MarketFilter.ALL) { filter = MarketFilter.ALL }
                FilterPill(stringResource(R.string.market_filter_gainers), filter == MarketFilter.GAINERS) { filter = MarketFilter.GAINERS }
                FilterPill(stringResource(R.string.market_filter_losers), filter == MarketFilter.LOSERS) { filter = MarketFilter.LOSERS }
            }

            // ─── Liste des cryptos ───
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { coin ->
                    CoinCard(coin) { navController.navigate(Routes.coinDetail(coin.id)) }
                }
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) AccentBlue else Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else TextSecondary
        )
    }
}

@Composable
private fun CoinCard(coin: CoinRow, onClick: () -> Unit) {
    val isPositive = coin.change24h >= 0
    val changeColor = if (isPositive) AccentGreen else AccentRed

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(46.dp).clip(CircleShape).background(coin.color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    coin.symbol.take(2),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(coin.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                Text(coin.symbol, fontSize = 12.sp, color = TextSecondary)
            }
            MiniSparkline(
                seed = coin.id.hashCode(),
                color = changeColor,
                modifier = Modifier.size(width = 64.dp, height = 28.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("${coin.priceXof} F", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                Text(
                    "${if (isPositive) "+" else ""}${String.format("%.1f", coin.change24h)}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = changeColor
                )
            }
        }
    }
}

@Composable
private fun MiniSparkline(seed: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val random = Random(seed)
        val points = 18
        val step = size.width / (points - 1)
        var y = size.height * 0.5f
        val path = Path()
        path.moveTo(0f, y)
        for (i in 1 until points) {
            y = (y + (random.nextFloat() - 0.5f) * size.height * 0.8f)
                .coerceIn(size.height * 0.1f, size.height * 0.9f)
            path.lineTo(i * step, y)
        }
        drawPath(path, color = color, style = Stroke(width = 2.5f))
    }
}
