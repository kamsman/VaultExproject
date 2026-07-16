package com.vaultex.ui.screens.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
            // ─── Tokens personnalisés (ajoutés par contrat) : RETRAIT, pas un
            // simple interrupteur — contrairement aux monnaies natives, ils
            // peuvent être entièrement retirés de ce wallet (même avec un
            // solde) : le token reste sur la blockchain, ré-ajoutable via son
            // contrat à tout moment.
            val customTokens = state.tokens.filter { it.isCustom }
            if (customTokens.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.manage_assets_custom_title),
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }
                items(customTokens, key = { it.contractAddress ?: it.symbol }) { token ->
                    var showConfirm by remember { mutableStateOf(false) }
                    Surface(shape = RoundedCornerShape(14.dp), color = SurfaceColor) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(38.dp).clip(CircleShape).background(AccentBlue), contentAlignment = Alignment.Center) {
                                Text(token.symbol.take(2), color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                coil.compose.AsyncImage(
                                    model = CryptoIcon.urlFor(token.symbol, token.contractAddress, token.blockchain.ticker),
                                    contentDescription = token.symbol,
                                    modifier = Modifier.size(38.dp).clip(CircleShape)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(token.symbol, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                                Text(
                                    if (token.blockchain.ticker == "BNB") "BNB Chain · BEP20" else "Ethereum · ERC20",
                                    fontSize = 11.sp, color = TextSecondary
                                )
                            }
                            IconButton(onClick = { showConfirm = true }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.manage_assets_remove),
                                    tint = com.vaultex.ui.theme.AccentRed
                                )
                            }
                        }
                    }
                    if (showConfirm) {
                        AlertDialog(
                            onDismissRequest = { showConfirm = false },
                            title = { Text(stringResource(R.string.manage_assets_remove_title, token.symbol)) },
                            text = { Text(stringResource(R.string.manage_assets_remove_body, token.symbol)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    showConfirm = false
                                    token.contractAddress?.let { addr ->
                                        viewModel.removeCustomToken(addr, token.blockchain.ticker)
                                    }
                                }) { Text(stringResource(R.string.manage_assets_remove), color = com.vaultex.ui.theme.AccentRed) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showConfirm = false }) { Text(stringResource(R.string.cancel)) }
                            }
                        )
                    }
                }
            }
            item {
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SurfaceColor,
                    onClick = { navController.navigate(com.vaultex.ui.navigation.Routes.ADD_TOKEN) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(38.dp).clip(CircleShape).background(AccentBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.add_token_button),
                            fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary
                        )
                    }
                }
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
