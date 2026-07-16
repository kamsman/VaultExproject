package com.vaultex.ui.screens.walletmanager

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vaultex.R
import com.vaultex.core.crypto.Blockchain
import com.vaultex.core.util.CurrencyFormat
import com.vaultex.ui.components.CryptoIcon
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.BgTertiary
import com.vaultex.ui.theme.BorderColor
import com.vaultex.ui.theme.Surface as SurfaceColor
import com.vaultex.ui.theme.SurfaceLight
import com.vaultex.ui.theme.TextMuted
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary
import com.vaultex.ui.viewmodel.PortfolioViewModel

/** Monnaies natives + USDT par réseau (toujours proposées). */
private val DEFAULTS = mapOf(
    "ETH" to listOf("ETH", "USDT-ETH"),
    "BNB" to listOf("BNB", "USDT-BNB"),
    "TRX" to listOf("TRX", "USDT"),
    "SOL" to listOf("SOL")
)
private val NAMES = mapOf(
    "ETH" to "Ethereum", "BNB" to "BNB Chain", "TRX" to "Tron", "SOL" to "Solana",
    "USDT" to "Tether", "USDT-ETH" to "Tether", "USDT-BNB" to "Tether"
)
private fun chainKey(b: Blockchain): String = when (b) {
    Blockchain.ETHEREUM -> "ETH"; Blockchain.BNB_CHAIN -> "BNB"
    Blockchain.SOLANA -> "SOL"; Blockchain.TRON -> "TRX"; Blockchain.BITCOIN -> "BTC"
}
private fun iconSymbol(sym: String) = if (sym.startsWith("USDT")) "USDT" else sym

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenManagerScreen(navController: NavController) {
    val viewModel: PortfolioViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val visible by viewModel.visibleAssets.collectAsState()
    val currency by viewModel.currency.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var customAddress by remember { mutableStateOf("") }
    val networks = listOf("ETH", "BNB", "TRX", "SOL")
    var tab by remember { mutableStateOf("ETH") }

    // Vrais soldes/valeurs par symbole (issus du portefeuille).
    val tokenBySym = state.tokens.associateBy { it.symbol }
    fun valueOf(sym: String): Double {
        val t = tokenBySym[sym] ?: return 0.0
        return when (currency) { "EUR" -> t.valueEur; "XOF" -> t.valueXof; else -> t.valueUsd }
    }

    // Symboles du réseau sélectionné : natifs + USDT + tokens personnalisés ajoutés.
    val customSyms = state.tokens.filter { it.isCustom && chainKey(it.blockchain) == tab }.map { it.symbol }
    val symbols = (DEFAULTS[tab].orEmpty() + customSyms).distinct().filter { sym ->
        searchQuery.isBlank() ||
            sym.contains(searchQuery, true) ||
            (tokenBySym[sym]?.name ?: NAMES[sym] ?: "").contains(searchQuery, true)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.token_mgr_title), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back), tint = AccentBlue)
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Routes.ADD_TOKEN) }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.token_mgr_add_custom), tint = AccentBlue)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgPrimary)
            )
        },
        containerColor = BgPrimary
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ─── Onglets réseau ───
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SurfaceColor).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    networks.forEach { net ->
                        val sel = net == tab
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                                .background(if (sel) AccentBlue else Color.Transparent)
                                .clickable { tab = net }.padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(net, fontSize = 13.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                color = if (sel) Color.White else TextSecondary)
                        }
                    }
                }
            }

            // ─── Liste des tokens (vrais soldes + bascule persistée) ───
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                    elevation = CardDefaults.cardElevation(0.dp), modifier = Modifier.fillMaxWidth()) {
                    Column {
                        symbols.forEachIndexed { i, sym ->
                            val held = valueOf(sym) > 0.0
                            TokenRow(
                                symbol = sym,
                                name = tokenBySym[sym]?.name ?: NAMES[sym] ?: sym,
                                amount = tokenBySym[sym]?.amountFormatted ?: "0",
                                value = "≈ " + CurrencyFormat.format(valueOf(sym), currency),
                                held = held,
                                checked = held || sym in visible,
                                contractAddress = tokenBySym[sym]?.contractAddress,
                                chainTicker = tokenBySym[sym]?.blockchain?.ticker,
                                onToggle = { viewModel.toggleAssetVisible(sym) }
                            )
                            if (i < symbols.lastIndex) HorizontalDivider(color = SurfaceLight, thickness = 1.dp)
                        }
                        if (symbols.isEmpty()) {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.dashboard_no_assets), color = TextSecondary, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // ─── Ajouter un token personnalisé ───
            item {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                    elevation = CardDefaults.cardElevation(0.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.token_add_custom_title), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                        Spacer(Modifier.height(12.dp))
                        TextField(
                            value = customAddress,
                            onValueChange = { customAddress = it },
                            placeholder = { Text(stringResource(R.string.token_contract_placeholder), color = TextMuted, fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Description, null, tint = TextMuted, modifier = Modifier.size(20.dp)) },
                            trailingIcon = {
                                IconButton(onClick = { navController.navigate(Routes.SCANNER) }) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = stringResource(R.string.scan_qr), tint = AccentBlue)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = BgTertiary, unfocusedContainerColor = BgTertiary,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = AccentBlue
                            )
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { searchQuery = customAddress.trim() },
                                modifier = Modifier.weight(1f).height(46.dp),
                                shape = RoundedCornerShape(23.dp),
                                border = BorderStroke(1.5.dp, AccentBlue),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue)
                            ) {
                                Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.token_search_btn), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Button(
                                onClick = { navController.navigate(Routes.ADD_TOKEN) },
                                modifier = Modifier.weight(1f).height(46.dp),
                                shape = RoundedCornerShape(23.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = Color.White)
                            ) {
                                Text(stringResource(R.string.add), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                        Text(
                            stringResource(R.string.add_token_paste_hint),
                            fontSize = 11.sp, color = TextMuted, modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TokenRow(
    symbol: String, name: String, amount: String, value: String,
    held: Boolean, checked: Boolean,
    contractAddress: String? = null, chainTicker: String? = null,
    onToggle: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(BgTertiary), contentAlignment = Alignment.Center) {
            Text(symbol.take(2).uppercase(), color = TextSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            coil.compose.AsyncImage(
                model = CryptoIcon.urlFor(iconSymbol(symbol), contractAddress, chainTicker),
                contentDescription = symbol,
                modifier = Modifier.size(40.dp).clip(CircleShape)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary, maxLines = 1)
            Text(if (symbol.startsWith("USDT")) "USDT" else symbol, fontSize = 12.sp, color = TextSecondary)
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 10.dp)) {
            Text(amount, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(value, fontSize = 11.sp, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            enabled = !held,   // détenu → toujours visible (verrouillé sur ON)
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = AccentBlue,
                uncheckedThumbColor = Color.White, uncheckedTrackColor = BgTertiary,
                uncheckedBorderColor = Color.Transparent,
                disabledCheckedTrackColor = AccentBlue.copy(alpha = 0.6f)
            )
        )
    }
}
