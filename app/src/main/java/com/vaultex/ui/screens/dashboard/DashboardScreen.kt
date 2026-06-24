package com.vaultex.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import com.vaultex.R
import kotlinx.coroutines.launch
import com.vaultex.ui.components.VaultExBottomBar
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.*
import com.vaultex.ui.viewmodel.PortfolioViewModel
import com.vaultex.ui.viewmodel.TokenBalance
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun DashboardScreen(navController: NavHostController) {
    val viewModel: PortfolioViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val balanceHidden by viewModel.balanceHidden.collectAsState()
    val walletName by viewModel.walletName.collectAsState()
    val currency by viewModel.currency.collectAsState()
    val visibleAssets by viewModel.visibleAssets.collectAsState()
    val pendingSymbols by viewModel.pendingSymbols.collectAsState()

    // P5 : un deep link de paiement valide redirige vers l'écran d'envoi
    LaunchedEffect(Unit) {
        if (com.vaultex.core.session.DeepLinkBuffer.hasPending()) {
            navController.navigate(Routes.SEND)
        }
    }

    // Rafraîchissement auto des soldes au RETOUR sur le Dashboard (après un
    // envoi, ou retour de l'app au premier plan) — sans spinner.
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSilently()
                // Après un envoi : rafale courte (toutes les 5 s) pour afficher
                // le solde dès la confirmation, sans attendre le poll de 45 s.
                if (com.vaultex.core.session.BalanceRefreshSignal.consumePending()) {
                    scope.launch {
                        repeat(5) {
                            kotlinx.coroutines.delay(5_000)
                            viewModel.refreshSilently()
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Polling discret toutes les 45 s tant que le Dashboard est affiché
    // (pour voir une réception arriver sans action de l'utilisateur).
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(45_000)
            viewModel.refreshSilently()
        }
    }

    Scaffold(
        bottomBar = { VaultExBottomBar(navController) },
        containerColor = BgPrimary
    ) { padding ->
        // Pull-to-refresh (#6) : UNIQUEMENT déclenché par l'utilisateur (tirer
        // l'écran). L'indicateur ne s'affiche pas pour le chargement auto.
        var manualRefresh by remember { mutableStateOf(false) }
        LaunchedEffect(state.isLoading) {
            if (!state.isLoading) manualRefresh = false
        }
        val pullState = rememberPullRefreshState(
            refreshing = manualRefresh,
            onRefresh = { manualRefresh = true; viewModel.refresh() }
        )
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .pullRefresh(pullState)
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ─── En-tête « Bonjour 👋 / Mon Wallet » ───
            item {
                Column {
                    Text(stringResource(R.string.dashboard_greeting), fontSize = 14.sp, color = TextSecondary)
                    // Nom du wallet (affichage seul ; édition dans Paramètres)
                    Text(
                        walletName.ifEmpty { stringResource(R.string.my_wallet) },
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            // ─── Carte solde (dégradé bleu, USD + ≈ XOF) ───
            item {
                BalanceCard(
                    currency = currency,
                    usd = state.totalBalanceUsd,
                    eur = state.totalBalanceEur,
                    xof = state.totalBalanceXof,
                    changePercent = state.totalChangePercent,
                    // Barre de chargement UNIQUEMENT au tout premier chargement
                    // (aucune donnée en cache) ; sinon affichage direct + refresh
                    // silencieux en arrière-plan (comme Trust Wallet).
                    isLoading = state.isLoading && state.tokens.isEmpty(),
                    hidden = balanceHidden,
                    onToggleHidden = { viewModel.toggleBalanceVisibility() }
                )
                Spacer(Modifier.height(6.dp))
                com.vaultex.ui.components.LastUpdatedLabel(
                    lastUpdated = state.lastUpdated,
                    isFromCache = state.isFromCache,
                    modifier = Modifier.padding(start = 4.dp)
                )
                state.error?.let { err ->
                    Text(
                        err,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // ─── Donut de répartition (#10) : seulement si >= 2 actifs financés ───
            val funded = state.tokens.filter { it.valueUsd > 0.0 }
            if (funded.size >= 2) {
                item { PortfolioDonutCard(funded) }
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
                    ActionChip(stringResource(R.string.momo_label), Icons.Default.PhoneAndroid,
                        AccentOrange, Modifier.weight(1f)) { navController.navigate(Routes.MOBILE_MONEY) }
                }
            }

            // ─── Mes actifs ───
            item {
                SectionCard(
                    title = stringResource(R.string.my_assets),
                    linkLabel = stringResource(R.string.see_all),
                    onLinkClick = { navController.navigate(Routes.PORTFOLIO) }
                ) {
                    // Règle : une monnaie s'affiche si elle est ACTIVÉE (Gérer les
                    // actifs) OU si elle a un solde > 0 (on ne masque jamais de fonds).
                    // Les tokens personnalisés (ajoutés par contrat) sont toujours
                    // affichés tant qu'ils existent en base.
                    val visibleTokens = state.tokens
                        .filter { it.valueUsd > 0.0 || it.symbol in visibleAssets || it.isCustom }
                        .sortedByDescending { it.valueUsd }
                    if (visibleTokens.isEmpty() && !state.isLoading) {
                        Text(
                            stringResource(R.string.dashboard_no_assets),
                            color = TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                    visibleTokens.forEachIndexed { index, token ->
                        AssetRow(token, balanceHidden, currency, token.symbol in pendingSymbols) {
                            navController.navigate(Routes.tokenDetail(token.symbol))
                        }
                        if (index < visibleTokens.lastIndex) {
                            HorizontalDivider(color = SurfaceLight, thickness = 1.dp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "+ " + stringResource(R.string.manage_assets_action),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentBlue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigate(Routes.MANAGE_ASSETS) }
                            .padding(vertical = 8.dp)
                    )
                }
            }

            // ─── Récent ───
            item {
                SectionCard(
                    title = stringResource(R.string.recent_title),
                    linkLabel = stringResource(R.string.history_title),
                    onLinkClick = { navController.navigate(Routes.HISTORY) }
                ) {
                    Text(
                        stringResource(R.string.recent_empty),
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }
        }
        PullRefreshIndicator(
            refreshing = manualRefresh,
            state = pullState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
        }
    }
}

@Composable
private fun BalanceCard(
    currency: String,
    usd: Double,
    eur: Double,
    xof: Double,
    changePercent: Double,
    isLoading: Boolean,
    hidden: Boolean,
    onToggleHidden: () -> Unit
) {
    val primaryAmount = when (currency) { "EUR" -> eur; "XOF" -> xof; else -> usd }
    val primaryText = com.vaultex.core.util.CurrencyFormat.format(primaryAmount, currency)
    // Référence secondaire : USD si on n'est pas en USD, sinon FCFA.
    val secondaryText =
        if (currency == "USD") com.vaultex.core.util.CurrencyFormat.format(xof, "XOF")
        else com.vaultex.core.util.CurrencyFormat.format(usd, "USD")
    val masked = "••••••"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(AccentBlue, AccentBlueDark)))
            .padding(20.dp)
    ) {
        // Œil Show/Hide en haut à droite
        IconButton(
            onClick = onToggleHidden,
            modifier = Modifier.align(Alignment.TopEnd).size(28.dp)
        ) {
            Icon(
                if (hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = stringResource(if (hidden) R.string.balance_show else R.string.balance_hide),
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(20.dp)
            )
        }
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
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (hidden) masked else primaryText,
                    color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(6.dp))
                Text(currency, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (hidden) masked else "≈ $secondaryText",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp
            )
            Spacer(Modifier.height(8.dp))
            val positive = changePercent >= 0
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (positive) Color(0xFFB9F3DC) else Color(0xFFFBD1D1)
            ) {
                Text(
                    "%+.1f%%".format(changePercent),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    color = if (positive) Color(0xFF067A53) else Color(0xFFB42318),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (isLoading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f)
                )
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
private fun SectionCard(
    title: String,
    linkLabel: String,
    onLinkClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                Text(
                    "$linkLabel →",
                    color = AccentBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable(onClick = onLinkClick)
                )
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun AssetRow(token: TokenBalance, hidden: Boolean, currency: String, isPending: Boolean = false, onClick: () -> Unit) {
    val valueAmount = when (currency) {
        "EUR" -> token.valueEur
        "XOF" -> token.valueXof
        else -> token.valueUsd
    }
    val valueText = com.vaultex.core.util.CurrencyFormat.format(valueAmount, currency)
    val tokenColor = try {
        Color(android.graphics.Color.parseColor(token.colorHex))
    } catch (_: Exception) { AccentBlue }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(tokenColor),
            contentAlignment = Alignment.Center
        ) {
            // Lettres en repli ; le logo réel par-dessus s'il se charge.
            Text(
                token.symbol.take(2),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            coil.compose.AsyncImage(
                model = com.vaultex.ui.components.CryptoIcon.url(token.symbol),
                contentDescription = token.symbol,
                modifier = Modifier.size(40.dp).clip(CircleShape)
            )
            // Badge « ! » : transaction sortante en attente de confirmation.
            if (isPending) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(15.dp).clip(CircleShape)
                        .background(Color(0xFFF59E0B))
                        .border(1.5.dp, BgPrimary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        // Prix unitaire de marché. Repli sur valeur/quantité si le prix stocké
        // est 0 (ex. instantané en cache d'une ancienne version) → plus de « 00 ».
        val storedPrice = when (currency) {
            "EUR" -> token.priceEur
            "XOF" -> token.priceXof
            else -> token.priceUsd
        }
        val unitPrice = if (storedPrice > 0.0) storedPrice else {
            val qty = token.amountFormatted.substringBefore(" ").replace(" ", "").replace(",", ".").toDoubleOrNull() ?: 0.0
            if (qty > 0.0) valueAmount / qty else 0.0
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(token.symbol, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                // Badge réseau (#9) : montre clairement sur quel réseau est le coin
                Surface(shape = RoundedCornerShape(4.dp), color = tokenColor.copy(alpha = 0.12f)) {
                    Text(
                        networkLabel(token.symbol),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        fontSize = 9.sp,
                        color = tokenColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Text(
                stringResource(R.string.asset_price_fmt, com.vaultex.core.util.CurrencyFormat.formatPrice(unitPrice, currency)),
                fontSize = 11.sp, color = TextSecondary
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (hidden) "••••" else valueText,
                fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary
            )
            val changeColor = if (token.changePercent24h >= 0) AccentGreen else AccentRed
            Text("%+.1f%%".format(token.changePercent24h), fontSize = 12.sp, color = changeColor)
        }
    }
}

/** Réseau lisible d'un coin (#9) — clarifie sur quelle chaîne il circule. */
private fun networkLabel(symbol: String): String = when (symbol) {
    "USDT" -> "TRC20"
    "USDT-ETH" -> "ERC20"
    "USDT-BNB" -> "BEP20"
    "BTC" -> "Bitcoin"
    "ETH" -> "Ethereum"
    "BNB" -> "BNB Chain"
    "SOL" -> "Solana"
    "TRX" -> "Tron"
    else -> symbol.substringBefore("-")
}

private fun formatAssetPrice(value: Double): String =
    NumberFormat.getNumberInstance(Locale.FRANCE).apply {
        maximumFractionDigits = if (value < 1.0) 4 else 2
    }.format(value)

@Composable
private fun PortfolioDonutCard(tokens: List<TokenBalance>) {
    val total = tokens.sumOf { it.valueUsd }
    val fallbackColor = AccentBlue   // lu dans le contexte @Composable
    fun colorOf(hex: String): Color = try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) { fallbackColor }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.portfolio_repartition),
                fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.vaultex.ui.components.DonutChart(
                    slices = tokens.map { colorOf(it.colorHex) to it.valueUsd.toFloat() },
                    modifier = Modifier.size(120.dp)
                )
                Spacer(Modifier.width(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tokens.forEach { token ->
                        val pct = if (total > 0.0) token.valueUsd / total * 100 else 0.0
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                Modifier.size(10.dp).clip(CircleShape).background(colorOf(token.colorHex))
                            )
                            Text(
                                token.symbol,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                            Text(
                                "%.0f%%".format(pct),
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
