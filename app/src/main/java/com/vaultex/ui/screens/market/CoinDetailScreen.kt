package com.vaultex.ui.screens.market

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vaultex.R
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.*
import com.vaultex.ui.viewmodel.MarketViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoinDetailScreen(navController: NavHostController, coinId: String = "bitcoin") {
    val viewModel: MarketViewModel = hiltViewModel()
    val coin by viewModel.coin.collectAsState()
    val coinLoading by viewModel.coinLoading.collectAsState()
    val coinError by viewModel.coinError.collectAsState()

    // Charge UNIQUEMENT cette pièce (appel léger), pas toute la liste marché.
    LaunchedEffect(coinId) { viewModel.loadCoin(coinId) }

    val chart by viewModel.chart.collectAsState()
    val chartLoading by viewModel.chartLoading.collectAsState()
    val periods = listOf("24H", "7J", "1M", "1A")
    var selectedPeriod by remember { mutableStateOf("7J") }

    LaunchedEffect(coinId, selectedPeriod) {
        // 7J vient du sparkline en cache → pas d'appel. Autres périodes : market_chart.
        if (selectedPeriod != "7J") viewModel.loadChart(coinId, daysForPeriod(selectedPeriod))
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        coin?.name ?: coinId.replaceFirstChar { it.uppercase() },
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back), tint = AccentBlue)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgSecondary)
            )
        },
        containerColor = BgSecondary
    ) { padding ->
        val c = coin
        if (c == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (coinError) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.coin_load_error), color = TextMuted, fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.loadCoin(coinId) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                        ) { Text(stringResource(R.string.history_refresh)) }
                    }
                } else {
                    CircularProgressIndicator(color = AccentBlue)
                }
            }
            return@Scaffold
        }

        val usdFmt = remember {
            NumberFormat.getNumberInstance(Locale.FRANCE).apply { maximumFractionDigits = 2 }
        }
        val symbol = c.symbol.uppercase()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // Cercle token + prix + badge variation
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(tokenColor(symbol)),
                contentAlignment = Alignment.Center
            ) {
                Text(symbol.take(2), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "$" + usdFmt.format(c.currentPrice),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            val positive = c.change24h >= 0
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (positive) AccentGreen.copy(alpha = 0.12f) else AccentRed.copy(alpha = 0.12f)
            ) {
                Text(
                    "%+.2f%%".format(c.change24h),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (positive) AccentGreen else AccentRed
                )
            }

            Spacer(Modifier.height(16.dp))

            // Graphique : pour 7J on réutilise le sparkline déjà en cache (aucun
            // appel réseau) ; les autres périodes passent par market_chart.
            val sparklineF = c.sparkline_in_7d?.price?.map { it.toFloat() } ?: emptyList()
            val chartPoints = if (selectedPeriod == "7J" && sparklineF.size >= 2) sparklineF else chart
            Box(
                Modifier.fillMaxWidth().height(180.dp).background(BgPrimary),
                contentAlignment = Alignment.Center
            ) {
                when {
                    chartLoading && chartPoints.isEmpty() ->
                        CircularProgressIndicator(color = AccentBlue, modifier = Modifier.size(28.dp))
                    chartPoints.size < 2 ->
                        Text(
                            stringResource(R.string.coin_chart_unavailable),
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    else ->
                        PriceLineChart(
                            points = chartPoints,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 12.dp)
                        )
                }
            }

            // Sélecteur de période
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                periods.forEach { period ->
                    val selected = period == selectedPeriod
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) AccentBlue else Color.Transparent)
                            .clickable { selectedPeriod = period }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            period,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) Color.White else TextSecondary
                        )
                    }
                }
            }

            // Cartes de stats 2×2
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(stringResource(R.string.market_cap), "$" + compact(c.marketCap), Modifier.weight(1f))
                    StatCard(stringResource(R.string.coin_volume_label), "$" + compact(c.volume24h), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(stringResource(R.string.coin_high_24h), "$" + usdFmt.format(c.high24h), Modifier.weight(1f))
                    StatCard(stringResource(R.string.coin_low_24h), "$" + usdFmt.format(c.low24h), Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(28.dp))

            // Boutons d'action
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        com.vaultex.core.session.TokenSelectionBuffer.set(symbol)
                        navController.navigate(Routes.SWAP)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                ) {
                    Text(stringResource(R.string.coin_buy_swap), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                OutlinedButton(
                    onClick = {
                        com.vaultex.core.session.TokenSelectionBuffer.set(symbol)
                        navController.navigate(Routes.RECEIVE)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, AccentBlue)
                ) {
                    Text(
                        stringResource(R.string.receive_token_fmt, symbol),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = AccentBlue
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Mappe la période UI vers le nombre de jours pour CoinGecko market_chart. */
private fun daysForPeriod(period: String): Int = when (period) {
    "24H" -> 1
    "7J" -> 7
    "1M" -> 30
    "1A" -> 365
    else -> 7
}

/** Courbe de prix dessinée au Canvas (sans dépendance externe). */
@Composable
private fun PriceLineChart(points: List<Float>, modifier: Modifier = Modifier) {
    val rising = points.last() >= points.first()
    val lineColor = if (rising) AccentGreen else AccentRed
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val min = points.min()
        val max = points.max()
        val range = (max - min).takeIf { it > 0f } ?: 1f
        val stepX = if (points.size > 1) size.width / (points.size - 1) else size.width
        fun y(v: Float) = size.height - ((v - min) / range) * size.height

        val linePath = androidx.compose.ui.graphics.Path()
        val fillPath = androidx.compose.ui.graphics.Path()
        points.forEachIndexed { i, v ->
            val px = i * stepX
            val py = y(v)
            if (i == 0) {
                linePath.moveTo(px, py)
                fillPath.moveTo(px, size.height)
                fillPath.lineTo(px, py)
            } else {
                linePath.lineTo(px, py)
                fillPath.lineTo(px, py)
            }
        }
        fillPath.lineTo((points.size - 1) * stepX, size.height)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(lineColor.copy(alpha = 0.22f), lineColor.copy(alpha = 0f))
            )
        )
        drawPath(
            path = linePath,
            color = lineColor,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 3f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        )
    }
}

private fun tokenColor(symbol: String) = when (symbol) {
    "BTC" -> NetworkBtc
    "ETH" -> NetworkEth
    "BNB" -> NetworkBnb
    "SOL" -> NetworkSol
    "TRX" -> NetworkTrx
    else -> Color(0xFF1A6FE8)
}

private fun compact(value: Double): String = when {
    value >= 1e12 -> "%.2fT".format(value / 1e12)
    value >= 1e9 -> "%.0fMd".format(value / 1e9)
    value >= 1e6 -> "%.0fM".format(value / 1e6)
    else -> "%.0f".format(value)
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = BgPrimary),
        modifier = modifier
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
    }
}
