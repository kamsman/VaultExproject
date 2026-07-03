package com.vaultex.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vaultex.R
import com.vaultex.ui.components.VaultExBottomBar
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.*
import com.vaultex.ui.viewmodel.PortfolioViewModel
import com.vaultex.ui.viewmodel.TokenBalance
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavHostController) {
    val viewModel: PortfolioViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val balanceHidden by viewModel.balanceHidden.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_name),
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleBalanceVisibility() }) {
                        Icon(
                            if (balanceHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = stringResource(if (balanceHidden) R.string.balance_show else R.string.balance_hide),
                            tint = TextPrimary
                        )
                    }
                    IconButton(onClick = { navController.navigate(Routes.NOTIFICATIONS) }) {
                        Icon(Icons.Default.Notifications, contentDescription = stringResource(R.string.notifications), tint = TextPrimary)
                    }
                    IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_title), tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgPrimary)
            )
        },
        bottomBar = { VaultExBottomBar(navController) },
        containerColor = BgPrimary
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ─── Carte solde FCFA ───
            item {
                val xofFormatted = NumberFormat.getNumberInstance(Locale.FRANCE)
                    .format(state.totalBalanceXof.toLong())

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.verticalGradient(listOf(AccentBlue, AccentBlueDark)))
                        .padding(20.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            stringResource(R.string.total_balance),
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (balanceHidden) "••••••" else "$xofFormatted FCFA",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        val positive = state.totalChangePercent >= 0
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (positive) Color(0xFFB9F3DC) else Color(0xFFFBD1D1)
                        ) {
                            Text(
                                "%+.1f%%".format(state.totalChangePercent),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                color = if (positive) Color(0xFF067A53) else Color(0xFFB42318),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (state.isLoading) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color.White,
                                trackColor = Color.White.copy(alpha = 0.25f)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                com.vaultex.ui.components.LastUpdatedLabel(
                    lastUpdated = state.lastUpdated,
                    isFromCache = state.isFromCache,
                    modifier = Modifier.padding(start = 4.dp)
                )
                state.error?.let { err ->
                    Text(err, color = AccentRed, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }

            // ─── 4 actions pastel ───
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionChip(stringResource(R.string.action_send), Icons.Default.ArrowUpward,
                        AccentRed, Modifier.weight(1f)) { navController.navigate(Routes.SEND_SELECT) }
                    ActionChip(stringResource(R.string.action_receive), Icons.Default.ArrowDownward,
                        AccentGreen, Modifier.weight(1f)) { navController.navigate(Routes.RECEIVE) }
                    ActionChip(stringResource(R.string.tab_swap), Icons.Default.SwapHoriz,
                        AccentBlue, Modifier.weight(1f)) { navController.navigate(Routes.SWAP) }
                }
            }

            item {
                Text(
                    stringResource(R.string.my_assets),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TextPrimary
                )
            }

            val visibleTokens = state.tokens.filter { it.valueXof > 0.0 || !state.isLoading }
            items(visibleTokens) { token ->
                TokenCard(token, balanceHidden) { navController.navigate(Routes.tokenDetail(token.symbol)) }
            }

            if (!state.isLoading && state.tokens.isEmpty() && state.error == null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.dashboard_no_assets), color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionChip(
    label: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = TextPrimary, fontSize = 11.sp)
    }
}

@Composable
private fun TokenCard(token: TokenBalance, hidden: Boolean, onClick: () -> Unit) {
    val xofFormatted = NumberFormat.getNumberInstance(Locale.FRANCE)
        .format(token.valueXof.toLong())
    val tokenColor = try {
        Color(android.graphics.Color.parseColor(token.colorHex))
    } catch (_: Exception) { AccentBlue }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(tokenColor),
                contentAlignment = Alignment.Center
            ) {
                Text(token.symbol.take(2), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(token.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                Text(token.amountFormatted, fontSize = 12.sp, color = TextSecondary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (hidden) "••••" else "$xofFormatted FCFA", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
                val changeColor = if (token.changePercent24h >= 0) AccentGreen else AccentRed
                Text("%+.1f%%".format(token.changePercent24h), fontSize = 12.sp, color = changeColor)
            }
        }
    }
}
