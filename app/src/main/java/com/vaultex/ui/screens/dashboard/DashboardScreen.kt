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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController

import com.vaultex.core.crypto.Blockchain
import com.vaultex.ui.components.BalanceDisplay
import com.vaultex.ui.components.TokenIcon
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.*

/* =========================
   VIEWMODEL
========================= */

class DashboardViewModel : ViewModel() {

    val totalBalance = "12,847.92"

    data class TokenItem(
        val symbol: String,
        val name: String,
        val blockchain: Blockchain,
        val balance: String,
        val valueUsd: String
    )

    val tokens = listOf(

        TokenItem(
            "BTC",
            "Bitcoin",
            Blockchain.BITCOIN,
            "0.124",
            "$8,234.50"
        ),

        TokenItem(
            "ETH",
            "Ethereum",
            Blockchain.ETHEREUM,
            "1.832",
            "$3,541.22"
        ),

        TokenItem(
            "USDT",
            "Tether",
            Blockchain.TRON,
            "750.00",
            "$750.00"
        ),

        TokenItem(
            "BNB",
            "BNB",
            Blockchain.BNB_CHAIN,
            "0.45",
            "$215.30"
        ),

        TokenItem(
            "SOL",
            "Solana",
            Blockchain.SOLANA,
            "0.65",
            "$106.90"
        )
    )
}

/* =========================
   SCREEN
========================= */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavHostController
) {

    val viewModel = remember {
        DashboardViewModel()
    }

    Scaffold(

        topBar = {

            TopAppBar(

                title = {
                    Text(
                        "VaultEx",
                        color = TextPrimary
                    )
                },

                actions = {

                    IconButton(
                        onClick = {
                            // TEMPORAIRE
                        }
                    ) {

                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            tint = TextPrimary
                        )
                    }
                },

                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BgPrimary
                )
            )
        },

        bottomBar = {
            BottomNavBar(navController)
        },

        containerColor = BgPrimary

    ) { padding ->

        LazyColumn(

            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),

            contentPadding = PaddingValues(16.dp)

        ) {

            item {

                BalanceDisplay(
                    amount = "$${viewModel.totalBalance}"
                )

                Spacer(
                    Modifier.height(20.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {

                    QuickAction(
                        label = "Envoyer",
                        icon = Icons.Default.CallMade,
                        modifier = Modifier.weight(1f)
                    ) {

                        // TEMPORAIRE
                    }

                    QuickAction(
                        label = "Recevoir",
                        icon = Icons.Default.CallReceived,
                        modifier = Modifier.weight(1f)
                    ) {

                        // TEMPORAIRE
                    }

                    QuickAction(
                        label = "Swap",
                        icon = Icons.Default.SwapHoriz,
                        modifier = Modifier.weight(1f)
                    ) {

                        // TEMPORAIRE
                    }

                    QuickAction(
                        label = "Acheter",
                        icon = Icons.Default.AddCircle,
                        modifier = Modifier.weight(1f)
                    ) {

                        // TEMPORAIRE
                    }
                }

                Spacer(
                    Modifier.height(24.dp)
                )

                Text(
                    "Vos actifs",
                    color = TextPrimary,
                    fontSize = 18.sp
                )

                Spacer(
                    Modifier.height(12.dp)
                )
            }

            items(viewModel.tokens) { token ->

                TokenRow(token) {

                    // TEMPORAIRE
                }

                Spacer(
                    Modifier.height(8.dp)
                )
            }
        }
    }
}

/* =========================
   QUICK ACTION
========================= */

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
            .clickable {
                onClick()
            }
            .padding(vertical = 14.dp),

        horizontalAlignment = Alignment.CenterHorizontally

    ) {

        Icon(
            icon,
            contentDescription = label,
            tint = AccentGold
        )

        Spacer(
            Modifier.height(6.dp)
        )

        Text(
            label,
            color = TextPrimary,
            fontSize = 11.sp
        )
    }
}

/* =========================
   TOKEN ROW
========================= */

@Composable
private fun TokenRow(
    token: DashboardViewModel.TokenItem,
    onClick: () -> Unit
) {

    Row(

        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .clickable {
                onClick()
            }
            .padding(14.dp),

        verticalAlignment = Alignment.CenterVertically

    ) {

        TokenIcon(
            token.symbol,
            token.blockchain,
            size = 42.dp
        )

        Spacer(
            Modifier.width(12.dp)
        )

        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                token.symbol,
                color = TextPrimary
            )

            Text(
                token.name,
                color = TextSecondary
            )
        }

        Column(
            horizontalAlignment = Alignment.End
        ) {

            Text(
                token.valueUsd,
                color = TextPrimary
            )

            Spacer(
                Modifier.height(2.dp)
            )

            Text(
                token.balance,
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

/* =========================
   BOTTOM NAVIGATION
========================= */

@Composable
private fun BottomNavBar(
    navController: NavHostController
) {

    NavigationBar(
        containerColor = Surface
    ) {

        NavigationBarItem(

            selected = true,

            onClick = {

                navController.navigate(
                    Routes.DASHBOARD
                )
            },

            icon = {

                Icon(
                    Icons.Default.Home,
                    contentDescription = "Home"
                )
            }
        )

        NavigationBarItem(

            selected = false,

            onClick = {

                navController.navigate(
                    Routes.MARKET
                )
            },

            icon = {

                Icon(
                    Icons.Default.TrendingUp,
                    contentDescription = "Market"
                )
            }
        )

        NavigationBarItem(

            selected = false,

            onClick = {

                // TEMPORAIRE
            },

            icon = {

                Icon(
                    Icons.Default.SwapHoriz,
                    contentDescription = "Swap"
                )
            }
        )

        NavigationBarItem(

            selected = false,

            onClick = {

                // TEMPORAIRE
            },

            icon = {

                Icon(
                    Icons.Default.History,
                    contentDescription = "History"
                )
            }
        )

        NavigationBarItem(

            selected = false,

            onClick = {

                navController.navigate(
                    Routes.SETTINGS
                )
            },

            icon = {

                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings"
                )
            }
        )
    }
}