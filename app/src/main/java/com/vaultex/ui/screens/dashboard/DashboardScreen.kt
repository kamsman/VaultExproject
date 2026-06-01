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
import androidx.compose.runtime.*
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
import com.vaultex.ui.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavHostController) {

    val viewModel: DashboardViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VaultEx", color = TextPrimary) },
                actions = {
                    IconButton(onClick = { navController.navigate(Routes.NOTIFICATIONS) }) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgPrimary)
            )
        },
        bottomBar = { BottomNavBar(navController) },
        containerColor = BgPrimary
    ) { padding ->

        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentGold)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                BalanceDisplay(amount = "$${uiState.totalBalance}")
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
                    QuickAction("Historique", Icons.Default.History, Modifier.weight(1f)) {
                        navController.navigate(Routes.HISTORY)
                    }
                }

                Spacer(Modifier.height(24.dp))
                Text("Vos actifs", color = TextPrimary, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))
            }

            items(uiState.tokens) { token ->
                TokenRow(token) { navController.navigate(Routes.TOKEN_DETAIL) }
                Spacer(Modifier.height(8.dp))
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
private fun TokenRow(
    token: DashboardViewModel.TokenItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TokenIcon(token.symbol, token.blockchain, size = 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(token.symbol, color = TextPrimary)
            Text(token.name, color = TextSecondary)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(token.valueUsd, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(token.balance, color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun BottomNavBar(navController: NavHostController) {
    NavigationBar(containerColor = Surface) {
        NavigationBarItem(selected = true,
            onClick = { navController.navigate(Routes.DASHBOARD) },
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") })
        NavigationBarItem(selected = false,
            onClick = { navController.navigate(Routes.MARKET) },
            icon = { Icon(Icons.Default.TrendingUp, contentDescription = "Market") })
        NavigationBarItem(selected = false,
            onClick = { navController.navigate(Routes.SWAP) },
            icon = { Icon(Icons.Default.SwapHoriz, contentDescription = "Swap") })
        NavigationBarItem(selected = false,
            onClick = { navController.navigate(Routes.HISTORY) },
            icon = { Icon(Icons.Default.History, contentDescription = "History") })
        NavigationBarItem(selected = false,
            onClick = { navController.navigate(Routes.SETTINGS) },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") })
    }
}
