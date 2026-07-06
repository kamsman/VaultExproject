package com.vaultex.ui.screens.market

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import com.vaultex.ui.viewmodel.MarketViewModel
import java.text.NumberFormat
import java.util.Locale

private enum class MarketFilter { ALL, SWAPPABLE, GAINERS, LOSERS, FAVORITES }

@Composable
fun MarketScreen(navController: NavHostController) {

    val viewModel: MarketViewModel = hiltViewModel()
    val markets by viewModel.markets.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val global by viewModel.global.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val isStale by viewModel.isStale.collectAsState()
    val loadError by viewModel.loadError.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadMarkets() }

    var searchQuery by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(MarketFilter.ALL) }

    val filtered = markets
        .filter { it.name.contains(searchQuery, true) || it.symbol.contains(searchQuery, true) }
        .filter {
            when (filter) {
                MarketFilter.ALL -> true
                MarketFilter.SWAPPABLE -> it.symbol.uppercase() in com.vaultex.ui.viewmodel.SwapViewModel.SWAPPABLE_SYMBOLS
                MarketFilter.GAINERS -> it.change24h > 0
                MarketFilter.LOSERS -> it.change24h < 0
                MarketFilter.FAVORITES -> it.id in favorites
            }
        }
    val topGainers = markets.filter { it.change24h > 0 }.sortedByDescending { it.change24h }.take(6)

    Scaffold(
        bottomBar = { VaultExBottomBar(navController) },
        containerColor = BgPrimary
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            // ─── Titre + cloche ───
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.market_title),
                        fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { navController.navigate(Routes.NOTIFICATIONS) }) {
                        Icon(Icons.Default.Notifications, stringResource(R.string.notifications), tint = TextPrimary)
                    }
                }
            }

            // ─── Bandeau hors ligne (données du cache) ───
            if (isStale && markets.isNotEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text(
                            stringResource(R.string.offline_cached),
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFB45309),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // ─── Bandeau global : cap. totale + dominance BTC ───
            global?.let { g ->
                item {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GlobalStatCard(
                            title = stringResource(R.string.market_cap_total),
                            value = compactUsd(g.totalMcapUsd),
                            change = g.mcapChange24h,
                            modifier = Modifier.weight(1f)
                        )
                        GlobalStatCard(
                            title = stringResource(R.string.market_btc_dominance),
                            value = String.format(Locale.US, "%.1f%%", g.btcDominance),
                            change = null,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ─── Recherche ───
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.market_search_hint), color = TextMuted, fontSize = 14.sp) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
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
            }

            // ─── Filtres Tous / Gagnants / Perdants / Favoris ───
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterPill(stringResource(R.string.market_filter_all), filter == MarketFilter.ALL) { filter = MarketFilter.ALL }
                    FilterPill(stringResource(R.string.market_swappable) + " ⇄", filter == MarketFilter.SWAPPABLE) { filter = MarketFilter.SWAPPABLE }
                    FilterPill(stringResource(R.string.market_filter_gainers) + " ↗", filter == MarketFilter.GAINERS) { filter = MarketFilter.GAINERS }
                    FilterPill(stringResource(R.string.market_filter_losers) + " ↘", filter == MarketFilter.LOSERS) { filter = MarketFilter.LOSERS }
                    FilterPill(stringResource(R.string.market_filter_favs) + " ★", filter == MarketFilter.FAVORITES) { filter = MarketFilter.FAVORITES }
                }
            }

            // ─── Top gagnants (cartes horizontales) ───
            if (topGainers.isNotEmpty() && filter == MarketFilter.ALL && searchQuery.isBlank()) {
                item {
                    Column(Modifier.padding(top = 12.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🚀 " + stringResource(R.string.market_top_gainers), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            topGainers.forEach { dto ->
                                GainerCard(dto) { navController.navigate(Routes.coinDetail(dto.id)) }
                            }
                        }
                    }
                }
            }

            // ─── Liste des cryptos ───
            if (isLoading && markets.isEmpty()) {
                item { HistoryListSkeleton() }
            } else if (loadError && markets.isEmpty()) {
                // Chargement échoué ET aucun cache (1re ouverture hors ligne /
                // rate-limit CoinGecko) : message + bouton « Actualiser » manuel.
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            stringResource(R.string.coin_load_error),
                            color = TextSecondary, fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Button(
                            onClick = { viewModel.loadMarkets() },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                        ) { Text(stringResource(R.string.history_refresh)) }
                    }
                }
            } else {
                item {
                    Text(
                        stringResource(R.string.market_all_cryptos),
                        fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
                items(filtered, key = { it.id }) { dto ->
                    CoinRowCard(
                        dto = dto,
                        isFavorite = dto.id in favorites,
                        onToggleFavorite = { viewModel.toggleFavorite(dto.id) },
                        onAlert = { navController.navigate(Routes.NOTIFICATIONS) },
                        onClick = { navController.navigate(Routes.coinDetail(dto.id)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GlobalStatCard(title: String, value: String, change: Double?, modifier: Modifier) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        modifier = modifier
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 12.sp, color = TextSecondary)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
            change?.let {
                Text(
                    (if (it >= 0) "+" else "") + String.format(Locale.US, "%.2f%%", it) + " (24h)",
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = if (it >= 0) AccentGreen else AccentRed
                )
            }
        }
    }
}

@Composable
private fun GainerCard(dto: CoinGeckoMarketDto, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        modifier = Modifier.width(120.dp).clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            coil.compose.AsyncImage(model = dto.image, contentDescription = dto.symbol, modifier = Modifier.size(30.dp).clip(CircleShape))
            Text(dto.symbol.uppercase(), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
            Text("$" + formatMarketUsd(dto.currentPrice), fontSize = 12.sp, color = TextSecondary)
            Text("+" + String.format(Locale.US, "%.2f%%", dto.change24h), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
            Sparkline(dto.sparkline_in_7d?.price ?: emptyList(), AccentGreen, Modifier.fillMaxWidth().height(20.dp))
        }
    }
}

@Composable
private fun CoinRowCard(
    dto: CoinGeckoMarketDto,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onAlert: () -> Unit,
    onClick: () -> Unit
) {
    val isPositive = dto.change24h >= 0
    val changeColor = if (isPositive) AccentGreen else AccentRed

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            coil.compose.AsyncImage(
                model = dto.image, contentDescription = dto.symbol,
                modifier = Modifier.size(40.dp).clip(CircleShape)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(dto.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(dto.symbol.uppercase(), fontSize = 11.sp, color = TextSecondary)
                    if (dto.symbol.uppercase() in com.vaultex.ui.viewmodel.SwapViewModel.SWAPPABLE_SYMBOLS) {
                        androidx.compose.material3.Surface(shape = RoundedCornerShape(5.dp), color = AccentGreen.copy(alpha = 0.13f)) {
                            Text(stringResource(R.string.market_swappable), fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                color = AccentGreen, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("$" + formatMarketUsd(dto.currentPrice), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                Spacer(Modifier.height(2.dp))
                Sparkline(dto.sparkline_in_7d?.price ?: emptyList(), changeColor, Modifier.size(width = 56.dp, height = 18.dp))
            }
            Spacer(Modifier.width(8.dp))
            androidx.compose.material3.Surface(shape = RoundedCornerShape(8.dp), color = changeColor.copy(alpha = 0.13f)) {
                Text(
                    "${if (isPositive) "+" else ""}${String.format(Locale.US, "%.2f", dto.change24h)}%",
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = changeColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Default.NotificationsNone, stringResource(R.string.alerts_title), tint = TextSecondary,
                modifier = Modifier.size(20.dp).clip(CircleShape).clickable(onClick = onAlert)
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                stringResource(R.string.market_filter_favs),
                tint = if (isFavorite) Color(0xFFF5B301) else TextSecondary,
                modifier = Modifier.size(20.dp).clip(CircleShape).clickable(onClick = onToggleFavorite)
            )
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

/** Vraie mini-courbe 7 j (données CoinGecko), sous-échantillonnée pour rester légère. */
@Composable
private fun Sparkline(prices: List<Double>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (prices.size < 2) return@Canvas
        val pts = if (prices.size > 40) prices.filterIndexed { i, _ -> i % (prices.size / 40) == 0 } else prices
        val min = pts.min(); val max = pts.max()
        val range = (max - min).takeIf { it > 0 } ?: 1.0
        val step = size.width / (pts.size - 1)
        val path = Path()
        pts.forEachIndexed { i, p ->
            val x = i * step
            val y = size.height * (1f - ((p - min) / range).toFloat())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = color, style = Stroke(width = 2f))
    }
}

/** Cap. de marché compacte : 3,52 T$, 70,1 Md$… */
private fun compactUsd(v: Double): String = when {
    v >= 1e12 -> String.format(Locale.US, "$%.2fT", v / 1e12)
    v >= 1e9 -> String.format(Locale.US, "$%.1fB", v / 1e9)
    v >= 1e6 -> String.format(Locale.US, "$%.1fM", v / 1e6)
    else -> String.format(Locale.US, "$%,.0f", v)
}

/** Prix en USD : 4 décimales sous 1 $, 2 décimales sinon, sans décimale au-delà de 1000. */
internal fun formatMarketUsd(value: Double): String =
    NumberFormat.getNumberInstance(Locale.FRANCE).apply {
        maximumFractionDigits = when {
            value < 1.0 -> 4
            value < 1000.0 -> 2
            else -> 0
        }
    }.format(value)
