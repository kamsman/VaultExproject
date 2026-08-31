package com.vaultex.ui.screens.receive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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

/** Un actif que l'on peut recevoir : symbole, libellé, réseau, solde, adresse. */
private data class ReceiveAsset(
    val symbol: String,
    val title: String,        // ex: "USDT (TRC20)"
    val network: String,      // ex: "Tron · TRC20"
    val amount: String,       // ex: "150.00 USDT"
    val fiat: String,         // ex: "≈ 138.75 €"
    val chainKey: String,     // BTC/ETH/BNB/SOL/TRX (clé d'adresse)
    val colorHex: String,
    val held: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveScreen(navController: NavController) {
    val portfolio: PortfolioViewModel = hiltViewModel()
    val state by portfolio.state.collectAsState()
    val currency by portfolio.currency.collectAsState()

    val etatRecherche = rememberEtatRecherche()
    val query = etatRecherche.texte

    val assets = remember(state.tokens, currency) {
        state.tokens.map { t ->
            val value = when (currency) { "EUR" -> t.valueEur; "XOF" -> t.valueXof; else -> t.valueUsd }
            ReceiveAsset(
                symbol = t.symbol,
                title = receiveCoinTitle(t.symbol, t.isCustom, t.blockchain),
                network = receiveNetworkLabel(t.symbol, t.isCustom, t.blockchain),
                amount = t.amountFormatted,
                fiat = "≈ " + CurrencyFormat.format(value, currency),
                chainKey = chainKeyOf(t.blockchain),
                colorHex = t.colorHex,
                held = value > 0.0
            )
        }.sortedByDescending { it.held }
    }

    val filtered = assets.filter {
        query.isBlank() || it.symbol.contains(query, true) || it.title.contains(query, true) ||
            it.network.contains(query, true)
    }
    val held = filtered.filter { it.held }
    val others = filtered.filter { !it.held }

    // Pré-sélection depuis la page d'une crypto : on consomme le buffer (pour
    // qu'il ne « fuite » pas vers l'écran Envoyer) et, dès que la liste est
    // chargée, on ouvre directement la réception de cet actif.
    var pendingSymbol by remember { mutableStateOf(com.vaultex.core.session.TokenSelectionBuffer.consume()) }
    LaunchedEffect(assets, pendingSymbol) {
        val sym = pendingSymbol ?: return@LaunchedEffect
        val match = assets.firstOrNull { it.symbol == sym }
        if (match != null) {
            pendingSymbol = null
            navController.navigate(Routes.receiveAsset(match.symbol, match.chainKey))
        } else if (assets.isNotEmpty()) {
            // Actif pas encore détenu (token du registre non ajouté au
            // portefeuille) : on ouvre quand même sa réception via SA chaîne —
            // l'adresse native/EVM reçoit le token sans ajout préalable.
            val asset = com.vaultex.ui.viewmodel.SwapViewModel.assetForSymbol(sym)
            pendingSymbol = null
            if (asset != null) navController.navigate(Routes.receiveAsset(asset.base, asset.chain))
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.receive_title), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back), tint = AccentBlue)
                    }
                },
                actions = {
                    if (!etatRecherche.ouverte) {
                        BoutonRecherche(etatRecherche, Modifier.padding(end = 8.dp))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgPrimary)
            )
        },
        containerColor = BgPrimary
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Le champ n'existe QUE pendant la recherche : le reste du temps,
            // la place revient à la liste des actifs.
            if (etatRecherche.ouverte) {
                ChampRecherche(
                    etat = etatRecherche,
                    placeholder = stringResource(R.string.receive_search_asset),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (held.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.receive_section_my_assets)) }
                    items(held) { AssetRow(it) { navController.navigate(Routes.receiveAsset(it.symbol, it.chainKey)) } }
                }
                if (others.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.receive_section_all_assets)) }
                    items(others) { AssetRow(it) { navController.navigate(Routes.receiveAsset(it.symbol, it.chainKey)) } }
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
private fun SectionHeader(label: String) {
    Text(
        label,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = TextSecondary,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
    )
}

@Composable
private fun AssetRow(asset: ReceiveAsset, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = SurfaceColor) {
        Row(
            Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(runCatching { Color(android.graphics.Color.parseColor(asset.colorHex)) }.getOrDefault(AccentBlue)),
                contentAlignment = Alignment.Center
            ) {
                Text(asset.symbol.take(2), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                coil.compose.AsyncImage(
                    model = CryptoIcon.url(asset.symbol),
                    contentDescription = asset.symbol,
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(asset.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                Text(asset.network, fontSize = 11.sp, color = TextSecondary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(asset.amount, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextPrimary)
                Text(asset.fiat, fontSize = 11.sp, color = TextSecondary)
            }
        }
    }
}

// ─── Helpers de libellé (partagés avec l'écran de détail) ───────────────

internal fun chainKeyOf(b: Blockchain): String = when (b) {
    Blockchain.BITCOIN -> "BTC"
    Blockchain.ETHEREUM -> "ETH"
    Blockchain.BNB_CHAIN -> "BNB"
    Blockchain.SOLANA -> "SOL"
    Blockchain.TRON -> "TRX"
}

internal fun receiveCoinTitle(symbol: String, isCustom: Boolean, b: Blockchain): String = when {
    isCustom -> "$symbol (${if (b == Blockchain.BNB_CHAIN) "BEP20" else "ERC20"})"
    symbol == "USDT" -> "USDT (TRC20)"
    symbol == "USDT-ETH" -> "USDT (ERC20)"
    symbol == "USDT-BNB" -> "USDT (BEP20)"
    else -> symbol
}

internal fun receiveNetworkLabel(symbol: String, isCustom: Boolean, b: Blockchain): String = when {
    isCustom -> if (b == Blockchain.BNB_CHAIN) "BNB Chain · BEP20" else "Ethereum · ERC20"
    symbol == "USDT" -> "Tron · TRC20"
    symbol == "USDT-ETH" -> "Ethereum · ERC20"
    symbol == "USDT-BNB" -> "BNB Chain · BEP20"
    symbol == "BTC" -> "Bitcoin"
    symbol == "ETH" -> "Ethereum"
    symbol == "BNB" -> "BNB Chain"
    symbol == "SOL" -> "Solana"
    symbol == "TRX" -> "Tron"
    else -> b.displayName
}
