package com.vaultex.ui.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vaultex.R
import com.vaultex.data.local.entity.PriceAlertEntity
import com.vaultex.ui.theme.VaultExColors
import com.vaultex.ui.viewmodel.AlertsViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(navController: NavController) {
    val viewModel: AlertsViewModel = hiltViewModel()
    val alerts by viewModel.alerts.collectAsState()
    val currentPrices by viewModel.currentPricesXof.collectAsState()
    val movesEnabled by viewModel.movesEnabled.collectAsState()
    val moveThreshold by viewModel.moveThreshold.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.alerts_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.alerts_add), tint = VaultExColors.BluePrimary)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = VaultExColors.Background)
            )
        },
        containerColor = VaultExColors.Background
    ) { padding ->
        // Liste unique : les alertes AUTOMATIQUES restent visibles même
        // lorsqu'aucune alerte de cible n'a été créée — sinon l'utilisateur
        // croirait n'avoir aucune surveillance active alors qu'il en a une.
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (currentPrices.isNotEmpty()) {
                item { LivePricesBanner(currentPrices) }
            }
            item {
                AutoMovesCard(
                    enabled = movesEnabled,
                    threshold = moveThreshold,
                    onToggle = viewModel::setMovesEnabled,
                    onThresholdChange = viewModel::setMoveThreshold
                )
            }
            if (alerts.isEmpty()) {
                item { EmptyAlertsBlock(onCreate = { showAddDialog = true }) }
            } else {
                items(alerts, key = { it.id }) { alert ->
                    AlertCard(
                        alert = alert,
                        currentPriceXof = currentPrices[alert.tokenSymbol],
                        onToggle = { viewModel.toggleAlert(alert.id, it) },
                        onDelete = { viewModel.deleteAlert(alert.id) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddAlertDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { token, condition, target ->
                viewModel.createAlert(token, condition, target)
                showAddDialog = false
            }
        )
    }
}

/**
 * Alertes AUTOMATIQUES de variation — activées par défaut, sans rien à créer.
 * Présentées en tête de l'écran : c'est la surveillance dont l'utilisateur
 * bénéficie déjà, les alertes de cible en dessous n'étant qu'un complément.
 */
@Composable
private fun AutoMovesCard(
    enabled: Boolean,
    threshold: Int,
    onToggle: (Boolean) -> Unit,
    onThresholdChange: (Int) -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = VaultExColors.CardBackground)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.TrendingUp,
                    contentDescription = null,
                    tint = VaultExColors.BluePrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.price_moves_section),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = VaultExColors.TextPrimary
                    )
                    Text(
                        stringResource(R.string.price_moves_toggle),
                        fontSize = 12.sp,
                        color = VaultExColors.TextSecondary
                    )
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(
                    if (enabled) R.string.price_moves_desc else R.string.price_moves_off_hint
                ),
                fontSize = 12.sp,
                color = VaultExColors.TextSecondary
            )
            // Le seuil n'a de sens que si les alertes sont actives.
            if (enabled) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.price_moves_threshold),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VaultExColors.TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.vaultex.core.session.PriceMoveSettings.THRESHOLD_CHOICES.forEach { percent ->
                        val selected = percent == threshold
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) VaultExColors.BluePrimary
                                    else VaultExColors.Background
                                )
                                .clickable { onThresholdChange(percent) }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                stringResource(R.string.price_moves_threshold_value, percent),
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) androidx.compose.ui.graphics.Color.White
                                        else VaultExColors.TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Message affiché quand aucune alerte de CIBLE n'a encore été créée. */
@Composable
private fun EmptyAlertsBlock(onCreate: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Notifications,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = VaultExColors.Border
        )
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.alerts_empty), color = VaultExColors.TextSecondary)
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onCreate,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VaultExColors.BluePrimary)
        ) { Text(stringResource(R.string.alerts_create)) }
    }
}

private fun formatXof(value: Double): String =
    NumberFormat.getNumberInstance(Locale.FRANCE).format(value.toLong()) + " FCFA"

private fun tokenColor(symbol: String) = when (symbol) {
    "BTC" -> VaultExColors.BitcoinOrange
    "ETH" -> VaultExColors.EthereumBlue
    "BNB" -> VaultExColors.BnbYellow
    "SOL" -> VaultExColors.SolanaGreen
    "TRX" -> VaultExColors.TronRed
    else -> androidx.compose.ui.graphics.Color(0xFF1A6FE8)
}

@Composable
private fun LivePricesBanner(prices: Map<String, Double>) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = VaultExColors.CardBackground)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            prices.entries.take(3).forEach { (symbol, price) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(
                        Modifier.size(26.dp).clip(androidx.compose.foundation.shape.CircleShape).background(tokenColor(symbol)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(symbol.take(2), color = androidx.compose.ui.graphics.Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(formatXof(price), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = VaultExColors.TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun AlertCard(
    alert: PriceAlertEntity,
    currentPriceXof: Double?,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val targetFormatted = alert.targetPrice.toDoubleOrNull()?.let(::formatXof) ?: "${alert.targetPrice} FCFA"
    val color = tokenColor(alert.tokenSymbol)
    val isAbove = alert.condition.contains("dessus", ignoreCase = true)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = VaultExColors.CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape).background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(alert.tokenSymbol.take(2), color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(alert.tokenSymbol, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isAbove) VaultExColors.Success.copy(alpha = 0.12f) else VaultExColors.Error.copy(alpha = 0.12f)
                ) {
                    Text(
                        alert.condition.replaceFirstChar { it.uppercase() },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 11.sp,
                        color = if (isAbove) VaultExColors.Success else VaultExColors.Error
                    )
                }
                Text(stringResource(R.string.alerts_target_fmt, targetFormatted), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = VaultExColors.TextPrimary)
                currentPriceXof?.let {
                    Text(stringResource(R.string.alerts_current_price, formatXof(it)), fontSize = 11.sp, color = VaultExColors.TextSecondary)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Switch(
                    checked = alert.isActive, onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(checkedTrackColor = VaultExColors.BluePrimary)
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.alerts_delete), tint = VaultExColors.Error, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** Formulaire « Nouvelle alerte » pleine page (maquette : token, condition, cible, résumé). */
@Composable
private fun AddAlertDialog(onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    var token by remember { mutableStateOf("BTC") }
    var condition by remember { mutableStateOf("au-dessus de") }
    var target by remember { mutableStateOf("") }
    val mainTokens = listOf("BTC", "ETH", "BNB", "SOL")
    val otherTokens = listOf("TRX", "USDT")
    val isAbove = condition.startsWith("au-dessus")
    val fmt = NumberFormat.getNumberInstance(Locale.FRANCE)

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(color = VaultExColors.Background, modifier = Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // En-tête « Nouvelle alerte »
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape)
                            .background(VaultExColors.BluePrimary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Notifications, null, tint = VaultExColors.BluePrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(stringResource(R.string.alerts_new), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = VaultExColors.TextPrimary)
                        Text(stringResource(R.string.alerts_new_subtitle), fontSize = 12.sp, color = VaultExColors.TextSecondary)
                    }
                }

                // 1. Token
                Text("1. Token", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = VaultExColors.TextPrimary)
                Row(
                    Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    (mainTokens + otherTokens).forEach { t ->
                        val sel = token == t
                        Surface(
                            onClick = { token = t },
                            shape = RoundedCornerShape(12.dp),
                            color = if (sel) VaultExColors.BluePrimary.copy(alpha = 0.10f) else VaultExColors.CardBackground,
                            border = androidx.compose.foundation.BorderStroke(if (sel) 1.5.dp else 1.dp, if (sel) VaultExColors.BluePrimary else VaultExColors.Border)
                        ) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                coil.compose.AsyncImage(
                                    model = com.vaultex.ui.components.CryptoIcon.url(t),
                                    contentDescription = t,
                                    modifier = Modifier.size(20.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(t, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = VaultExColors.TextPrimary)
                            }
                        }
                    }
                }

                // 2. Condition (deux cartes radio)
                Text("2. Condition", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = VaultExColors.TextPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ConditionCard(
                        selected = isAbove,
                        title = stringResource(R.string.alerts_above),
                        desc = stringResource(R.string.alerts_above_desc),
                        up = true, modifier = Modifier.weight(1f)
                    ) { condition = "au-dessus de" }
                    ConditionCard(
                        selected = !isAbove,
                        title = stringResource(R.string.alerts_below),
                        desc = stringResource(R.string.alerts_below_desc),
                        up = false, modifier = Modifier.weight(1f)
                    ) { condition = "en-dessous de" }
                }

                // 3. Prix cible (FCFA)
                Text(stringResource(R.string.alerts_step_target), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = VaultExColors.TextPrimary)
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it.replace(',', '.') },
                    prefix = { Text("FCFA  ", fontSize = 14.sp, color = VaultExColors.TextSecondary) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                target.toDoubleOrNull()?.let {
                    Text("≈ ${fmt.format(it.toLong())} FCFA", fontSize = 12.sp, color = VaultExColors.TextSecondary)
                }

                // 5. Résumé
                Text(stringResource(R.string.alerts_step_summary), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = VaultExColors.TextPrimary)
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = VaultExColors.CardBackground)) {
                    Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        coil.compose.AsyncImage(
                            model = com.vaultex.ui.components.CryptoIcon.url(token),
                            contentDescription = token,
                            modifier = Modifier.size(30.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(token, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = VaultExColors.TextPrimary)
                            Text(
                                condition.replaceFirstChar { it.uppercase() } + " " +
                                    (target.toDoubleOrNull()?.let { fmt.format(it.toLong()) } ?: "—") + " FCFA",
                                fontSize = 12.sp, color = VaultExColors.TextSecondary
                            )
                        }
                        Text(
                            stringResource(R.string.alerts_active_until),
                            fontSize = 11.sp, color = VaultExColors.Success, fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Annuler / Créer l'alerte
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, VaultExColors.BluePrimary)
                    ) { Text(stringResource(R.string.cancel), color = VaultExColors.BluePrimary, fontWeight = FontWeight.SemiBold) }
                    Button(
                        onClick = { onConfirm(token, condition, target) },
                        enabled = target.toDoubleOrNull() != null,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VaultExColors.BluePrimary)
                    ) {
                        Icon(Icons.Default.Notifications, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.alerts_create_cta), fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

/** Carte de condition (Au-dessus ↗ / En-dessous ↘) avec radio, comme la maquette. */
@Composable
private fun ConditionCard(
    selected: Boolean, title: String, desc: String, up: Boolean,
    modifier: Modifier, onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) VaultExColors.BluePrimary.copy(alpha = 0.08f) else VaultExColors.CardBackground,
        border = androidx.compose.foundation.BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) VaultExColors.BluePrimary else VaultExColors.Border),
        modifier = modifier
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = onClick, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (up) "↗" else "↘", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = if (up) VaultExColors.Success else VaultExColors.Error
                )
            }
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = VaultExColors.TextPrimary)
            Text(desc, fontSize = 11.sp, color = VaultExColors.TextSecondary, lineHeight = 14.sp)
        }
    }
}
