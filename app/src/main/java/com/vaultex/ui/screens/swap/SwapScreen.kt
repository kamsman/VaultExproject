package com.vaultex.ui.screens.swap

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CallReceived
import androidx.compose.material.icons.outlined.CallMade
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vaultex.ui.components.CryptoIcon
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.NetworkBnb
import com.vaultex.ui.theme.NetworkBtc
import com.vaultex.ui.theme.NetworkEth
import com.vaultex.ui.theme.NetworkSol
import com.vaultex.ui.theme.NetworkTrx
import com.vaultex.ui.viewmodel.SwapViewModel

/* ───────────────────────── Palette sombre / violet (prototype) ───────────────────────── */
private val SwapBg = Color(0xFF0B0E16)
private val SwapCard = Color(0xFF161B26)
private val SwapCardAlt = Color(0xFF1B2130)
private val SwapBorder = Color(0xFF252B3A)
private val SwapPurple = Color(0xFF7C5CFC)
private val SwapPurpleDim = Color(0xFF2A2546)
private val SwapGreen = Color(0xFF22C55E)
private val SwapGreenDim = Color(0xFF15301F)
private val SwapText = Color(0xFFF5F6FA)
private val SwapTextDim = Color(0xFF8A91A3)
private val SwapTextFaint = Color(0xFF5B6275)

private fun tokenColor(token: String): Color = when (token.uppercase()) {
    "BTC" -> NetworkBtc
    "ETH" -> NetworkEth
    "BNB" -> NetworkBnb
    "SOL" -> NetworkSol
    "TRX", "USDT" -> NetworkTrx
    else -> SwapPurple
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

/** Nom de réseau « long » (écran de confirmation). */
private fun swapNetworkLong(token: String): String = when (token.uppercase()) {
    "USDT" -> "Tron (TRC20)"
    "BTC" -> "Bitcoin"
    "ETH" -> "Ethereum"
    "BNB" -> "BNB Chain (BEP20)"
    "SOL" -> "Solana"
    "TRX" -> "Tron"
    else -> token
}

/** Chaîne d'envoi VaultEx pour déposer le token source (notre USDT = TRC20). */
private fun swapSendChain(fromToken: String): String = when (fromToken.uppercase()) {
    "USDT" -> "USDT"
    else -> fromToken.uppercase()
}

/** Rang d'avancement d'un statut ChangeNOW (pour la frise). */
private fun statusRank(status: String?): Int = when (status) {
    "creating", "depositing" -> 0
    "waiting" -> 1
    "confirming" -> 2
    "exchanging" -> 3
    "sending" -> 4
    "finished" -> 5
    else -> 0
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwapScreen(navController: NavHostController) {
    val viewModel: SwapViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current as androidx.fragment.app.FragmentActivity
    val biometricHelper = remember { com.vaultex.core.security.BiometricHelper(context) }

    // "form" | "confirm" — le suivi (state.swapInProgress) prend la priorité.
    var screen by remember { mutableStateOf("form") }

    val tokens = listOf("ETH", "BNB", "USDT", "BTC", "SOL", "TRX")

    // Pré-sélection « De » depuis la page d'une crypto.
    LaunchedEffect(Unit) {
        com.vaultex.core.session.TokenSelectionBuffer.consume()?.let { sym ->
            val t = if (sym == "USDT-ETH" || sym == "USDT-BNB") "USDT" else sym
            if (t in tokens) viewModel.setFromToken(t)
        }
    }

    val confirmAndExecute = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val bio = biometricHelper.checkAvailability()
        if (bio == com.vaultex.core.security.BiometricHelper.BiometricStatus.AVAILABLE ||
            biometricHelper.canUseDeviceCredential()
        ) {
            biometricHelper.authenticateStrongOrCredential(
                title = "Confirmer le swap",
                subtitle = "${state.fromAmount} ${state.fromToken} → ${state.toToken}",
                onSuccess = { viewModel.executeSwap() },
                onError = { _, _ -> }
            )
        } else viewModel.executeSwap()
    }

    when {
        state.swapInProgress -> SwapTrackingScreen(
            state = state,
            onClose = { viewModel.resetSwap(); screen = "form" },
            onHistory = { viewModel.resetSwap(); screen = "form"; navController.navigate(Routes.HISTORY) }
        )
        screen == "confirm" -> SwapConfirmScreen(
            state = state,
            onBack = { screen = "form" },
            onConfirm = confirmAndExecute
        )
        else -> SwapFormScreen(
            navController = navController,
            state = state,
            tokens = tokens,
            onFromToken = viewModel::setFromToken,
            onToToken = viewModel::setToToken,
            onAmount = viewModel::setFromAmount,
            onMax = viewModel::onMaxClicked,
            onInvert = viewModel::swapTokens,
            onContinue = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                screen = "confirm"
            }
        )
    }
}

/* ════════════════════════════ 1) FORMULAIRE ════════════════════════════ */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwapFormScreen(
    navController: NavHostController,
    state: com.vaultex.ui.viewmodel.SwapState,
    tokens: List<String>,
    onFromToken: (String) -> Unit,
    onToToken: (String) -> Unit,
    onAmount: (String) -> Unit,
    onMax: () -> Unit,
    onInvert: () -> Unit,
    onContinue: () -> Unit
) {
    val fromAmt = state.fromAmount.toDoubleOrNull() ?: 0.0
    val toAmt = state.toAmount.toDoubleOrNull() ?: 0.0
    val fromFiat = if (state.fromPriceUsd > 0.0 && fromAmt > 0.0) "≈ " + String.format("%,.2f", fromAmt * state.fromPriceUsd) + " $" else null
    val toFiat = if (state.toPriceUsd > 0.0 && toAmt > 0.0) "≈ " + String.format("%,.2f", toAmt * state.toPriceUsd) + " $" else null
    val balTxt = if (state.fromBalance > 0.0)
        java.math.BigDecimal.valueOf(state.fromBalance).setScale(4, java.math.RoundingMode.DOWN).stripTrailingZeros().toPlainString()
    else "0"

    Scaffold(
        containerColor = SwapBg,
        topBar = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BoxIconButton(Icons.Default.ArrowBack, "Retour") { navController.popBackStack() }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text("Swap", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = SwapText)
                    Text("Échangez vos cryptos", fontSize = 12.sp, color = SwapTextDim)
                }
                BoxIconButton(Icons.Default.History, "Historique") { navController.navigate(Routes.HISTORY) }
            }
        },
        bottomBar = {
            Column(Modifier.background(SwapBg)) {
                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(54.dp),
                    enabled = state.fromAmount.isNotEmpty() && state.toAmount.isNotEmpty() &&
                        state.fromToken != state.toToken && !state.isLoading,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SwapPurple,
                        disabledContainerColor = SwapPurple.copy(alpha = 0.35f),
                        contentColor = Color.White,
                        disabledContentColor = Color.White.copy(alpha = 0.6f)
                    )
                ) {
                    if (state.isLoading)
                        CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Continuer", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                SwapBottomNav(navController)
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // ─── Vous envoyez ───
            SwapCoinCard(
                label = "Vous envoyez",
                rightLabel = "Solde : $balTxt ${state.fromToken}",
                token = state.fromToken, tokens = tokens, onTokenSelect = onFromToken,
                amount = state.fromAmount, editable = true, onAmountChange = onAmount,
                fiat = fromFiat, onMax = onMax, highlight = true
            )

            // Inversion
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                Surface(onClick = onInvert, shape = CircleShape, color = SwapCardAlt,
                    border = BorderStroke(1.dp, SwapBorder), modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.SwapVert, "Inverser", tint = SwapPurple, modifier = Modifier.size(22.dp))
                    }
                }
            }

            // ─── Vous recevez ───
            SwapCoinCard(
                label = "Vous recevez",
                rightLabel = null,
                token = state.toToken, tokens = tokens, onTokenSelect = onToToken,
                amount = state.toAmount, editable = false, onAmountChange = {},
                fiat = toFiat, onMax = null, highlight = false
            )

            Spacer(Modifier.height(12.dp))

            // ─── Détails ───
            Surface(shape = RoundedCornerShape(16.dp), color = SwapCard, border = BorderStroke(1.dp, SwapBorder), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 14.dp)) {
                    val rate = if (fromAmt > 0.0 && toAmt > 0.0)
                        "1 ${state.fromToken} ≈ ${String.format("%.8f", toAmt / fromAmt).trimEnd('0').trimEnd('.')} ${state.toToken}" else "—"
                    SwapDetailRow(Icons.Outlined.SwapHoriz, "Taux", rate, chevron = true)
                    Divider(color = SwapBorder, thickness = 1.dp)
                    val feeTxt = if (fromAmt > 0.0)
                        "${String.format("%.4f", fromAmt * com.vaultex.domain.usecase.SwapUseCase.VAULTEX_FEE_PERCENT / 100.0).trimEnd('0').trimEnd('.')} ${state.fromToken}" else "—"
                    SwapDetailRow(Icons.Default.Info, "Frais (inclus)", feeTxt, valueColor = SwapGreen)
                    Divider(color = SwapBorder, thickness = 1.dp)
                    SwapDetailRow(Icons.Outlined.SwapHoriz, "Délai estimé", "2 - 5 min", valueColor = SwapPurple, showIcon = false)
                }
            }

            Spacer(Modifier.height(12.dp))

            // ─── Fournisseur ───
            Surface(shape = RoundedCornerShape(16.dp), color = SwapCard, border = BorderStroke(1.dp, SwapBorder), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(SwapPurpleDim), contentAlignment = Alignment.Center) {
                        Text("N", fontWeight = FontWeight.Bold, color = SwapPurple, fontSize = 14.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Fournisseur", fontSize = 11.sp, color = SwapTextDim)
                        Text("ChangeNOW", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = SwapText)
                    }
                    Surface(shape = RoundedCornerShape(6.dp), color = SwapPurple.copy(alpha = 0.16f)) {
                        Text("Meilleur taux", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 11.sp, color = SwapPurple, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.ChevronRight, null, tint = SwapTextFaint, modifier = Modifier.size(18.dp))
                }
            }

            if (state.error != null) {
                Spacer(Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF3A1A1A), modifier = Modifier.fillMaxWidth()) {
                    Text(state.error!!, fontSize = 13.sp, color = Color(0xFFFF6B6B), modifier = Modifier.padding(12.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/* ════════════════════════════ 2) CONFIRMATION ════════════════════════════ */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwapConfirmScreen(
    state: com.vaultex.ui.viewmodel.SwapState,
    onBack: () -> Unit,
    onConfirm: () -> Unit
) {
    val fromAmt = state.fromAmount.toDoubleOrNull() ?: 0.0
    val toAmt = state.toAmount.toDoubleOrNull() ?: 0.0
    val fromFiat = if (state.fromPriceUsd > 0.0 && fromAmt > 0.0) "≈ " + String.format("%,.2f", fromAmt * state.fromPriceUsd) + " $" else ""
    val toFiat = if (state.toPriceUsd > 0.0 && toAmt > 0.0) "≈ " + String.format("%,.2f", toAmt * state.toPriceUsd) + " $" else ""
    // Montant minimum reçu (~2% de marge sous l'estimation, comme un slippage).
    val minReceive = if (toAmt > 0.0) String.format("%.6f", toAmt * 0.98).trimEnd('0').trimEnd('.') else "—"

    Scaffold(
        containerColor = SwapBg,
        topBar = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                BoxIconButton(Icons.Default.ArrowBack, "Retour", onBack)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text("Swap", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = SwapText)
                    Text("Vérifiez et confirmez", fontSize = 12.sp, color = SwapTextDim)
                }
            }
        },
        bottomBar = {
            Column(Modifier.background(SwapBg).padding(horizontal = 16.dp, vertical = 10.dp)) {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    enabled = !state.isLoading,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SwapPurple, contentColor = Color.White,
                        disabledContainerColor = SwapPurple.copy(alpha = 0.35f)
                    )
                ) {
                    if (state.isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Confirmer le swap", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(10.dp))
                Text("Le taux sera valable pendant 00:29", fontSize = 12.sp, color = SwapTextDim,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Vérifiez les détails", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = SwapText,
                modifier = Modifier.padding(top = 4.dp))

            // Carte récap envoi/réception
            Surface(shape = RoundedCornerShape(16.dp), color = SwapCard, border = BorderStroke(1.dp, SwapBorder), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    ConfirmAmountRow("Vous envoyez", state.fromToken, "${state.fromAmount} ${state.fromToken}", fromFiat)
                    Box(Modifier.padding(start = 4.dp, top = 6.dp, bottom = 6.dp)) {
                        Icon(Icons.Default.SwapVert, null, tint = SwapTextFaint, modifier = Modifier.size(20.dp))
                    }
                    ConfirmAmountRow("Vous recevez", state.toToken, "${state.toAmount.ifEmpty { "—" }} ${state.toToken}", toFiat)
                }
            }

            // Carte détails
            Surface(shape = RoundedCornerShape(16.dp), color = SwapCard, border = BorderStroke(1.dp, SwapBorder), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 14.dp)) {
                    ConfirmRow("Fournisseur", "ChangeNOW", chevron = true)
                    Divider(color = SwapBorder)
                    val rate = if (fromAmt > 0.0 && toAmt > 0.0)
                        "1 ${state.fromToken} ≈ ${String.format("%.8f", toAmt / fromAmt).trimEnd('0').trimEnd('.')} ${state.toToken}" else "—"
                    ConfirmRow("Taux", rate)
                    Divider(color = SwapBorder)
                    val feeTxt = if (fromAmt > 0.0)
                        "${String.format("%.4f", fromAmt * com.vaultex.domain.usecase.SwapUseCase.VAULTEX_FEE_PERCENT / 100.0).trimEnd('0').trimEnd('.')} ${state.fromToken}" else "—"
                    ConfirmRow("Frais (inclus)", feeTxt, valueColor = SwapGreen)
                    Divider(color = SwapBorder)
                    ConfirmRow("Réseau", "${swapNetworkLong(state.fromToken)} → ${swapNetworkLong(state.toToken)}")
                    Divider(color = SwapBorder)
                    ConfirmRow("Délai estimé", "2 - 5 min", valueColor = SwapPurple)
                }
            }

            // Estimation
            Surface(shape = RoundedCornerShape(12.dp), color = SwapGreenDim, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = SwapGreen, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Le montant que vous recevrez est estimé. Vous recevrez au moins $minReceive ${state.toToken}",
                        fontSize = 12.sp, color = SwapGreen, lineHeight = 16.sp
                    )
                }
            }

            // Avertissement réseau
            Surface(shape = RoundedCornerShape(12.dp), color = SwapCardAlt, border = BorderStroke(1.dp, SwapBorder), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Soyez attentif au réseau", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = SwapText)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        buildAnnotatedString(
                            "Le dépôt se fait sur le réseau ", swapNetworkLong(state.fromToken), ". L'app dépose automatiquement, vous n'avez rien à copier."
                        ),
                        fontSize = 12.sp, color = SwapTextDim, lineHeight = 16.sp
                    )
                }
            }

            if (state.error != null) {
                Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF3A1A1A), modifier = Modifier.fillMaxWidth()) {
                    Text(state.error!!, fontSize = 13.sp, color = Color(0xFFFF6B6B), modifier = Modifier.padding(12.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/* ════════════════════════════ 3) SUIVI / SUCCÈS ════════════════════════════ */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwapTrackingScreen(
    state: com.vaultex.ui.viewmodel.SwapState,
    onClose: () -> Unit,
    onHistory: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val finished = state.swapStatus == "finished"
    val failed = state.swapStatus in listOf("failed", "refunded", "expired")
    val rank = statusRank(state.swapStatus)

    LaunchedEffect(finished) { if (finished) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }

    val payoutAddr = "votre portefeuille"

    Scaffold(
        containerColor = SwapBg,
        topBar = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                BoxIconButton(Icons.Default.ArrowBack, "Fermer", onClose)
                Text(
                    if (finished) "Swap terminé !" else if (failed) "Swap échoué" else "Swap en cours",
                    fontWeight = FontWeight.Bold, fontSize = 18.sp, color = SwapText,
                    modifier = Modifier.weight(1f).padding(start = 14.dp)
                )
            }
        },
        bottomBar = {
            Column(Modifier.background(SwapBg).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        val msg = "Swap VaultEx : ${state.fromAmount} ${state.fromToken} → ≈ ${state.toAmount} ${state.toToken}" +
                            (state.swapId?.let { "\nID : $it" } ?: "")
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, msg)
                        }
                        context.startActivity(android.content.Intent.createChooser(send, "Partager le reçu"))
                    },
                    enabled = finished,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SwapPurple, contentColor = Color.White,
                        disabledContainerColor = SwapPurple.copy(alpha = 0.30f)
                    )
                ) { Text("Partager le reçu", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                OutlinedButton(
                    onClick = onHistory,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, SwapBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SwapText)
                ) { Text("Voir dans l'historique", fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            // Deux logos + flèche
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                TokenBadgeWithCheck(state.fromToken, finished)
                Icon(Icons.Default.ChevronRight, null, tint = SwapTextFaint, modifier = Modifier.size(26.dp))
                TokenBadgeWithCheck(state.toToken, finished)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${state.fromAmount} ${state.fromToken}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = SwapText)
                    Text(swapNetworkBadge(state.fromToken), fontSize = 11.sp, color = SwapTextDim)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("≈ ${state.toAmount.ifEmpty { "—" }} ${state.toToken}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = SwapText)
                    Text(swapNetworkBadge(state.toToken), fontSize = 11.sp, color = SwapTextDim)
                }
            }

            // Grand cercle d'état
            Box(
                Modifier.size(72.dp).clip(CircleShape)
                    .background(if (finished) SwapGreen else if (failed) Color(0xFF7A2222) else SwapPurpleDim),
                contentAlignment = Alignment.Center
            ) {
                when {
                    finished -> Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(40.dp))
                    failed -> Text("!", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    else -> CircularProgressIndicator(color = SwapPurple, strokeWidth = 4.dp, modifier = Modifier.size(40.dp))
                }
            }

            // Détails / frise
            Surface(shape = RoundedCornerShape(16.dp), color = SwapCard, border = BorderStroke(1.dp, SwapBorder), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Détails de la transaction", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = SwapText, modifier = Modifier.weight(1f))
                        val statusTxt = if (finished) "Réussie" else if (failed) "Échouée" else "En cours"
                        val statusCol = if (finished) SwapGreen else if (failed) Color(0xFFFF6B6B) else SwapPurple
                        Surface(shape = RoundedCornerShape(6.dp), color = statusCol.copy(alpha = 0.16f)) {
                            Text(statusTxt, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontSize = 11.sp, color = statusCol, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    TimelineStep("Transaction créée", null, done = rank >= 0 || finished, active = false, last = false)
                    TimelineStep("Confirmations réseau ${swapNetworkBadge(state.fromToken)}", null, done = rank > 2 || finished, active = rank in 1..2 && !finished, last = false)
                    TimelineStep("Échange effectué", "Par ChangeNOW", done = rank > 3 || finished, active = rank == 3 && !finished, last = false)
                    TimelineStep("Envoi des ${state.toToken}", swapNetworkBadge(state.toToken), done = rank > 4 || finished, active = rank == 4 && !finished, last = false)
                    TimelineStep("Terminé", null, done = finished, active = false, last = true)
                }
            }

            // Adresse de réception
            Surface(shape = RoundedCornerShape(16.dp), color = SwapCard, border = BorderStroke(1.dp, SwapBorder), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Vous recevez", fontSize = 12.sp, color = SwapTextDim)
                    Text("≈ ${state.toAmount.ifEmpty { "—" }} ${state.toToken}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = SwapText)
                    Spacer(Modifier.height(8.dp))
                    Text("À l'adresse de $payoutAddr", fontSize = 12.sp, color = SwapTextDim)
                    state.swapId?.let { id ->
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                clipboard.setText(AnnotatedString(id))
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }) {
                            Text("ID : ${id.take(18)}…", fontSize = 12.sp, color = SwapPurple, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ContentCopy, "Copier", tint = SwapPurple, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            if (failed && state.error != null) {
                Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF3A1A1A), modifier = Modifier.fillMaxWidth()) {
                    Text(state.error!!, fontSize = 13.sp, color = Color(0xFFFF6B6B), modifier = Modifier.padding(12.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/* ───────────────────────── Composants partagés ───────────────────────── */

@Composable
private fun BoxIconButton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = SwapCard, border = BorderStroke(1.dp, SwapBorder), modifier = Modifier.size(40.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, desc, tint = SwapText, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun TokenLogo(token: String, size: Int) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(tokenColor(token)), contentAlignment = Alignment.Center) {
        Text(token.take(2).uppercase(), color = Color.White, fontSize = (size / 4).sp, fontWeight = FontWeight.Bold)
        coil.compose.AsyncImage(model = CryptoIcon.url(token), contentDescription = token, modifier = Modifier.size(size.dp).clip(CircleShape))
    }
}

@Composable
private fun TokenBadgeWithCheck(token: String, finished: Boolean) {
    Box(contentAlignment = Alignment.BottomEnd) {
        TokenLogo(token, 56)
        if (finished) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(SwapBg).padding(2.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(SwapGreen), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
                }
            }
        }
    }
}

@Composable
private fun TimelineStep(title: String, subtitle: String?, done: Boolean, active: Boolean, last: Boolean) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(22.dp).clip(CircleShape)
                    .background(if (done) SwapGreen else if (active) SwapPurple else SwapCardAlt),
                contentAlignment = Alignment.Center
            ) {
                when {
                    done -> Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
                    active -> CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(12.dp))
                    else -> Box(Modifier.size(6.dp).clip(CircleShape).background(SwapTextFaint))
                }
            }
            if (!last) Box(Modifier.width(2.dp).weight(1f).background(if (done) SwapGreen else SwapBorder))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.padding(bottom = if (last) 0.dp else 16.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = if (done || active) SwapText else SwapTextDim)
            subtitle?.let { Text(it, fontSize = 11.sp, color = SwapTextDim) }
        }
    }
}

@Composable
private fun ConfirmAmountRow(label: String, token: String, amount: String, fiat: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TokenLogo(token, 40)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, color = SwapTextDim)
            Text(amount, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = SwapText)
        }
        if (fiat.isNotEmpty()) Text(fiat, fontSize = 12.sp, color = SwapTextDim)
    }
}

@Composable
private fun ConfirmRow(label: String, value: String, valueColor: Color = SwapText, chevron: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = SwapTextDim)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = valueColor, textAlign = TextAlign.End)
        if (chevron) { Spacer(Modifier.width(4.dp)); Icon(Icons.Default.ChevronRight, null, tint = SwapTextFaint, modifier = Modifier.size(16.dp)) }
    }
}

private fun buildAnnotatedString(a: String, b: String, c: String): AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        append(a)
        pushStyle(androidx.compose.ui.text.SpanStyle(color = SwapPurple, fontWeight = FontWeight.SemiBold))
        append(b); pop()
        append(c)
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
        color = SwapCard,
        border = BorderStroke(if (highlight) 1.5.dp else 1.dp, if (highlight) SwapPurple else SwapBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 12.sp, color = SwapTextDim)
                Spacer(Modifier.weight(1f))
                rightLabel?.let { Text(it, fontSize = 12.sp, color = SwapTextDim) }
                if (onMax != null) {
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(6.dp), color = SwapPurple.copy(alpha = 0.16f),
                        modifier = Modifier.clickable { onMax() }) {
                        Text("MAX", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SwapPurple,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    Surface(shape = RoundedCornerShape(12.dp), color = SwapCardAlt,
                        modifier = Modifier.clickable { expanded = true }) {
                        Row(Modifier.padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TokenLogo(token, 34)
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(token, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = SwapText)
                                    Icon(Icons.Default.ArrowDropDown, null, tint = SwapTextDim, modifier = Modifier.size(18.dp))
                                }
                                Surface(shape = RoundedCornerShape(4.dp), color = SwapPurple.copy(alpha = 0.16f)) {
                                    Text(swapNetworkBadge(token), modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                        fontSize = 9.sp, color = SwapPurple, fontWeight = FontWeight.SemiBold)
                                }
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
                            textStyle = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = SwapText, textAlign = TextAlign.End),
                            cursorBrush = SolidColor(SwapPurple),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.widthIn(min = 60.dp),
                            decorationBox = { inner -> Box(contentAlignment = Alignment.CenterEnd) { if (amount.isEmpty()) Text("0", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = SwapTextFaint); inner() } }
                        )
                    } else {
                        Text(amount.ifEmpty { "0" }, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = SwapText)
                    }
                    fiat?.let { Text(it, fontSize = 12.sp, color = SwapTextDim) }
                }
            }
        }
    }
}

/** Ligne de détail (icône + libellé à gauche, valeur à droite). */
@Composable
private fun SwapDetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = SwapText,
    chevron: Boolean = false,
    showIcon: Boolean = true
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showIcon) { Icon(icon, null, tint = SwapTextDim, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)) }
        Text(label, fontSize = 13.sp, color = SwapTextDim)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
        if (chevron) { Spacer(Modifier.width(4.dp)); Icon(Icons.Default.ChevronRight, null, tint = SwapTextFaint, modifier = Modifier.size(16.dp)) }
    }
}

/** Barre de navigation sombre (prototype) : Accueil / Recevoir / Swap / Envoyer / Portefeuille. */
@Composable
private fun SwapBottomNav(navController: NavHostController) {
    Surface(color = SwapCard, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            NavItem(Icons.Default.Home, "Accueil", false) { navController.navigate(Routes.DASHBOARD) }
            NavItem(Icons.Outlined.CallReceived, "Recevoir", false) { navController.navigate(Routes.RECEIVE) }
            NavItem(Icons.Outlined.SwapHoriz, "Swap", true) { }
            NavItem(Icons.Outlined.CallMade, "Envoyer", false) { navController.navigate(Routes.SEND) }
            NavItem(Icons.Outlined.AccountBalanceWallet, "Portefeuille", false) { navController.navigate(Routes.PORTFOLIO) }
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val col = if (selected) SwapPurple else SwapTextDim
    Column(
        Modifier.clip(RoundedCornerShape(10.dp)).clickable { onClick() }.padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = col, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 10.sp, color = col, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
