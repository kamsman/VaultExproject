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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vaultex.R
import com.vaultex.data.remote.dto.CoinGeckoMarketDto
import com.vaultex.ui.components.HistoryListSkeleton
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
import com.vaultex.ui.viewmodel.MarketViewModel
import java.text.NumberFormat
import java.util.Locale
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

    val viewModel: MarketViewModel = hiltViewModel()
    val markets by viewModel.markets.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadMarkets() }

    // Données live CoinGecko (en XOF) ; repli sur une liste statique en cas d'échec.
    val liveCoins = markets.map { dto ->
        CoinRow(
            id = dto.id,
            name = dto.name,
            symbol = dto.symbol.uppercase(),
            priceXof = formatMarketUsd(dto.currentPrice),
            change24h = dto.change24h,
            marketCap = "",
            color = marketColor(dto.symbol)
        )
    }
    val coins = if (liveCoins.isNotEmpty()) liveCoins else FALLBACK_COINS

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

            // ─── Liste des cryptos (skeleton au 1er chargement) ───
            if (isLoading && markets.isEmpty()) {
                HistoryListSkeleton()
            } else {
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
                Text("$" + coin.priceXof, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
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

/** Prix en FCFA : grands nombres sans décimale, petits montants à 2 décimales. */
/** Prix en USD : 4 décimales sous 1 $, 2 décimales sinon, sans décimale au-delà de 1000. */
private fun formatMarketUsd(value: Double): String =
    NumberFormat.getNumberInstance(Locale.FRANCE).apply {
        maximumFractionDigits = when {
            value < 1.0 -> 4
            value < 1000.0 -> 2
            else -> 0
        }
    }.format(value)

private fun marketColor(symbol: String): Color = when (symbol.uppercase()) {
    "BTC"  -> VaultExColors.BitcoinOrange
    "ETH"  -> VaultExColors.EthereumBlue
    "BNB"  -> VaultExColors.BnbYellow
    "SOL"  -> VaultExColors.SolanaGreen
    "TRX"  -> VaultExColors.TronRed
    "USDT" -> Color(0xFF26A17B)
    "USDC" -> Color(0xFF2775CA)
    else   -> Color(0xFF1A6FE8)
}

/** Repli affiché si l'appel CoinGecko échoue (jamais d'écran vide). */
private val FALLBACK_COINS = listOf(
    CoinRow("bitcoin", "Bitcoin", "BTC", "65 000", 2.4, "", VaultExColors.BitcoinOrange),
    CoinRow("ethereum", "Ethereum", "ETH", "3 200", 3.1, "", VaultExColors.EthereumBlue),
    CoinRow("binancecoin", "BNB", "BNB", "600", -0.8, "", VaultExColors.BnbYellow),
    CoinRow("solana", "Solana", "SOL", "150", 7.2, "", VaultExColors.SolanaGreen),
    CoinRow("tron", "Tron", "TRX", "0.12", 5.1, "", VaultExColors.TronRed),
    CoinRow("tether", "Tether", "USDT", "1.00", 0.01, "", Color(0xFF26A17B)),
    CoinRow("usd-coin", "USD Coin", "USDC", "1.00", 0.0, "", Color(0xFF2775CA)),
)
