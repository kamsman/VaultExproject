package com.vaultex.ui.screens.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vaultex.R
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.AccentBlueDark
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.BgTertiary
import com.vaultex.ui.theme.Surface
import com.vaultex.ui.theme.SurfaceLight
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary
import com.vaultex.ui.viewmodel.PortfolioViewModel
import com.vaultex.ui.viewmodel.TokenBalance
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(navController: NavHostController) {
    val viewModel: PortfolioViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    val totalUsd = state.totalBalanceUsd
    val tokens = state.tokens
        .sortedByDescending { it.valueUsd }
        .filter { it.valueUsd > 0.0 }
        .ifEmpty { state.tokens.sortedByDescending { it.valueUsd } }
        .take(6)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.portfolio_title),
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
        containerColor = BgPrimary
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ─── Carte « Valeur totale » (dégradé bleu) + barres de répartition ───
            item {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Brush.verticalGradient(listOf(AccentBlue, AccentBlueDark)))
                            .padding(vertical = 22.dp, horizontal = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                stringResource(R.string.portfolio_total_value),
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "$" + formatUsd(totalUsd),
                                color = Color.White,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                formatXof(state.totalBalanceXof) + " XOF",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp
                            )
                            if (state.isLoading) {
                                Spacer(Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color.White,
                                    trackColor = Color.White.copy(alpha = 0.25f)
                                )
                            }
                        }
                    }
                    if (tokens.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        DistributionBars(tokens = tokens, totalUsd = totalUsd)
                    }
                }
            }

            // ─── Carte « Répartition » ───
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.portfolio_repartition),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        tokens.forEachIndexed { index, token ->
                            PortfolioTokenRow(
                                token = token,
                                share = if (totalUsd > 0.0) token.valueUsd / totalUsd else 0.0,
                                onClick = { navController.navigate(Routes.PORTFOLIO_TOKEN_DETAIL) }
                            )
                            if (index < tokens.lastIndex) {
                                HorizontalDivider(color = SurfaceLight, thickness = 1.dp)
                            }
                        }
                        state.error?.let { err ->
                            Text(
                                err,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DistributionBars(tokens: List<TokenBalance>, totalUsd: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        tokens.forEach { token ->
            val share = if (totalUsd > 0.0) token.valueUsd / totalUsd else 0.0
            val barHeight = (10 + 38 * share).dp
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight)
                        .clip(RoundedCornerShape(3.dp))
                        .background(token.color())
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    token.symbol.substringBefore("-"),
                    fontSize = 9.sp,
                    color = TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun PortfolioTokenRow(token: TokenBalance, share: Double, onClick: () -> Unit) {
    val tokenColor = token.color()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(tokenColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    token.symbol.take(2),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    token.symbol,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextPrimary
                )
                Text(
                    stringResource(R.string.portfolio_percent_of, "%.0f".format(share * 100)),
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            Text(
                "$" + formatUsd(token.valueUsd),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = TextPrimary
            )
        }
        Spacer(Modifier.height(8.dp))
        // Barre de progression colorée (part du portfolio)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(BgTertiary)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(share.toFloat().coerceIn(0.02f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(tokenColor)
            )
        }
    }
}

private fun TokenBalance.color(): Color = try {
    Color(android.graphics.Color.parseColor(colorHex))
} catch (_: Exception) {
    AccentBlue
}

private fun formatUsd(value: Double): String =
    NumberFormat.getNumberInstance(Locale.FRANCE).apply {
        maximumFractionDigits = 2; minimumFractionDigits = 2
    }.format(value)

private fun formatXof(value: Double): String =
    NumberFormat.getNumberInstance(Locale.FRANCE).format(value.toLong())
