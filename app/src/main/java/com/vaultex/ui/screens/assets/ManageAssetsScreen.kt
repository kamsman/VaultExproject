package com.vaultex.ui.screens.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vaultex.R
import com.vaultex.core.session.AssetVisibilityController
import com.vaultex.ui.components.CryptoIcon
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.Surface as SurfaceColor
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary
import com.vaultex.ui.viewmodel.PortfolioViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageAssetsScreen(navController: NavController) {
    val viewModel: PortfolioViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val visible by viewModel.visibleAssets.collectAsState()

    // Soldes par symbole (pour savoir ce qui est détenu).
    val balanceBySymbol = state.tokens.associate { it.symbol to it.valueUsd }
    val amountBySymbol = state.tokens.associate { it.symbol to it.amountFormatted }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.manage_assets_title), fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back), tint = AccentBlue)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgPrimary)
            )
        },
        containerColor = BgPrimary
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.manage_assets_hint),
                    fontSize = 13.sp, color = TextSecondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(AssetVisibilityController.SUPPORTED) { symbol ->
                val held = (balanceBySymbol[symbol] ?: 0.0) > 0.0
                AssetToggleRow(
                    symbol = symbol,
                    amount = amountBySymbol[symbol] ?: "",
                    held = held,
                    checked = held || symbol in visible,
                    onToggle = { viewModel.toggleAssetVisible(symbol) }
                )
            }
        }
    }
}

@Composable
private fun AssetToggleRow(
    symbol: String,
    amount: String,
    held: Boolean,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Surface(shape = RoundedCornerShape(14.dp), color = SurfaceColor) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(AccentBlue), contentAlignment = Alignment.Center) {
                Text(symbol.take(2), color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                coil.compose.AsyncImage(
                    model = CryptoIcon.url(symbol),
                    contentDescription = symbol,
                    modifier = Modifier.size(38.dp).clip(CircleShape)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(symbol, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                Text(networkLabel(symbol), fontSize = 11.sp, color = TextSecondary)
                if (held && amount.isNotEmpty()) {
                    Text(amount, fontSize = 11.sp, color = TextSecondary)
                }
            }
            // Détenu → toujours visible, interrupteur verrouillé sur ON.
            Switch(
                checked = checked,
                enabled = !held,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
            )
        }
    }
}

private fun networkLabel(symbol: String): String = when (symbol) {
    "USDT" -> "Tron · TRC20"
    "USDT-ETH" -> "Ethereum · ERC20"
    "USDT-BNB" -> "BNB Chain · BEP20"
    "BTC" -> "Bitcoin"
    "ETH" -> "Ethereum"
    "BNB" -> "BNB Chain"
    "SOL" -> "Solana"
    "TRX" -> "Tron"
    else -> symbol
}
