package com.vaultex.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vaultex.ui.components.BalanceDisplay
import com.vaultex.ui.components.TokenIcon
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.*
import com.vaultex.ui.viewmodel.PortfolioViewModel
import com.vaultex.ui.viewmodel.TokenBalance
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavHostController) {
    val viewModel: PortfolioViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VaultEx", color = TextPrimary) },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgPrimary)
            )
        },
        bottomBar = { BottomNavBar(navController) },
        containerColor = BgPrimary
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                val xofFormatted = NumberFormat.getNumberInstance(Locale.FRANCE)
                    .format(state.totalBalanceXof.toLong()) + " FCFA"

                BalanceDisplay(amount = xofFormatted, currency = "")

                if (state.isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        color = AccentGold
                    )
                }

                state.error?.let { err ->
                    Text(
                        err,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction("Envoyer", Icons.Default.CallMade, Modifier.weight(1f)) {
                        navController.navigate(Routes.SEND)
                    }
                    QuickAction("Recevoir", Icons.Default.CallReceived, Modifier.weight(1f)) {
                        navController.navigate(Routes.RECEIVE)
                    }
                    QuickAction("Swap", Icons.Default.SwapHoriz, Modifier.weight(1f)) {
                        navController.navigate(Routes.SWAP)
                    }
                    QuickAction("Acheter", Icons.Default.AddCircle, Modifier.weight(1f)) {
                        navController.navigate(Routes.MOBILE_MONEY)
                    }
                }

                Spacer(Modifier.height(24.dp))
                Text("Vos actifs", color = TextPrimary, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))
            }

            val visibleTokens = state.tokens.filter { it.valueXof > 0.0 || !state.isLoading }
            items(visibleTokens) { token ->
                TokenRow(token)
                Spacer(Modifier.height(8.dp))
            }

            if (!state.isLoading && state.tokens.isEmpty() && state.error == null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Aucun actif trouvé", color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAction(
    label: String,
    icon: ImageVector,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = AccentGold)
        Spacer(Modifier.height(6.dp))
        Text(label, color = TextPrimary, fontSize = 11.sp)
    }
}

@Composable
private fun TokenRow(token: TokenBalance) {
    val xofFormatted = NumberFormat.getNumberInstance(Locale.FRANCE)
        .format(token.valueXof.toLong()) + " FCFA"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TokenIcon(token.symbol, token.blockchain, size = 42.dp)

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(token.symbol, color = TextPrimary)
            Text(token.name, color = TextSecondary, fontSize = 12.sp)
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(xofFormatted, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(token.amountFormatted, color = TextSecondary, fontSize = 12.sp)
            val changeColor = if (token.changePercent24h >= 0) AccentGreen else MaterialTheme.colorScheme.error
            Text(
                "%.2f%%".format(token.changePercent24h),
                color = changeColor,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun BottomNavBar(navController: NavHostController) {
    NavigationBar(containerColor = Surface) {
        NavigationBarItem(
            selected = true,
            onClick = { navController.navigate(Routes.DASHBOARD) },
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") }
        )
        NavigationBarItem(
            selected = false,
            onClick = { navController.navigate(Routes.MARKET) },
            icon = { Icon(Icons.Default.TrendingUp, contentDescription = "Market") }
        )
        NavigationBarItem(
            selected = false,
            onClick = { navController.navigate(Routes.SWAP) },
            icon = { Icon(Icons.Default.SwapHoriz, contentDescription = "Swap") }
        )
        NavigationBarItem(
            selected = false,
            onClick = { navController.navigate(Routes.HISTORY) },
            icon = { Icon(Icons.Default.History, contentDescription = "History") }
        )
        NavigationBarItem(
            selected = false,
            onClick = { navController.navigate(Routes.SETTINGS) },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") }
        )
    }
}
