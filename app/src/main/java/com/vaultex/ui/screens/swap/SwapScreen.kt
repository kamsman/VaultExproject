package com.vaultex.ui.screens.swap

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.CallReceived
import androidx.compose.material.icons.outlined.CallMade
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vaultex.ui.components.CryptoIcon
import com.vaultex.ui.components.ExpandableDetails
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.AccentRed
import com.vaultex.ui.theme.BgPrimary
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
import com.vaultex.ui.viewmodel.SwapViewModel

/* ───────────────────────── Palette violet (accents fixes) ─────────────────────────
 * Les fonds / textes suivent le thème ACTUEL de l'app (clair ou sombre) via les
 * accesseurs @Composable ci-dessous ; seuls le violet et le vert restent constants. */
private val SwapPurple = Color(0xFF7C5CFC)
private val SwapGreen = Color(0xFF22C55E)

private val swapBg: Color        @Composable get() = BgPrimary
private val swapCard: Color      @Composable get() = SurfaceColor
private val swapCardAlt: Color   @Composable get() = SurfaceLight
private val swapBorder: Color    @Composable get() = BorderColor
private val swapText: Color      @Composable get() = TextPrimary
private val swapTextDim: Color   @Composable get() = TextSecondary
private val swapTextFaint: Color @Composable get() = TextMuted
private val swapPurpleDim: Color @Composable get() = SwapPurple.copy(alpha = 0.16f)
private val swapGreenDim: Color  @Composable get() = SwapGreen.copy(alpha = 0.14f)
private val swapErrBg: Color     @Composable get() = AccentRed.copy(alpha = 0.12f)

private fun tokenColor(token: String): Color = when (token.uppercase()) {
    "BTC" -> NetworkBtc
    "ETH" -> NetworkEth
    "BNB" -> NetworkBnb
    "SOL" -> NetworkSol
    "TRX", "USDT" -> NetworkTrx
    else -> SwapPurple
}

/* Affichage d'un actif : tout vient du registre du ViewModel (12 actifs). */
private fun swapBaseOf(key: String): String = SwapViewModel.assetOf(key).base
private fun swapNetworkBadge(key: String): String = SwapViewModel.assetOf(key).badge
private fun swapNetworkLong(key: String): String = SwapViewModel.assetOf(key).network

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

    val tokens = SwapViewModel.SWAP_ASSETS.map { it.key }

    // Pré-sélection « De » depuis la page d'une crypto.
    LaunchedEffect(Unit) {
        com.vaultex.core.session.TokenSelectionBuffer.consume()?.let { sym ->
            if (tokens.any { it.equals(sym, ignoreCase = true) }) viewModel.setFromToken(sym)
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
                subtitle = "${state.fromAmount} ${swapBaseOf(state.fromToken)} → ${swapBaseOf(state.toToken)}",
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
            balanceInfo = viewModel::balanceInfo,
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
    balanceInfo: (String) -> Pair<Double, Double>,
    onFromToken: (String) -> Unit,
    onToToken: (String) -> Unit,
    onAmount: (String) -> Unit,
    onMax: () -> Unit,
    onInvert: () -> Unit,
    onContinue: () -> Unit
) {
    val fromAmt = state.fromAmount.toDoubleOrNull() ?: 0.0
    val toAmt = state.toAmount.toDoubleOrNull() ?: 0.0
    val fromFiat = if (state.fromPriceUsd > 0.0 && fromAmt > 0.0) "≈ " + String.format(java.util.Locale.US, "%,.2f", fromAmt * state.fromPriceUsd) + " $" else null
    val toFiat = if (state.toPriceUsd > 0.0 && toAmt > 0.0) "≈ " + String.format(java.util.Locale.US, "%,.2f", toAmt * state.toPriceUsd) + " $" else null
    // Affiche le vrai solde : si trop petit pour 4 décimales (ex. 0,00003 BNB
    // arrondi à « 0 »), on montre jusqu'à 8 décimales pour ne pas mentir.
    val balTxt = if (state.fromBalance > 0.0) {
        val bd4 = java.math.BigDecimal.valueOf(state.fromBalance).setScale(4, java.math.RoundingMode.DOWN).stripTrailingZeros()
        if (bd4.signum() == 0)
            java.math.BigDecimal.valueOf(state.fromBalance).setScale(8, java.math.RoundingMode.DOWN).stripTrailingZeros().toPlainString()
        else bd4.toPlainString()
    } else "0"

    Scaffold(
        containerColor = swapBg,
        topBar = {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                BoxIconButton(Icons.Default.ArrowBack, "Retour", Modifier.align(Alignment.CenterStart)) { navController.popBackStack() }
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Swap", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = swapText)
                    Text("Échangez vos cryptos", fontSize = 12.sp, color = swapTextDim)
                }
                BoxIconButton(Icons.Default.History, "Historique", Modifier.align(Alignment.CenterEnd)) { navController.navigate(Routes.HISTORY) }
            }
        },
        bottomBar = {
            Column(Modifier.background(swapBg)) {
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
                rightLabel = "Solde : $balTxt ${swapBaseOf(state.fromToken)}",
                token = state.fromToken, tokens = tokens, balanceInfo = balanceInfo, onTokenSelect = onFromToken,
                amount = state.fromAmount, editable = true, onAmountChange = onAmount,
                fiat = fromFiat, onMax = onMax, highlight = true
            )

            // Inversion (le bouton fait un demi-tour à chaque clic → preuve visuelle de l'échange)
            var invertSpin by remember { mutableStateOf(0f) }
            val spinAngle by animateFloatAsState(
                targetValue = invertSpin,
                animationSpec = tween(durationMillis = 420),
                label = "swapInvertSpin"
            )
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                Surface(
                    onClick = { invertSpin += 180f; onInvert() },
                    shape = CircleShape, color = swapCardAlt,
                    border = BorderStroke(1.dp, swapBorder), modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.SwapVert, "Inverser", tint = SwapPurple,
                            modifier = Modifier.size(22.dp).rotate(spinAngle))
                    }
                }
            }

            // ─── Vous recevez ───
            SwapCoinCard(
                label = "Vous recevez",
                rightLabel = null,
                token = state.toToken, tokens = tokens, balanceInfo = balanceInfo, onTokenSelect = onToToken,
                amount = state.toAmount, editable = false, onAmountChange = {},
                fiat = toFiat, onMax = null, highlight = false
            )

            Spacer(Modifier.height(12.dp))

            // ─── Détails ───
            Surface(shape = RoundedCornerShape(16.dp), color = swapCard, border = BorderStroke(1.dp, swapBorder), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 14.dp)) {
                    val rate = if (fromAmt > 0.0 && toAmt > 0.0)
                        "1 ${swapBaseOf(state.fromToken)} ≈ ${String.format(java.util.Locale.US, "%.8f", toAmt / fromAmt).trimEnd('0').trimEnd('.')} ${swapBaseOf(state.toToken)}" else "—"
                    SwapDetailRow(Icons.Outlined.SwapHoriz, "Taux", rate, chevron = true)
                    Divider(color = swapBorder, thickness = 1.dp)
                    val feeTxt = if (fromAmt > 0.0)
                        "${String.format(java.util.Locale.US, "%.4f", fromAmt * com.vaultex.domain.usecase.SwapUseCase.VAULTEX_FEE_PERCENT / 100.0).trimEnd('0').trimEnd('.')} ${swapBaseOf(state.fromToken)}" else "—"
                    SwapDetailRow(Icons.Default.Info, "Frais (inclus)", feeTxt, valueColor = SwapGreen)
                    Divider(color = swapBorder, thickness = 1.dp)
                    SwapDetailRow(Icons.Outlined.SwapHoriz, "Délai estimé", "2 - 5 min", valueColor = SwapPurple, showIcon = false)
                }
            }

            Spacer(Modifier.height(12.dp))

            // ─── Fournisseur ───
            Surface(shape = RoundedCornerShape(16.dp), color = swapCard, border = BorderStroke(1.dp, swapBorder), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(swapPurpleDim), contentAlignment = Alignment.Center) {
                        Text("N", fontWeight = FontWeight.Bold, color = SwapPurple, fontSize = 14.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Fournisseur", fontSize = 11.sp, color = swapTextDim)
                        Text("ChangeNOW", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = swapText)
                    }
                    Surface(shape = RoundedCornerShape(6.dp), color = SwapPurple.copy(alpha = 0.16f)) {
                        Text("Meilleur taux", modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 11.sp, color = SwapPurple, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.ChevronRight, null, tint = swapTextFaint, modifier = Modifier.size(18.dp))
                }
            }

            if (state.error != null) {
                Spacer(Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(10.dp), color = swapErrBg, modifier = Modifier.fillMaxWidth()) {
                    Text(state.error!!, fontSize = 13.sp, color = AccentRed, modifier = Modifier.padding(12.dp))
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
    val fromFiat = if (state.fromPriceUsd > 0.0 && fromAmt > 0.0) "≈ " + String.format(java.util.Locale.US, "%,.2f", fromAmt * state.fromPriceUsd) + " $" else ""
    val toFiat = if (state.toPriceUsd > 0.0 && toAmt > 0.0) "≈ " + String.format(java.util.Locale.US, "%,.2f", toAmt * state.toPriceUsd) + " $" else ""
    // Montant minimum reçu (~2% de marge sous l'estimation, comme un slippage).
    val minReceive = if (toAmt > 0.0) String.format(java.util.Locale.US, "%.6f", toAmt * 0.98).trimEnd('0').trimEnd('.') else "—"

    Scaffold(
        containerColor = swapBg,
        topBar = {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                BoxIconButton(Icons.Default.ArrowBack, "Retour", Modifier.align(Alignment.CenterStart), onClick = onBack)
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Swap", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = swapText)
                    Text("Vérifiez et confirmez", fontSize = 12.sp, color = swapTextDim)
                }
            }
        },
        bottomBar = {
            Column(Modifier.background(swapBg).padding(horizontal = 16.dp, vertical = 10.dp)) {
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
                Text("Le taux sera valable pendant 00:29", fontSize = 12.sp, color = swapTextDim,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Vérifiez les détails", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = swapText,
                modifier = Modifier.padding(top = 4.dp))

            // Carte récap envoi/réception
            Surface(shape = RoundedCornerShape(16.dp), color = swapCard, border = BorderStroke(1.dp, swapBorder), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    ConfirmAmountRow("Vous envoyez", state.fromToken, "${state.fromAmount} ${swapBaseOf(state.fromToken)}", fromFiat)
                    Box(Modifier.padding(start = 4.dp, top = 6.dp, bottom = 6.dp)) {
                        Icon(Icons.Default.SwapVert, null, tint = swapTextFaint, modifier = Modifier.size(20.dp))
                    }
                    ConfirmAmountRow("Vous recevez", state.toToken, "${state.toAmount.ifEmpty { "—" }} ${swapBaseOf(state.toToken)}", toFiat)
                }
            }

            /*
            Même principe que la confirmation d'envoi : le TAUX est la seule
            donnée sur laquelle l'utilisateur décide vraiment — c'est elle qui
            dit ce qu'il obtient. Fournisseur, frais, réseau et délai servent à
            vérifier. Ils passent donc derrière « Voir détails », pour que le
            bouton de confirmation reste atteignable sans faire défiler.
             */
            val rate = if (fromAmt > 0.0 && toAmt > 0.0)
                "1 ${swapBaseOf(state.fromToken)} ≈ ${String.format(java.util.Locale.US, "%.8f", toAmt / fromAmt).trimEnd('0').trimEnd('.')} ${swapBaseOf(state.toToken)}" else "—"
            val feeTxt = if (fromAmt > 0.0)
                "${String.format(java.util.Locale.US, "%.4f", fromAmt * com.vaultex.domain.usecase.SwapUseCase.VAULTEX_FEE_PERCENT / 100.0).trimEnd('0').trimEnd('.')} ${swapBaseOf(state.fromToken)}" else "—"

            Surface(shape = RoundedCornerShape(16.dp), color = swapCard, border = BorderStroke(1.dp, swapBorder), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 14.dp)) {
                    ConfirmRow("Taux", rate)
                    ExpandableDetails(
                        accent = SwapPurple,
                        labelColor = swapTextDim,
                        summary = "Frais inclus : $feeTxt"
                    ) {
                        Divider(color = swapBorder)
                        ConfirmRow("Fournisseur", "ChangeNOW", chevron = true)
                        Divider(color = swapBorder)
                        ConfirmRow("Frais (inclus)", feeTxt, valueColor = SwapGreen)
                        Divider(color = swapBorder)
                        ConfirmRow("Réseau", "${swapNetworkLong(state.fromToken)} → ${swapNetworkLong(state.toToken)}")
                        Divider(color = swapBorder)
                        ConfirmRow("Délai estimé", "2 - 5 min", valueColor = SwapPurple)
                    }
                }
            }

            // Estimation
            Surface(shape = RoundedCornerShape(12.dp), color = swapGreenDim, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = SwapGreen, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Le montant que vous recevrez est estimé. Vous recevrez au moins $minReceive ${swapBaseOf(state.toToken)}",
                        fontSize = 12.sp, color = SwapGreen, lineHeight = 16.sp
                    )
                }
            }

            // Avertissement réseau
            Surface(shape = RoundedCornerShape(12.dp), color = swapCardAlt, border = BorderStroke(1.dp, swapBorder), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Soyez attentif au réseau", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = swapText)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        buildAnnotatedString(
                            "Le dépôt se fait sur le réseau ", swapNetworkLong(state.fromToken), ". L'app dépose automatiquement, vous n'avez rien à copier."
                        ),
                        fontSize = 12.sp, color = swapTextDim, lineHeight = 16.sp
                    )
                }
            }

            if (state.error != null) {
                Surface(shape = RoundedCornerShape(10.dp), color = swapErrBg, modifier = Modifier.fillMaxWidth()) {
                    Text(state.error!!, fontSize = 13.sp, color = AccentRed, modifier = Modifier.padding(12.dp))
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
        containerColor = swapBg,
        topBar = {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                BoxIconButton(Icons.Default.ArrowBack, "Fermer", Modifier.align(Alignment.CenterStart), onClick = onClose)
                Text(
                    if (finished) "Swap terminé !" else if (failed) "Swap échoué" else "Swap en cours",
                    fontWeight = FontWeight.Bold, fontSize = 18.sp, color = swapText,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        },
        bottomBar = {
            Column(Modifier.background(swapBg).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Bouton PRINCIPAL « Nouveau swap » (remplace « Partager le reçu ») :
                // toujours actif — pendant le traitement, l'échange se termine tout
                // seul en arrière-plan, l'utilisateur n'a pas à attendre.
                Button(
                    onClick = onClose,   // resetSwap → formulaire vierge
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SwapPurple, contentColor = Color.White)
                ) { Text("Nouveau swap", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                OutlinedButton(
                    onClick = onHistory,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, swapBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = swapText)
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
                Icon(Icons.Default.ChevronRight, null, tint = swapTextFaint, modifier = Modifier.size(26.dp))
                TokenBadgeWithCheck(state.toToken, finished)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${state.fromAmount} ${swapBaseOf(state.fromToken)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = swapText)
                    Text(swapNetworkBadge(state.fromToken), fontSize = 11.sp, color = swapTextDim)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("≈ ${state.toAmount.ifEmpty { "—" }} ${swapBaseOf(state.toToken)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = swapText)
                    Text(swapNetworkBadge(state.toToken), fontSize = 11.sp, color = swapTextDim)
                }
            }

            // Grand cercle d'état
            Box(
                Modifier.size(72.dp).clip(CircleShape)
                    .background(if (finished) SwapGreen else if (failed) AccentRed else swapPurpleDim),
                contentAlignment = Alignment.Center
            ) {
                when {
                    finished -> Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(40.dp))
                    failed -> Text("!", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    else -> CircularProgressIndicator(color = SwapPurple, strokeWidth = 4.dp, modifier = Modifier.size(40.dp))
                }
            }

            // Message « pas besoin d'attendre » pendant le traitement (2–5 min).
            if (!finished && !failed) {
                Surface(shape = RoundedCornerShape(12.dp), color = swapPurpleDim, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = SwapPurple, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            // La promesse « pas besoin d'attendre » n'était pas
                            // tenue : le suivi vivait dans le ViewModel de cet
                            // écran et mourait en le quittant. SwapTrackingWorker
                            // le reprend désormais depuis la base, donc on peut
                            // annoncer la notification sans mentir.
                            "Le dépôt est envoyé ✓. L'échange se termine tout seul en 2 à 5 min — pas besoin d'attendre ici. Tu recevras une notification dès qu'il est terminé, même si tu fermes l'application.",
                            fontSize = 12.sp, color = swapText, lineHeight = 16.sp
                        )
                    }
                }
            }

            // Détails / frise
            Surface(shape = RoundedCornerShape(16.dp), color = swapCard, border = BorderStroke(1.dp, swapBorder), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Détails de la transaction", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = swapText, modifier = Modifier.weight(1f))
                        val statusTxt = if (finished) "Réussie" else if (failed) "Échouée" else "En cours"
                        val statusCol = if (finished) SwapGreen else if (failed) AccentRed else SwapPurple
                        Surface(shape = RoundedCornerShape(6.dp), color = statusCol.copy(alpha = 0.16f)) {
                            Text(statusTxt, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontSize = 11.sp, color = statusCol, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    TimelineStep("Transaction créée", null, done = rank >= 0 || finished, active = false, last = false)
                    TimelineStep("Confirmations réseau ${swapNetworkBadge(state.fromToken)}", null, done = rank > 2 || finished, active = rank in 1..2 && !finished, last = false)
                    TimelineStep("Échange effectué", "Par ChangeNOW", done = rank > 3 || finished, active = rank == 3 && !finished, last = false)
                    TimelineStep("Envoi des ${swapBaseOf(state.toToken)}", swapNetworkBadge(state.toToken), done = rank > 4 || finished, active = rank == 4 && !finished, last = false)
                    TimelineStep("Terminé", null, done = finished, active = false, last = true)
                }
            }

            // Adresse de réception
            Surface(shape = RoundedCornerShape(16.dp), color = swapCard, border = BorderStroke(1.dp, swapBorder), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Vous recevez", fontSize = 12.sp, color = swapTextDim)
                    Text("≈ ${state.toAmount.ifEmpty { "—" }} ${swapBaseOf(state.toToken)}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = swapText)
                    Spacer(Modifier.height(8.dp))
                    Text("À l'adresse de $payoutAddr", fontSize = 12.sp, color = swapTextDim)
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
                Surface(shape = RoundedCornerShape(10.dp), color = swapErrBg, modifier = Modifier.fillMaxWidth()) {
                    Text(state.error!!, fontSize = 13.sp, color = AccentRed, modifier = Modifier.padding(12.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/* ───────────────────────── Composants partagés ───────────────────────── */

@Composable
private fun BoxIconButton(icon: ImageVector, desc: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = swapCard, border = BorderStroke(1.dp, swapBorder), modifier = modifier.size(40.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, desc, tint = swapText, modifier = Modifier.size(20.dp)) }
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
            Box(Modifier.size(22.dp).clip(CircleShape).background(swapBg).padding(2.dp), contentAlignment = Alignment.Center) {
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
                    .background(if (done) SwapGreen else if (active) SwapPurple else swapCardAlt),
                contentAlignment = Alignment.Center
            ) {
                when {
                    done -> Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
                    active -> CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(12.dp))
                    else -> Box(Modifier.size(6.dp).clip(CircleShape).background(swapTextFaint))
                }
            }
            if (!last) Box(Modifier.width(2.dp).weight(1f).background(if (done) SwapGreen else swapBorder))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.padding(bottom = if (last) 0.dp else 16.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = if (done || active) swapText else swapTextDim)
            subtitle?.let { Text(it, fontSize = 11.sp, color = swapTextDim) }
        }
    }
}

@Composable
private fun ConfirmAmountRow(label: String, token: String, amount: String, fiat: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TokenLogo(token, 40)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, color = swapTextDim)
            Text(amount, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = swapText)
        }
        if (fiat.isNotEmpty()) Text(fiat, fontSize = 12.sp, color = swapTextDim)
    }
}

@Composable
private fun ConfirmRow(label: String, value: String, valueColor: Color = swapText, chevron: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = swapTextDim)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = valueColor, textAlign = TextAlign.End)
        if (chevron) { Spacer(Modifier.width(4.dp)); Icon(Icons.Default.ChevronRight, null, tint = swapTextFaint, modifier = Modifier.size(16.dp)) }
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
    balanceInfo: (String) -> Pair<Double, Double>,
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
        color = swapCard,
        border = BorderStroke(if (highlight) 1.5.dp else 1.dp, if (highlight) SwapPurple else swapBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 12.sp, color = swapTextDim)
                Spacer(Modifier.weight(1f))
                rightLabel?.let { Text(it, fontSize = 12.sp, color = swapTextDim) }
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
                var showPicker by remember { mutableStateOf(false) }
                Surface(shape = RoundedCornerShape(12.dp), color = swapCardAlt,
                    modifier = Modifier.clickable { showPicker = true }) {
                    Row(Modifier.padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TokenLogo(token, 34)
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(swapBaseOf(token), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = swapText)
                                Icon(Icons.Default.ArrowDropDown, null, tint = swapTextDim, modifier = Modifier.size(18.dp))
                            }
                            Surface(shape = RoundedCornerShape(4.dp), color = SwapPurple.copy(alpha = 0.16f)) {
                                Text(swapNetworkBadge(token), modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    fontSize = 9.sp, color = SwapPurple, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                if (showPicker) {
                    TokenPickerSheet(
                        tokens = tokens,
                        current = token,
                        balanceInfo = balanceInfo,
                        onSelect = { onTokenSelect(it); showPicker = false },
                        onDismiss = { showPicker = false }
                    )
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    if (editable) {
                        BasicTextField(
                            value = amount,
                            onValueChange = onAmountChange,
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = swapText, textAlign = TextAlign.End),
                            cursorBrush = SolidColor(SwapPurple),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.widthIn(min = 60.dp),
                            decorationBox = { inner -> Box(contentAlignment = Alignment.CenterEnd) { if (amount.isEmpty()) Text("0", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = swapTextFaint); inner() } }
                        )
                    } else {
                        Text(amount.ifEmpty { "0" }, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = swapText)
                    }
                    fiat?.let { Text(it, fontSize = 12.sp, color = swapTextDim) }
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
    valueColor: Color = swapText,
    chevron: Boolean = false,
    showIcon: Boolean = true
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showIcon) { Icon(icon, null, tint = swapTextDim, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)) }
        Text(label, fontSize = 13.sp, color = swapTextDim)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
        if (chevron) { Spacer(Modifier.width(4.dp)); Icon(Icons.Default.ChevronRight, null, tint = swapTextFaint, modifier = Modifier.size(16.dp)) }
    }
}

/** Barre de navigation sombre (prototype) : Accueil / Recevoir / Swap / Envoyer / Portefeuille. */
@Composable
private fun SwapBottomNav(navController: NavHostController) {
    Surface(color = swapCard, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            /*
             * Mêmes options que VaultExBottomBar — cette barre en est un
             * doublon local et n'en avait aucune.
             *
             * Un `navigate()` nu EMPILE la destination. Chaque passage d'un
             * onglet à l'autre ajoutait donc une entrée : le bouton retour
             * du téléphone repassait par tous les onglets visités au lieu de
             * ramener au tableau de bord, et la pile grossissait
             * indéfiniment.
             *
             * popUpTo(DASHBOARD) ramène au tableau de bord avant d'ouvrir
             * l'onglet ; launchSingleTop évite d'empiler deux fois le même ;
             * saveState/restoreState conservent la position de défilement
             * d'un onglet à l'autre.
             */
            fun tab(route: String) = navController.navigate(route) {
                popUpTo(Routes.DASHBOARD) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            NavItem(Icons.Default.Home, "Accueil", false) { tab(Routes.DASHBOARD) }
            NavItem(Icons.Outlined.CallReceived, "Recevoir", false) { tab(Routes.RECEIVE) }
            NavItem(Icons.Outlined.SwapHoriz, "Swap", true) { }
            NavItem(Icons.Outlined.CallMade, "Envoyer", false) { tab(Routes.SEND) }
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val col = if (selected) SwapPurple else swapTextDim
    Column(
        Modifier.clip(RoundedCornerShape(10.dp)).clickable { onClick() }.padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = col, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 10.sp, color = col, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

/* ─────────────── Feuille « Choisir une crypto » (maquette) ─────────────── */

/** Nom complet d'un actif (ligne du sélecteur). */
private fun swapFullName(key: String): String = when (SwapViewModel.assetOf(key).base.uppercase()) {
    "BTC" -> "Bitcoin"; "ETH" -> "Ethereum"; "BNB" -> "BNB Chain"; "SOL" -> "Solana"
    "TRX" -> "Tron"; "USDT" -> "Tether USD"; "USDC" -> "USD Coin"; "DAI" -> "Dai Stablecoin"
    "LINK" -> "Chainlink"; "SHIB" -> "Shiba Inu"; "PEPE" -> "Pepe"; "UNI" -> "Uniswap"
    "AAVE" -> "Aave"; "WBTC" -> "Wrapped Bitcoin"; "CAKE" -> "PancakeSwap"
    else -> SwapViewModel.assetOf(key).base
}

/** Montant du sélecteur : 8 décimales pour les natifs, 2 pour les gros soldes. */
private fun pickerAmount(v: Double): String =
    if (v <= 0.0) "0.00"
    else java.math.BigDecimal.valueOf(v).setScale(if (v >= 1.0) 2 else 8, java.math.RoundingMode.DOWN)
        .stripTrailingZeros().toPlainString()

private fun pickerXof(v: Double): String =
    java.text.NumberFormat.getNumberInstance(com.vaultex.core.session.LocaleManager.appLocale()).format(v.toLong())

/** Couleur du badge réseau (TRC20 violet, ERC20 bleu, BEP20 jaune). */
private fun badgeColor(badge: String): Color = when (badge.uppercase()) {
    "TRC20" -> SwapPurple
    "ERC20" -> Color(0xFF3B82F6)
    "BEP20" -> Color(0xFFF5B301)
    else -> Color(0xFF3B82F6)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TokenPickerSheet(
    tokens: List<String>,
    current: String,
    balanceInfo: (String) -> Pair<Double, Double>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = tokens.filter { key ->
        val a = SwapViewModel.assetOf(key)
        query.isBlank() || a.base.contains(query, true) ||
            swapFullName(key).contains(query, true) || a.badge.contains(query, true)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = swapBg) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp)) {
            // Titre + fermer
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(com.vaultex.R.string.swap_pick_crypto),
                    fontWeight = FontWeight.Bold, fontSize = 18.sp, color = swapText, modifier = Modifier.weight(1f))
                Surface(onClick = onDismiss, shape = CircleShape, color = swapCardAlt, modifier = Modifier.size(34.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Close, stringResource(com.vaultex.R.string.close), tint = swapText, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Recherche
            Surface(shape = RoundedCornerShape(14.dp), color = swapCard,
                border = BorderStroke(1.dp, swapBorder), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, tint = swapTextDim, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = TextStyle(fontSize = 14.sp, color = swapText),
                        cursorBrush = SolidColor(SwapPurple),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (query.isEmpty()) Text(stringResource(com.vaultex.R.string.swap_search_crypto), fontSize = 14.sp, color = swapTextFaint)
                            inner()
                        }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // Liste des actifs
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.weight(1f, fill = false).heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filtered.size) { i ->
                    val key = filtered[i]
                    val a = SwapViewModel.assetOf(key)
                    val selected = key.equals(current, ignoreCase = true)
                    val isTokenBadge = a.badge.uppercase() in listOf("TRC20", "ERC20", "BEP20")
                    val (bal, xof) = balanceInfo(key)
                    Surface(
                        onClick = { onSelect(key) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) SwapPurple.copy(alpha = 0.10f) else swapCard,
                        border = BorderStroke(if (selected) 1.2.dp else 1.dp, if (selected) SwapPurple else swapBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TokenLogo(key, 32)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.base, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = swapText)
                                Text(swapFullName(key), fontSize = 11.sp, color = swapTextDim)
                            }
                            // Solde + ≈ XOF (comme la maquette)
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    pickerAmount(bal),
                                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                    color = if (bal > 0.0) swapText else swapTextDim
                                )
                                Text("≈ " + pickerXof(xof) + " XOF", fontSize = 10.sp, color = swapTextDim)
                            }
                            Spacer(Modifier.width(8.dp))
                            if (isTokenBadge) {
                                val bc = badgeColor(a.badge)
                                Surface(shape = RoundedCornerShape(6.dp), color = bc.copy(alpha = 0.14f)) {
                                    Text(a.badge, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = bc,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                                }
                                Spacer(Modifier.width(6.dp))
                            }
                            if (selected) {
                                Box(Modifier.size(19.dp).clip(CircleShape).background(SwapPurple), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(11.dp))
                                }
                            } else {
                                Icon(Icons.Default.ChevronRight, null, tint = swapTextFaint, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Bandeau « Transactions sécurisées »
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SwapPurple.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, SwapPurple.copy(alpha = 0.30f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(SwapPurple.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Shield, null, tint = SwapPurple, modifier = Modifier.size(17.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(stringResource(com.vaultex.R.string.swap_secure_title), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = swapText)
                        Text(stringResource(com.vaultex.R.string.swap_secure_desc), fontSize = 10.sp, color = swapTextDim, lineHeight = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}
