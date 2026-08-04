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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SwapHoriz
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
    val favorites by viewModel.favorites.collectAsState()
    val periods = listOf("24H", "7J", "1M", "3M", "1A")
    var selectedPeriod by remember { mutableStateOf("7J") }

    LaunchedEffect(coinId, selectedPeriod, coin) {
        /*
        7 J s'appuie normalement sur le sparkline livré avec la fiche : aucun
        appel réseau supplémentaire. Mais si ce sparkline manque — fiche servie
        par le cache disque allégé, ou CoinGecko qui l'a omis — on se rabattait
        sur RIEN, et le graphique restait vide sans que personne ne tente de le
        charger. On demande alors explicitement market_chart sur 7 jours.
         */
        val hasSparkline = (coin?.sparkline_in_7d?.price?.size ?: 0) >= 2
        if (selectedPeriod != "7J" || !hasSparkline) {
            viewModel.loadChart(coinId, daysForPeriod(selectedPeriod))
        }
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
                actions = {
                    val isFav = coinId in favorites
                    IconButton(onClick = { viewModel.toggleFavorite(coinId) }) {
                        Icon(
                            if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = stringResource(R.string.market_filter_favs),
                            tint = if (isFav) Color(0xFFF5B301) else TextSecondary
                        )
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
            NumberFormat.getNumberInstance(com.vaultex.core.session.LocaleManager.appLocale()).apply { maximumFractionDigits = 2 }
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

            // Logo token (réel via CoinGecko) ou cercle coloré en repli + prix
            val logoUrl = c.image
            if (!logoUrl.isNullOrEmpty()) {
                coil.compose.AsyncImage(
                    model = logoUrl,
                    contentDescription = symbol,
                    modifier = Modifier.size(56.dp).clip(CircleShape)
                )
            } else {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(tokenColor(symbol)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(symbol.take(2), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            // « SYMBOLE · Nom  #rang » (maquette)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("$symbol · ${c.name}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                if (c.rank > 0) {
                    Surface(shape = RoundedCornerShape(6.dp), color = BgPrimary) {
                        Text("#${c.rank}", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
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

            // ─── « Votre solde / Gain 24h » si l'utilisateur détient la monnaie ───
            val holding = remember(symbol) { viewModel.holdingOf(symbol) }
            if (holding != null) {
                Spacer(Modifier.height(14.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BgPrimary),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Row(Modifier.padding(vertical = 14.dp)) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.coin_your_balance), fontSize = 12.sp, color = TextSecondary)
                            Spacer(Modifier.height(4.dp))
                            Text(trimAmount(holding.amount) + " " + symbol, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextPrimary)
                            Text("≈ $" + usdFmt.format(holding.valueUsd), fontSize = 12.sp, color = TextSecondary)
                        }
                        VerticalDivider(Modifier.height(52.dp), color = BorderColor)
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.coin_gain_24h), fontSize = 12.sp, color = TextSecondary)
                            Spacer(Modifier.height(4.dp))
                            val gainTok = holding.amount * c.change24h / 100.0
                            val gainUsd = holding.valueUsd * c.change24h / 100.0
                            val gCol = if (c.change24h >= 0) AccentGreen else AccentRed
                            Text((if (gainTok >= 0) "+" else "") + trimAmount(gainTok) + " " + symbol, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = gCol)
                            Text("≈ $" + usdFmt.format(kotlin.math.abs(gainUsd)) + " (${"%+.2f".format(c.change24h)}%)", fontSize = 12.sp, color = gCol)
                        }
                    }
                }
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

            // ─── Actions : Envoyer · Recevoir · (Swap) · Alerte ───
            // 3 niveaux réels de support :
            //  🟢 Échangeable (registre swap)         → Envoyer + Recevoir + Swap
            //  🔵 Existe sur Ethereum/BSC (hors registre) → Envoyer + Recevoir seuls
            //  ⚪ Ni l'un ni l'autre                    → consultation + alerte
            val supported = com.vaultex.ui.viewmodel.SwapViewModel.assetForSymbol(symbol)
            val receivable by viewModel.receivableToken.collectAsState()
            val receivableChecking by viewModel.receivableChecking.collectAsState()
            LaunchedEffect(coinId, supported) {
                // Inutile de sonder ETH/BSC : déjà pleinement pris en charge.
                if (supported == null) viewModel.checkReceivable(coinId)
            }
            val receiveOnlyKey = receivable?.symbol

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BgPrimary),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        if (supported != null || receiveOnlyKey != null) {
                            val bufferKey = supported?.key ?: receiveOnlyKey!!
                            CircleAction(Icons.Default.ArrowUpward, stringResource(R.string.action_send)) {
                                // Ouvre le formulaire d'envoi DIRECTEMENT sur cette
                                // monnaie ; le token (registre OU déjà résolu par
                                // contrat ETH/BSC) est reconnu côté SendViewModel.
                                com.vaultex.core.session.TokenSelectionBuffer.set(bufferKey)
                                navController.navigate(Routes.SEND)
                            }
                            CircleAction(Icons.Default.ArrowDownward, stringResource(R.string.action_receive)) {
                                com.vaultex.core.session.TokenSelectionBuffer.set(bufferKey)
                                navController.navigate(Routes.RECEIVE)
                            }
                            if (supported != null) {
                                CircleAction(Icons.Default.SwapHoriz, stringResource(R.string.tab_swap)) {
                                    com.vaultex.core.session.TokenSelectionBuffer.set(supported.key)
                                    navController.navigate(Routes.SWAP)
                                }
                            }
                        }
                        CircleAction(Icons.Default.NotificationsNone, stringResource(R.string.coin_alert)) {
                            navController.navigate(Routes.NOTIFICATIONS)
                        }
                    }
                    when {
                        supported != null -> {}
                        receivableChecking -> {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(color = AccentBlue, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.coin_checking_network), fontSize = 12.sp, color = TextSecondary)
                            }
                        }
                        receiveOnlyKey != null -> {
                            val netLabel = if (receivable?.chainTicker == "BNB") "BEP20 · BNB Chain" else "ERC20 · Ethereum"
                            Text(
                                stringResource(R.string.coin_receive_only, netLabel),
                                fontSize = 12.sp, color = TextSecondary,
                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                        else -> {
                            Text(
                                stringResource(R.string.coin_view_only),
                                fontSize = 12.sp, color = TextSecondary,
                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ─── Stats 3×2 : Cap / Volume / Offre — High / Low / ATH (maquette) ───
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(stringResource(R.string.market_cap), "$" + compact(c.marketCap), Modifier.weight(1f))
                    StatCard(stringResource(R.string.coin_volume_label), "$" + compact(c.volume24h), Modifier.weight(1f))
                    StatCard(stringResource(R.string.coin_supply), compact(c.circulatingSupply) + " " + symbol, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(stringResource(R.string.coin_high_24h), "$" + usdFmt.format(c.high24h), Modifier.weight(1f))
                    StatCard(stringResource(R.string.coin_low_24h), "$" + usdFmt.format(c.low24h), Modifier.weight(1f))
                    StatCard(stringResource(R.string.coin_ath), "$" + usdFmt.format(c.ath), Modifier.weight(1f))
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
    "3M" -> 90
    "1A" -> 365
    else -> 7
}

/** Montant lisible (8 décimales max, sans zéros inutiles). */
private fun trimAmount(v: Double): String =
    java.math.BigDecimal.valueOf(v).setScale(8, java.math.RoundingMode.DOWN)
        .stripTrailingZeros().toPlainString()

/** Bouton d'action rond (icône bleue sur pastille + libellé). */
@Composable
private fun CircleAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(AccentBlue)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
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
