package com.vaultex.ui.screens.walletmanager

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.navigation.NavController
import com.vaultex.R
import com.vaultex.ui.theme.VaultExColors

@Composable
fun TokenManagerScreen(navController: NavController) {
    data class TokenItem(val symbol: String, val name: String, val network: String, var isVisible: Boolean)
    var searchQuery by remember { mutableStateOf("") }
    val tokens = remember {
        mutableStateListOf(
            TokenItem("BTC", "Bitcoin", "Bitcoin", true),
            TokenItem("ETH", "Ethereum", "Ethereum", true),
            TokenItem("BNB", "BNB", "BSC", true),
            TokenItem("USDT", "Tether", "Ethereum", true),
            TokenItem("USDC", "USD Coin", "Ethereum", true),
            TokenItem("TRX", "Tron", "Tron", true),
            TokenItem("SOL", "Solana", "Solana", true),
            TokenItem("BUSD", "Binance USD", "BSC", false),
        )
    }
    val filtered = tokens.filter { it.symbol.contains(searchQuery, true) || it.name.contains(searchQuery, true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.token_manager), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultExColors.Background)
            )
        },
        containerColor = VaultExColors.Background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.token_mgr_search)) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered) { token ->
                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = VaultExColors.CardBackground)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).clip(CircleShape).background(VaultExColors.BlueLight), contentAlignment = Alignment.Center) {
                                Text(token.symbol.take(1), color = VaultExColors.BluePrimary, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(token.symbol, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("${token.name} · ${token.network}", fontSize = 12.sp, color = VaultExColors.TextSecondary)
                            }
                            Switch(
                                checked = token.isVisible,
                                onCheckedChange = { token.isVisible = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = VaultExColors.BluePrimary)
                            )
                        }
                    }
                }
            }
        }
    }
}
