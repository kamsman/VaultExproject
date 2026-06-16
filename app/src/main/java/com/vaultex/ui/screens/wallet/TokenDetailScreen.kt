package com.vaultex.ui.screens.wallet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vaultex.R
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.AccentGreen
import com.vaultex.ui.theme.AccentRed
import com.vaultex.ui.theme.BorderColor
import com.vaultex.ui.theme.Surface
import com.vaultex.ui.theme.SurfaceLight
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary
import com.vaultex.ui.viewmodel.PortfolioViewModel
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenDetailScreen(
    navController: NavHostController
) {
    val viewModel: PortfolioViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    // Pas d'argument de route : on affiche le token principal du portfolio (BTC en priorité)
    val token = state.tokens.firstOrNull { it.symbol == "BTC" }
        ?: state.tokens.maxByOrNull { it.valueUsd }

    val symbol = token?.symbol ?: "BTC"
    val name = token?.name ?: "Bitcoin"
    val tokenColor = try {
        Color(android.graphics.Color.parseColor(token?.colorHex ?: "#F7931A"))
    } catch (_: Exception) { AccentBlue }

    val change = token?.changePercent24h ?: 0.0
    val amount = token?.amountFormatted
        ?.substringBefore(" ")
        ?.replace(" ", "")
        ?.replace(",", ".")
        ?.toDoubleOrNull() ?: 0.0
    val unitPrice = if (amount > 0.0) (token?.valueUsd ?: 0.0) / amount else 0.0

    val periods = listOf("1H", "24H", "7J", "1M")
    var selectedPeriod by remember { mutableStateOf("24H") }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.token_detail_header, symbol),
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = AccentBlue
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Surface)
            )
        },
        containerColor = Surface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // ─── En-tête centré : avatar, nom, montant détenu ───
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(tokenColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        symbol.take(2),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(name, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    token?.amountFormatted ?: "0 $symbol",
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    color = AccentBlue
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(
                        R.string.token_approx_value,
                        "$" + formatNumber(token?.valueUsd ?: 0.0),
                        formatXofValue(token?.valueXof ?: 0.0)
                    ),
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }

            // ─── Graphique pleine largeur ───
            DemoLineChart(
                seed = symbol.hashCode() + selectedPeriod.hashCode(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(SurfaceLight)
            )

            // ─── Sélecteur de période ───
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                periods.forEach { period ->
                    PeriodPill(
                        label = period,
                        selected = selectedPeriod == period,
                        onClick = { selectedPeriod = period }
                    )
                }
            }

            // ─── Carte de statistiques ───
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    StatLine(stringResource(R.string.stat_current_price), "$" + formatNumber(unitPrice))
                    HorizontalDivider(color = SurfaceLight, thickness = 1.dp)
                    StatLine(
                        label = stringResource(R.string.stat_var_24h),
                        value = "%+.1f%%".format(change),
                        valueColor = if (change >= 0) AccentGreen else AccentRed
                    )
                    HorizontalDivider(color = SurfaceLight, thickness = 1.dp)
                    StatLine(
                        stringResource(R.string.stat_high_24h),
                        "$" + formatNumber(unitPrice * (1 + abs(change) / 100))
                    )
                    HorizontalDivider(color = SurfaceLight, thickness = 1.dp)
                    StatLine(
                        stringResource(R.string.stat_low_24h),
                        "$" + formatNumber(unitPrice * (1 - abs(change) / 100))
                    )
                }
            }

            Spacer(Modifier.height(48.dp))

            // ─── Boutons d'action ───
            Button(
                onClick = { navController.navigate(Routes.SWAP) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text(
                    stringResource(R.string.action_buy_swap),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { navController.navigate(Routes.RECEIVE) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, AccentBlue),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue)
            ) {
                Text(
                    stringResource(R.string.action_receive_token, symbol),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PeriodPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) AccentBlue else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp)
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
private fun StatLine(label: String, value: String, valueColor: Color = TextPrimary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = TextSecondary)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

/** Courbe de démonstration (pas de données de chart dans le PortfolioViewModel). */
@Composable
internal fun DemoLineChart(seed: Int, modifier: Modifier = Modifier) {
    // Lue dans le contexte @Composable, puis utilisée dans le lambda DrawScope.
    val lineColor = AccentBlue
    Canvas(modifier = modifier) {
        val random = Random(seed)
        val points = 90
        val step = size.width / (points - 1)
        var y = size.height * 0.55f
        val path = Path()
        path.moveTo(0f, y)
        for (i in 1 until points) {
            y = (y + (random.nextFloat() - 0.5f) * size.height * 0.45f)
                .coerceIn(size.height * 0.08f, size.height * 0.92f)
            path.lineTo(i * step, y)
        }
        drawPath(path, color = lineColor, style = Stroke(width = 3f))
    }
}

private fun formatNumber(value: Double): String =
    NumberFormat.getNumberInstance(Locale.FRANCE).apply {
        maximumFractionDigits = 2
    }.format(value)

private fun formatXofValue(value: Double): String =
    NumberFormat.getNumberInstance(Locale.FRANCE).format(value.toLong())
