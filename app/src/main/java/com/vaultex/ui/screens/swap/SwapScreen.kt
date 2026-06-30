package com.vaultex.ui.screens.swap

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vaultex.R
import androidx.compose.ui.draw.clip
import com.vaultex.ui.components.CryptoIcon
import com.vaultex.ui.components.VaultExBottomBar
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.AccentRed
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.BgTertiary
import com.vaultex.ui.theme.BorderColor
import com.vaultex.ui.theme.NetworkBnb
import com.vaultex.ui.theme.NetworkBtc
import com.vaultex.ui.theme.NetworkEth
import com.vaultex.ui.theme.NetworkSol
import com.vaultex.ui.theme.NetworkTrx
import com.vaultex.ui.theme.Surface as SurfaceColor
import com.vaultex.ui.theme.SurfaceLight
import com.vaultex.ui.theme.TextMuted
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary
import com.vaultex.ui.theme.VaultExColors
import com.vaultex.ui.viewmodel.SwapViewModel

/** Chaîne d'envoi VaultEx pour déposer le token source (notre USDT = TRC20). */
private fun swapSendChain(fromToken: String): String = when (fromToken.uppercase()) {
    "USDT" -> "USDT"
    else -> fromToken.uppercase()
}

private fun tokenColor(token: String): Color = when (token.uppercase()) {
    "BTC" -> NetworkBtc
    "ETH" -> NetworkEth
    "BNB" -> NetworkBnb
    "SOL" -> NetworkSol
    "TRX", "USDT" -> NetworkTrx
    else -> Color(0xFF1A6FE8)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwapScreen(navController: NavHostController) {
    val viewModel: SwapViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current as androidx.fragment.app.FragmentActivity
    val biometricHelper = remember { com.vaultex.core.security.BiometricHelper(context) }
    var showConfirm by remember { mutableStateOf(false) }

    val tokens = listOf("ETH", "BNB", "USDT", "BTC", "SOL", "TRX")

    // Pré-sélection « De » depuis la page d'une crypto (#4).
    LaunchedEffect(Unit) {
        com.vaultex.core.session.TokenSelectionBuffer.consume()?.let { sym ->
            val t = if (sym == "USDT-ETH" || sym == "USDT-BNB") "USDT" else sym
            if (t in tokens) viewModel.setFromToken(t)
        }
    }

    // 1) CONFIRMATION (récap) → biométrie → l'app crée l'ordre ET dépose
    //    automatiquement (façon Trust Wallet), sans détour par l'écran Envoyer.
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            icon = { Icon(Icons.Default.SwapHoriz, null, tint = AccentBlue) },
            title = { Text(stringResource(R.string.swap_exchange)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${state.fromAmount} ${state.fromToken}  →  ≈ ${state.toAmount.ifEmpty { "—" }} ${state.toToken}",
                        fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary
                    )
                    Text("${stringResource(R.string.swap_via)} ChangeNOW", fontSize = 13.sp, color = TextSecondary)
                    Text(stringResource(R.string.swap_confirm_hint), fontSize = 11.sp, color = TextSecondary)
                }
            },
            confirmButton = {
                Button(onClick = {
                    showConfirm = false
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val bio = biometricHelper.checkAvailability()
                    if (bio == com.vaultex.core.security.BiometricHelper.BiometricStatus.AVAILABLE ||
                        biometricHelper.canUseDeviceCredential()
                    ) {
                        biometricHelper.authenticateStrongOrCredential(
                            title = context.getString(R.string.swap_exchange),
                            subtitle = "${state.fromAmount} ${state.fromToken} → ${state.toToken}",
                            onSuccess = { viewModel.executeSwap() },
                            onError = { _, _ -> }
                        )
                    } else viewModel.executeSwap()
                }) { Text(stringResource(R.string.swap_confirm_cta)) }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // 2) SUIVI du swap : dépôt auto + statut ChangeNOW en temps réel.
    if (state.swapInProgress) {
        val finished = state.swapStatus == "finished"
        val failed = state.swapStatus in listOf("failed", "refunded", "expired")
        LaunchedEffect(finished) { if (finished) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
        AlertDialog(
            onDismissRequest = { if (finished || failed) viewModel.resetSwap() },
            icon = {
                if (finished) Icon(Icons.Default.CheckCircle, null, tint = VaultExColors.Success)
                else Icon(Icons.Default.SwapHoriz, null, tint = AccentBlue)
            },
            title = { Text(stringResource(if (finished) R.string.swap_done_title else R.string.swap_progress_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!finished && !failed) CircularProgressIndicator(color = AccentBlue, modifier = Modifier.size(30.dp), strokeWidth = 3.dp)
                    Text(swapStatusLabel(state.swapStatus), fontSize = 14.sp, color = TextPrimary)
                    if (state.toAmount.isNotEmpty())
                        Text("≈ ${state.toAmount} ${state.toToken}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                    state.swapId?.let { Text(stringResource(R.string.swap_id_label, it.take(16)), fontSize = 11.sp, color = TextSecondary) }
                }
            },
            confirmButton = {
                if (finished || failed) Button(onClick = { viewModel.resetSwap() }) { Text(stringResource(R.string.send_success_done)) }
                else TextButton(onClick = { viewModel.resetSwap() }) { Text(stringResource(R.string.close)) }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.swap_exchange),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = AccentBlue
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgPrimary)
            )
        },
        bottomBar = {
            Column {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showConfirm = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                        .height(54.dp),
                    enabled = state.fromAmount.isNotEmpty() && state.fromToken != state.toToken && !state.isLoading,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = VaultExColors.TextOnPrimary, strokeWidth = 2.dp)
                    } else {
                        Text(
                            stringResource(R.string.continue_btn),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = VaultExColors.TextOnPrimary
                        )
                    }
                }
                VaultExBottomBar(navController)
            }
        },
        containerColor = BgPrimary
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val fromAmt = state.fromAmount.toDoubleOrNull() ?: 0.0
            val toAmt = state.toAmount.toDoubleOrNull() ?: 0.0
            val fromFiat = if (state.fromPriceUsd > 0.0 && fromAmt > 0.0) "≈ $" + String.format("%,.2f", fromAmt * state.fromPriceUsd) else null
            val toFiat = if (state.toPriceUsd > 0.0 && toAmt > 0.0) "≈ $" + String.format("%,.2f", toAmt * state.toPriceUsd) else null
            val balTxt = if (state.fromBalance > 0.0)
                java.math.BigDecimal.valueOf(state.fromBalance).setScale(4, java.math.RoundingMode.DOWN).stripTrailingZeros().toPlainString()
            else "0"

            // ─── Vous envoyez ───
            SwapCoinCard(
                label = stringResource(R.string.swap_you_send_label),
                rightLabel = stringResource(R.string.balance_label, "$balTxt ${state.fromToken}"),
                token = state.fromToken, tokens = tokens, onTokenSelect = { viewModel.setFromToken(it) },
                amount = state.fromAmount, editable = true, onAmountChange = { viewModel.setFromAmount(it) },
                fiat = fromFiat, onMax = { viewModel.onMaxClicked() }, highlight = true
            )

            // Inversion
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                Surface(onClick = { viewModel.swapTokens() }, shape = CircleShape, color = SurfaceColor,
                    border = BorderStroke(1.dp, BorderColor), modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.SwapVert, stringResource(R.string.swap_invert_tokens), tint = AccentBlue, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // ─── Vous recevez ───
            SwapCoinCard(
                label = stringResource(R.string.swap_you_receive_label),
                rightLabel = null,
                token = state.toToken, tokens = tokens, onTokenSelect = { viewModel.setToToken(it) },
                amount = state.toAmount, editable = false, onAmountChange = {},
                fiat = toFiat, onMax = null, highlight = false
            )

            Spacer(Modifier.height(12.dp))

            // ─── Détails : Taux / Frais (inclus) / Délai estimé ───
            Surface(shape = RoundedCornerShape(16.dp), color = SurfaceColor, border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 14.dp)) {
                    val rate = if (fromAmt > 0.0 && toAmt > 0.0)
                        "1 ${state.fromToken} ≈ ${String.format("%.8f", toAmt / fromAmt).trimEnd('0').trimEnd('.')} ${state.toToken}" else "—"
                    SwapDetailRow(Icons.Default.ShowChart, stringResource(R.string.swap_rate), rate, chevron = true)
                    Divider(color = SurfaceLight, thickness = 1.dp)
                    val feeTxt = if (fromAmt > 0.0)
                        "${String.format("%.4f", fromAmt * com.vaultex.domain.usecase.SwapUseCase.VAULTEX_FEE_PERCENT / 100.0).trimEnd('0').trimEnd('.')} ${state.fromToken}" else "—"
                    SwapDetailRow(Icons.Default.Receipt, stringResource(R.string.swap_fee_included), feeTxt, valueColor = VaultExColors.Success)
                    Divider(color = SurfaceLight, thickness = 1.dp)
                    SwapDetailRow(Icons.Default.Schedule, stringResource(R.string.swap_eta), stringResource(R.string.swap_eta_value), valueColor = AccentBlue)
                }
            }

            Spacer(Modifier.height(12.dp))

            // ─── Fournisseur ───
            Surface(shape = RoundedCornerShape(16.dp), color = SurfaceColor, border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(BgTertiary), contentAlignment = Alignment.Center) {
                        Text("N", fontWeight = FontWeight.Bold, color = TextSecondary, fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.swap_provider), fontSize = 11.sp, color = TextSecondary)
                        Text("ChangeNOW", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                    }
                    Surface(shape = RoundedCornerShape(6.dp), color = AccentBlue.copy(alpha = 0.12f)) {
                        Text(stringResource(R.string.swap_best_rate), modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 11.sp, color = AccentBlue, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (state.error != null) {
                Spacer(Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(10.dp), color = AccentRed.copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth()) {
                    Text(state.error!!, fontSize = 13.sp, color = AccentRed, modifier = Modifier.padding(12.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    placeholder: String
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        placeholder = { Text(placeholder, color = TextMuted, fontSize = 16.sp) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(12.dp),
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) TextPrimary else TextMuted
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = SurfaceLight,
            unfocusedContainerColor = SurfaceLight,
            disabledContainerColor = SurfaceLight,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            disabledTextColor = TextMuted,
            cursorColor = AccentBlue
        )
    )
}

@Composable
private fun TokenPill(current: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(20.dp),
            color = BgTertiary
        ) {
            Row(
                Modifier.padding(start = 6.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier.size(24.dp).clip(CircleShape).background(tokenColor(current)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        current.take(2).uppercase(),
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                    coil.compose.AsyncImage(
                        model = CryptoIcon.url(current),
                        contentDescription = current,
                        modifier = Modifier.size(24.dp).clip(CircleShape)
                    )
                }
                Text(current, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { token ->
                DropdownMenuItem(
                    text = { Text(token) },
                    onClick = { onSelect(token); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, valueColor: Color = TextPrimary) =
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = TextSecondary)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
    }

/** Libellé clair de l'étape d'un swap ChangeNOW (statut → français). */
private fun swapStatusLabel(status: String?): String = when (status) {
    "depositing" -> "Envoi du dépôt…"
    "waiting"    -> "En attente du dépôt sur le réseau…"
    "confirming" -> "Confirmation du dépôt…"
    "exchanging" -> "Échange en cours…"
    "sending"    -> "Envoi vers ton wallet…"
    "finished"   -> "Terminé ! Les fonds arrivent dans ton solde."
    "failed", "refunded", "expired" -> "Échec ou remboursement."
    else         -> "Traitement…"
}

/** Réseau lisible d'une monnaie (badge sous le symbole). */
private fun swapNetworkBadge(token: String): String = when (token.uppercase()) {
    "USDT" -> "TRC20"
    "BTC" -> "Bitcoin"
    "ETH" -> "Ethereum"
    "BNB" -> "BNB Chain"
    "SOL" -> "Solana"
    "TRX" -> "Tron"
    else -> token
}

/** Carte « Vous envoyez / Vous recevez » (logo + sélecteur + montant + ≈ $). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwapCoinCard(
    label: String,
    rightLabel: String?,
    token: String,
    tokens: List<String>,
    onTokenSelect: (String) -> Unit,
    amount: String,
    editable: Boolean,
    onAmountChange: (String) -> Unit,
    fiat: String?,
    onMax: (() -> Unit)?,
    highlight: Boolean
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceColor,
        border = BorderStroke(if (highlight) 1.5.dp else 1.dp, if (highlight) AccentBlue else BorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.weight(1f))
                rightLabel?.let { Text(it, fontSize = 12.sp, color = TextSecondary) }
                if (onMax != null) {
                    Spacer(Modifier.width(8.dp))
                    Text("MAX", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentBlue,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onMax() }.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Sélecteur de monnaie
                var expanded by remember { mutableStateOf(false) }
                Box {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { expanded = true }) {
                        Box(Modifier.size(38.dp).clip(CircleShape).background(tokenColor(token)), contentAlignment = Alignment.Center) {
                            Text(token.take(2).uppercase(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            coil.compose.AsyncImage(model = CryptoIcon.url(token), contentDescription = token, modifier = Modifier.size(38.dp).clip(CircleShape))
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(token, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                                Icon(Icons.Default.ArrowDropDown, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }
                            Surface(shape = RoundedCornerShape(4.dp), color = AccentBlue.copy(alpha = 0.12f)) {
                                Text(swapNetworkBadge(token), modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    fontSize = 9.sp, color = AccentBlue, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        tokens.forEach { t ->
                            DropdownMenuItem(text = { Text(t) }, onClick = { onTokenSelect(t); expanded = false })
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    if (editable) {
                        BasicTextField(
                            value = amount,
                            onValueChange = onAmountChange,
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimary, textAlign = androidx.compose.ui.text.style.TextAlign.End),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(AccentBlue),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.widthIn(min = 60.dp),
                            decorationBox = { inner -> Box(contentAlignment = Alignment.CenterEnd) { if (amount.isEmpty()) Text("0", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextMuted); inner() } }
                        )
                    } else {
                        Text(amount.ifEmpty { "0" }, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    fiat?.let { Text(it, fontSize = 12.sp, color = TextSecondary) }
                }
            }
        }
    }
}

/** Ligne de détail (icône + libellé à gauche, valeur à droite). */
@Composable
private fun SwapDetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    valueColor: Color = TextPrimary,
    chevron: Boolean = false
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 13.sp, color = TextSecondary)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
        if (chevron) { Spacer(Modifier.width(4.dp)); Icon(Icons.Default.ChevronRight, null, tint = TextSecondary, modifier = Modifier.size(16.dp)) }
    }
}
