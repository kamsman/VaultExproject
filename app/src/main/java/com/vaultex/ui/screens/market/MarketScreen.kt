package com.vaultex.ui.screens.market

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.vaultex.data.remote.dto.CoinGeckoMarketDto
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.*
import com.vaultex.ui.viewmodel.MarketViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketScreen(navController: NavHostController) {

    val viewModel: MarketViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Marchés", color = TextPrimary) },
                actions = {
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualiser", tint = AccentGold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgPrimary)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Surface) {
                NavigationBarItem(selected = false,
                    onClick = { navController.navigate(Routes.DASHBOARD) },
                    icon = { Icon(Icons.Default.TrendingUp, contentDescription = null) })
                NavigationBarItem(selected = true,
                    onClick = {},
                    icon = { Icon(Icons.Default.TrendingUp, contentDescription = null) })
                NavigationBarItem(selected = false,
                    onClick = { navController.navigate(Routes.SWAP) },
                    icon = { Icon(Icons.Default.TrendingDown, contentDescription = null) })
            }
        },
        containerColor = BgPrimary
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(BgPrimary, BgSecondary)))
                .padding(padding)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.search(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Rechercher Bitcoin, ETH…", color = TextSecondary, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentGold, unfocusedBorderColor = BgSecondary,
                    focusedContainerColor = BgSecondary, unfocusedContainerColor = BgSecondary,
                    cursorColor = AccentGold, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                )
            )

            when (val s = state) {
                MarketViewModel.UiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentGold)
                    }
                }
                is MarketViewModel.UiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(s.message, color = AccentRed, fontSize = 14.sp)
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = { viewModel.load() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGold)) {
                                Text("Réessayer")
                            }
                        }
                    }
                }
                is MarketViewModel.UiState.Success -> {
                    if (s.coins.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Aucun résultat", color = TextMuted)
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(s.coins) { index, coin ->
                                CoinRow(rank = index + 1, coin = coin)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoinRow(rank: Int, coin: CoinGeckoMarketDto) {
    val changePositive = coin.change24h >= 0
    val changeColor = if (changePositive) Color(0xFF4CAF50) else AccentRed

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank
            Text(
                "#$rank",
                color = TextMuted, fontSize = 11.sp,
                modifier = Modifier.width(28.dp)
            )

            // Icon
            AsyncImage(
                model = coin.image,
                contentDescription = coin.name,
                modifier = Modifier.size(36.dp).clip(CircleShape)
            )

            Spacer(Modifier.width(10.dp))

            // Name + symbol
            Column(modifier = Modifier.weight(1f)) {
                Text(coin.name, color = TextPrimary, fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(coin.symbol.uppercase(), color = TextSecondary, fontSize = 12.sp)
            }

            // Sparkline
            if (coin.sparkline_in_7d?.price?.isNotEmpty() == true) {
                SparklineChart(
                    prices = coin.sparkline_in_7d.price,
                    positive = changePositive,
                    modifier = Modifier.width(60.dp).height(32.dp)
                )
                Spacer(Modifier.width(10.dp))
            }

            // Price + change
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "$${formatPrice(coin.currentPrice)}",
                    color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (changePositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = changeColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        "${"%.2f".format(kotlin.math.abs(coin.change24h))}%",
                        color = changeColor, fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SparklineChart(prices: List<Double>, positive: Boolean, modifier: Modifier) {
    val color = if (positive) Color(0xFF4CAF50) else AccentRed
    androidx.compose.foundation.Canvas(modifier = modifier) {
        if (prices.size < 2) return@Canvas
        val min = prices.min()
        val max = prices.max()
        val range = (max - min).takeIf { it > 0 } ?: 1.0
        val step = size.width / (prices.size - 1)
        val path = Path()
        prices.forEachIndexed { i, p ->
            val x = i * step
            val y = size.height - ((p - min) / range * size.height).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = color, style = Stroke(width = 1.5.dp.toPx()))
        val lastX = (prices.size - 1) * step
        val lastY = size.height - ((prices.last() - min) / range * size.height).toFloat()
        drawCircle(color, radius = 2.dp.toPx(), center = Offset(lastX, lastY))
    }
}

private fun formatPrice(price: Double): String = when {
    price >= 1000 -> "%,.0f".format(price)
    price >= 1    -> "%.2f".format(price)
    price >= 0.01 -> "%.4f".format(price)
    else          -> "%.6f".format(price)
}
