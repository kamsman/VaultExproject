package com.vaultex.ui.screens.tokens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vaultex.R
import com.vaultex.ui.components.TokenDetailSkeleton
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.VaultExColors
import com.vaultex.ui.viewmodel.TokenDetailViewModel
import java.text.NumberFormat
import java.util.Locale

@Composable
fun TokenDetailScreen(navController: NavController, symbol: String = "ETH") {
    val viewModel: TokenDetailViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.name.ifEmpty { symbol }, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.token_detail_refresh), tint = VaultExColors.BluePrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultExColors.Background)
            )
        },
        containerColor = VaultExColors.Background
    ) { padding ->
        Crossfade(targetState = state.isLoading, label = "token_detail_reveal") { loading ->
        if (loading) {
            TokenDetailSkeleton(Modifier.padding(padding))
        } else {
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(Modifier.height(4.dp))

                // Price header
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = VaultExColors.CardBackground),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).background(VaultExColors.BlueLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(symbol.take(1), color = VaultExColors.BluePrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                coil.compose.AsyncImage(
                                    model = com.vaultex.ui.components.CryptoIcon.url(symbol),
                                    contentDescription = symbol,
                                    modifier = Modifier.size(44.dp).clip(CircleShape)
                                )
                            }
                            Column {
                                Text(symbol, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                Text(state.name, fontSize = 13.sp, color = VaultExColors.TextSecondary)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        /*
                         * Cours affiché SEULEMENT s'il est connu.
                         *
                         * Un jeton hors registre n'a pas d'identifiant de
                         * cotation : afficher « 0,00 $ · +0,00 % » laisserait
                         * croire à une valeur nulle, et la version précédente
                         * affichait pire encore — le cours d'Ethereum, hérité
                         * d'une valeur de repli. Sur un portefeuille, mieux
                         * vaut ne rien annoncer qu'annoncer faux.
                         */
                        if (state.priceUsd > 0.0) {
                            Text(
                                formatUsd(state.priceUsd),
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp,
                                color = VaultExColors.TextPrimary
                            )
                            val isPositive = state.change24h >= 0
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(
                                    if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                    null,
                                    tint = if (isPositive) VaultExColors.Success else VaultExColors.Error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    stringResource(R.string.token_detail_change_24h, "%+.2f%%".format(state.change24h)),
                                    fontSize = 14.sp,
                                    color = if (isPositive) VaultExColors.Success else VaultExColors.Error,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else if (!state.isLoading) {
                            Text(
                                stringResource(R.string.token_detail_price_unavailable),
                                fontSize = 14.sp,
                                color = VaultExColors.TextSecondary
                            )
                        }
                        if (state.marketCapUsd > 0) {
                            Text(
                                stringResource(R.string.token_detail_market_cap, formatUsd(state.marketCapUsd)),
                                fontSize = 12.sp,
                                color = VaultExColors.TextSecondary
                            )
                        }
                    }
                }

                // 7-day sparkline chart
                if (state.chartPrices.size >= 2) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = VaultExColors.CardBackground),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.token_detail_chart_7d), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Spacer(Modifier.height(12.dp))
                            SparklineChart(
                                prices = state.chartPrices,
                                color = if (state.change24h >= 0) VaultExColors.Success else VaultExColors.Error,
                                modifier = Modifier.fillMaxWidth().height(100.dp)
                            )
                        }
                    }
                }

                // Wallet balance card
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = VaultExColors.CardBackground),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.token_detail_my_wallet), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        // Solde détenu (le fond)
                        Text(
                            state.amountFormatted.ifEmpty { "—" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = VaultExColors.TextPrimary
                        )
                        if (state.valueUsd > 0.0) {
                            Text(
                                "≈ " + formatUsd(state.valueUsd),
                                fontSize = 13.sp,
                                color = VaultExColors.TextSecondary
                            )
                        }
                        // Le message n'apparaît QUE si le calcul de l'adresse a
                        // abouti sans rien donner. Tant qu'il est en cours, on ne
                        // dit rien : une adresse vide ne signifie pas encore
                        // « wallet absent ». Voir State.addressResolved.
                        if (state.address.isEmpty()) {
                            if (state.addressResolved) {
                                Text(
                                    stringResource(R.string.token_detail_wallet_not_initialized),
                                    fontSize = 13.sp,
                                    color = VaultExColors.Error
                                )
                            }
                        } else {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                stringResource(R.string.token_detail_address, symbol),
                                fontSize = 12.sp,
                                color = VaultExColors.TextSecondary
                            )
                            Text(
                                state.address,
                                fontSize = 11.sp,
                                color = VaultExColors.TextPrimary
                            )
                        }
                    }
                }

                // Action buttons row 1
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            com.vaultex.core.session.TokenSelectionBuffer.set(symbol)
                            navController.navigate(Routes.SEND)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VaultExColors.BluePrimary)
                    ) {
                        Icon(Icons.Default.Send, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_send))
                    }
                    OutlinedButton(
                        onClick = {
                            com.vaultex.core.session.TokenSelectionBuffer.set(symbol)
                            navController.navigate(Routes.RECEIVE)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_receive))
                    }
                }

                // Action buttons row 2
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            com.vaultex.core.session.TokenSelectionBuffer.set(symbol)
                            navController.navigate(Routes.SWAP)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.SwapHoriz, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_swap))
                    }
                    OutlinedButton(
                        onClick = { navController.navigate(Routes.HISTORY) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.History, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.history_title))
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
        }
    }
}

@Composable
private fun SparklineChart(prices: List<Double>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (prices.size < 2) return@Canvas
        val min = prices.min()
        val max = prices.max()
        val range = (max - min).coerceAtLeast(0.0001)
        val step = size.width / (prices.size - 1)

        val path = Path()
        prices.forEachIndexed { i, price ->
            val x = i * step
            val y = size.height - ((price - min) / range * size.height).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = color, style = Stroke(width = 3f))

        // Mark latest price dot
        val lastX = (prices.size - 1) * step
        val lastY = size.height - ((prices.last() - min) / range * size.height).toFloat()
        drawCircle(color = color, radius = 6f, center = Offset(lastX, lastY))
    }
}

private fun formatUsd(value: Double): String {
    if (value == 0.0) return "$ —"
    val nf = NumberFormat.getNumberInstance(Locale.US)
    nf.minimumFractionDigits = if (value < 1.0) 4 else 2
    nf.maximumFractionDigits = if (value < 1.0) 6 else 2
    return "$ ${nf.format(value)}"
}
