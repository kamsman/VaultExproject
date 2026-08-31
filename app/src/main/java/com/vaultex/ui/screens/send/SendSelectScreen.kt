package com.vaultex.ui.screens.send

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
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
import com.vaultex.ui.theme.Surface as SurfaceColor
import com.vaultex.ui.theme.TextMuted
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary
import com.vaultex.ui.viewmodel.PortfolioViewModel
import com.vaultex.ui.components.BoutonRecherche
import com.vaultex.ui.components.ChampRecherche
import com.vaultex.ui.components.rememberEtatRecherche

private data class SendAsset(
    val symbol: String,
    val title: String,     // ex: USDT
    val name: String,      // ex: Tether (BEP20)
    val amount: String,    // ex: 150.00 USDT
    val fiat: String,      // ex: ≈ 138.75 €
    val chainKey: String,  // BTC/ETH/BNB/SOL/TRX
    val colorHex: String,
    val value: Double
)

private data class NetFilter(val key: String, val label: String, val color: Color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendSelectScreen(navController: NavController) {
    val portfolio: PortfolioViewModel = hiltViewModel()
    val state by portfolio.state.collectAsState()
    val currency by portfolio.currency.collectAsState()

    val etatRecherche = rememberEtatRecherche()
    val query = etatRecherche.texte
    var network by remember { mutableStateOf("ALL") }

    val filters = listOf(
        NetFilter("ALL", stringResource(R.string.send_select_all), AccentBlue),
        NetFilter("ETH", "Ethereum", com.vaultex.ui.theme.NetworkEth),
        NetFilter("BNB", "BNB Chain", com.vaultex.ui.theme.NetworkBnb),
        NetFilter("TRX", "Tron", com.vaultex.ui.theme.NetworkTrx),
        NetFilter("SOL", "Solana", com.vaultex.ui.theme.NetworkSol),
        NetFilter("BTC", "Bitcoin", com.vaultex.ui.theme.NetworkBtc)
    )

    val assets = remember(state.tokens, currency) {
        state.tokens.map { t ->
            val value = when (currency) { "EUR" -> t.valueEur; "XOF" -> t.valueXof; else -> t.valueUsd }
            SendAsset(
                symbol = t.symbol,
                title = sendCoinShort(t.symbol),
                name = sendCoinSubtitle(t.symbol, t.isCustom, t.blockchain),
                amount = t.amountFormatted,
                fiat = "≈ " + CurrencyFormat.format(value, currency),
                chainKey = sendChainKey(t.blockchain),
                colorHex = t.colorHex,
                value = value
            )
        }.sortedByDescending { it.value }
    }

    val filtered = assets.filter {
        (network == "ALL" || it.chainKey == network) &&
            (query.isBlank() || it.symbol.contains(query, true) || it.name.contains(query, true))
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.send_title), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back), tint = AccentBlue)
                    }
                },
                actions = {
                    if (!etatRecherche.ouverte) {
                        BoutonRecherche(etatRecherche, Modifier.padding(end = 8.dp))
                    }
                    IconButton(onClick = { navController.navigate(Routes.SCANNER) }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = AccentBlue)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgPrimary)
            )
        },
        containerColor = BgPrimary
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (etatRecherche.ouverte) {
                ChampRecherche(
                    etat = etatRecherche,
                    placeholder = stringResource(R.string.send_select_search),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            // Sélecteur de réseau
            Text(
                stringResource(R.string.send_select_network),
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 8.dp)
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                filters.forEach { f ->
                    NetworkFilterChip(f, selected = network == f.key) { network = f.key }
                }
            }

            // Bandeau réseau
            if (network != "ALL") {
                val label = filters.firstOrNull { it.key == network }?.label ?: network
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = com.vaultex.ui.theme.NetworkBnb.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        stringResource(R.string.send_select_showing, label),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        fontSize = 13.sp, color = TextPrimary
                    )
                }
            } else {
                Spacer(Modifier.height(8.dp))
            }

            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                items(filtered) { a ->
                    SendAssetRow(a) {
                        com.vaultex.core.session.TokenSelectionBuffer.set(a.symbol)
                        navController.navigate(Routes.SEND)
                    }
                    HorizontalDivider(color = SurfaceColor, thickness = 1.dp)
                }
                item {
                    // Ajouter un token (au lieu d'un « voir les 43 » fictif)
                    Row(
                        Modifier.fillMaxWidth().clickable { navController.navigate(Routes.ADD_TOKEN) }.padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "+ " + stringResource(R.string.add_token_button),
                            color = AccentBlue, fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                        )
                    }
                }
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.receive_no_asset),
                            color = TextSecondary, fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkFilterChip(f: NetFilter, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Box(
            Modifier.size(52.dp).clip(CircleShape)
                .background(if (f.key == "ALL") BgTertiary else f.color)
                .then(if (selected) Modifier.border(2.dp, AccentBlue, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            if (f.key == "ALL") {
                Text(stringResource(R.string.send_select_all), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            } else {
                coil.compose.AsyncImage(
                    model = CryptoIcon.url(networkTicker(f.key)),
                    contentDescription = f.label,
                    modifier = Modifier.size(52.dp).clip(CircleShape)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            f.label, fontSize = 11.sp,
            color = if (selected) AccentBlue else TextSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun SendAssetRow(a: SendAsset, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape)
                .background(runCatching { Color(android.graphics.Color.parseColor(a.colorHex)) }.getOrDefault(AccentBlue)),
            contentAlignment = Alignment.Center
        ) {
            Text(a.symbol.take(2), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            coil.compose.AsyncImage(
                model = CryptoIcon.url(a.symbol),
                contentDescription = a.symbol,
                modifier = Modifier.size(40.dp).clip(CircleShape)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
            Text(a.name, fontSize = 12.sp, color = TextSecondary)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(a.amount, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
            Text(a.fiat, fontSize = 11.sp, color = TextSecondary)
        }
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Default.ChevronRight, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
    }
}

private fun sendChainKey(b: Blockchain): String = when (b) {
    Blockchain.BITCOIN -> "BTC"
    Blockchain.ETHEREUM -> "ETH"
    Blockchain.BNB_CHAIN -> "BNB"
    Blockchain.SOLANA -> "SOL"
    Blockchain.TRON -> "TRX"
}

private fun networkTicker(key: String): String = when (key) {
    "ETH" -> "ETH"; "BNB" -> "BNB"; "TRX" -> "TRX"; "SOL" -> "SOL"; "BTC" -> "BTC"; else -> key
}

private fun sendCoinShort(symbol: String): String = when (symbol) {
    "USDT-ETH", "USDT-BNB" -> "USDT"
    else -> symbol
}

private fun sendCoinSubtitle(symbol: String, isCustom: Boolean, b: Blockchain): String = when {
    isCustom -> if (b == Blockchain.BNB_CHAIN) "BEP20" else "ERC20"
    symbol == "USDT" -> "Tether (TRC20)"
    symbol == "USDT-ETH" -> "Tether (ERC20)"
    symbol == "USDT-BNB" -> "Tether (BEP20)"
    symbol == "BTC" -> "Bitcoin"
    symbol == "ETH" -> "Ethereum"
    symbol == "BNB" -> "BNB (BEP20)"
    symbol == "SOL" -> "Solana"
    symbol == "TRX" -> "Tron"
    else -> b.displayName
}
