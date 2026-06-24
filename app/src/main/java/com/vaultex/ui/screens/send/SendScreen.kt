package com.vaultex.ui.screens.send

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vaultex.ui.components.DotsCircleLoader
import com.vaultex.ui.theme.SplashNavyBottom
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vaultex.R
import com.vaultex.core.security.BiometricHelper
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.AccentGreen
import com.vaultex.ui.theme.AccentRed
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.BgTertiary
import com.vaultex.ui.theme.BorderColor
import com.vaultex.ui.theme.Surface as SurfaceColor
import com.vaultex.ui.theme.SurfaceLight
import com.vaultex.ui.theme.TextMuted
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary
import com.vaultex.ui.theme.VaultExColors
import com.vaultex.ui.viewmodel.CustomTokenLite
import com.vaultex.ui.viewmodel.SendViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(navController: NavController) {
    val viewModel: SendViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val customTokens by viewModel.customTokens.collectAsState()
    val pendingTxs by viewModel.pendingTxs.collectAsState()

    // P5 : préremplissage depuis un deep link déjà validé (chaîne + adresse).
    // Sinon, pré-sélection de la chaîne depuis la page d'une crypto (#4).
    LaunchedEffect(Unit) {
        val target = com.vaultex.core.session.DeepLinkBuffer.consume()
        if (target != null) {
            viewModel.setChain(target.chain)
            viewModel.setToAddress(target.address)
        } else {
            com.vaultex.core.session.TokenSelectionBuffer.consume()?.let { sym ->
                // Chaînes natives OU symbole d'un token personnalisé enregistré.
                if (sym in listOf("BTC", "ETH", "BNB", "TRX", "SOL", "USDT", "USDT-ETH", "USDT-BNB") ||
                    customTokens.any { it.symbol == sym }
                ) {
                    viewModel.setChain(sym)
                }
            }
        }
    }

    // Adresse renvoyée par le scanner QR
    val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
    LaunchedEffect(savedStateHandle) {
        savedStateHandle?.getStateFlow<String?>(com.vaultex.ui.screens.scanner.SCANNED_ADDRESS_KEY, null)
            ?.collect { scanned ->
                if (!scanned.isNullOrBlank()) {
                    viewModel.setToAddress(scanned)
                    savedStateHandle.remove<String>(com.vaultex.ui.screens.scanner.SCANNED_ADDRESS_KEY)
                }
            }
    }
    val context = LocalContext.current as FragmentActivity
    val biometricHelper = remember { BiometricHelper(context) }
    val haptic = LocalHapticFeedback.current
    var showConfirm by remember { mutableStateOf(false) }

    // Retour haptique de confirmation quand la transaction part (ou est mise en file)
    LaunchedEffect(state.txHash) {
        if (state.txHash != null) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    LaunchedEffect(state.queued) {
        if (state.queued) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    // Regroupement par RÉSEAU (blockchain) : chaque réseau liste toutes ses
    // monnaies (natives + tokens personnalisés ajoutés par contrat).
    val networks = buildSendNetworks(customTokens)
    val selectedNetworkKey = networks.firstOrNull { state.selectedChain in it.coins }?.key
        ?: networks.first().key

    // ─ Données d'affichage « pro » (titre, conversions fiat, récapitulatif) ─
    val coinShort = coinLabel(state.selectedChain)                          // ex: USDT
    val coinTitle = coinTitleOf(state.selectedChain, state.customToken)     // ex: USDT (TRC20)
    val netFull = networkFullLabel(state.selectedChain, state.customToken)  // ex: TRC20 · Tron
    val isNative = state.selectedChain in listOf("BTC", "ETH", "BNB", "SOL", "TRX")

    val price = state.priceSelected
    val amountNum = state.amount.replace(",", ".").toDoubleOrNull() ?: 0.0
    val availNum = state.availableBalance?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
    val feeNum = state.feeNativeAmount ?: 0.0

    fun fiat(v: Double): String? =
        if (v > 0.0) com.vaultex.core.util.CurrencyFormat.format(v, state.currency) else null
    val availFiat = if (price > 0.0) fiat(availNum * price) else null
    val amountFiat = if (price > 0.0) fiat(amountNum * price) else null
    val feeFiat = if (state.priceNative > 0.0) fiat(feeNum * state.priceNative) else null
    // Total : pour une monnaie native, montant + frais (même actif) ; pour un
    // token, les frais sont payés en natif → le total dans le token = montant.
    val totalToken = if (isNative) amountNum + feeNum else amountNum
    val totalFiatValue = if (isNative) (amountNum + feeNum) * price else amountNum * price
    val totalFiat = if (price > 0.0) fiat(totalFiatValue) else null

    // Frais réseau réel (gas live), calculé par chaîne dans le ViewModel.
    val feeEstimate = state.estimatedFee

    // Hors-ligne : transaction mise en file
    if (state.queued) {
        AlertDialog(
            onDismissRequest = { viewModel.reset(); navController.popBackStack() },
            icon = { Icon(Icons.Default.Schedule, null, tint = AccentBlue) },
            title = { Text(stringResource(R.string.send_queued_title)) },
            text = { Text(stringResource(R.string.send_queued_body), fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = { viewModel.reset(); navController.popBackStack() }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    // Données partagées par les écrans Confirmation / Traitement / Succès.
    val detail = SendDetail(
        coinShort = coinShort,
        coinName = coinFullName(state.selectedChain, state.customToken),
        toAddress = state.toAddress,
        netFull = netFull,
        amount = state.amount.ifEmpty { "0" },
        amountFiat = amountFiat,
        feeNative = feeEstimate,
        feeFiat = feeFiat,
        totalToken = formatTokenAmount(totalToken) + " " + coinShort,
        totalFiat = totalFiat
    )

    // ── ÉCRAN EN ATTENTE / SUCCÈS (plein écran) ──
    if (state.txHash != null) {
        val hash = state.txHash!!
        val pending = pendingTxs.firstOrNull { it.hash == hash }
        // Tant que la transaction n'est pas confirmée → écran « En attente X/Y ».
        if (pending != null && !pending.confirmed) {
            SendPendingScreen(
                detail = detail,
                confirmations = pending.confirmations,
                target = pending.target,
                txHash = hash,
                explorerName = explorerName(state.selectedChain, state.customToken),
                explorerUrl = explorerUrl(state.selectedChain, state.customToken, hash),
                onDone = { viewModel.reset(); navController.popBackStack() }
            )
            return
        }
        SendSuccessScreen(
            detail = detail,
            txHash = hash,
            explorerName = explorerName(state.selectedChain, state.customToken),
            explorerUrl = explorerUrl(state.selectedChain, state.customToken, hash),
            onShare = { shareReceipt(context, detail, hash) },
            onDone = { viewModel.reset(); navController.popBackStack() }
        )
        return
    }

    // ── ÉCRAN TRAITEMENT (diffusion réseau) ──
    if (state.isLoading) {
        SendProcessingScreen(detail)
        return
    }

    // ── ÉCRAN CONFIRMATION (plein écran) ──
    if (showConfirm) {
        SendConfirmScreen(
            detail = detail,
            onCancel = { showConfirm = false },
            onConfirm = {
                showConfirm = false
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                // m-05 : ré-authentification OBLIGATOIRE avant un envoi.
                val bioStatus = biometricHelper.checkAvailability()
                if (bioStatus == BiometricHelper.BiometricStatus.AVAILABLE ||
                    biometricHelper.canUseDeviceCredential()
                ) {
                    biometricHelper.authenticateStrongOrCredential(
                        title = context.getString(R.string.send_biometric_title),
                        subtitle = context.getString(
                            R.string.send_biometric_subtitle,
                            state.amount, state.selectedChain, state.toAddress.take(12)
                        ),
                        onSuccess = { viewModel.send() },
                        onError = { _, _ -> /* annulé/erreur — reste sur l'écran */ }
                    )
                } else {
                    viewModel.send()
                }
            }
        )
        return
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("${stringResource(R.string.send_title)} $coinTitle", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // 1) Sélecteur de RÉSEAU (blockchain). 2) Monnaies de ce réseau.
            // L'utilisateur voit ainsi toutes les monnaies liées à chaque chaîne.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.send_network_label),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                ) {
                    networks.forEach { net ->
                        SendTokenChip(
                            label = net.label,
                            selected = net.key == selectedNetworkKey,
                            // Sélectionner un réseau choisit sa première monnaie.
                            onClick = { net.coins.firstOrNull()?.let { viewModel.setChain(it) } }
                        )
                    }
                }

                val coins = networks.firstOrNull { it.key == selectedNetworkKey }?.coins ?: emptyList()
                Text(
                    stringResource(R.string.send_coin_label),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                ) {
                    coins.forEach { coin ->
                        SendTokenChip(
                            label = coinLabel(coin),
                            selected = state.selectedChain == coin,
                            onClick = { viewModel.setChain(coin) }
                        )
                    }
                }
            }

            // ─── Carte « Solde disponible » (montant + équivalent fiat + icône) ─
            SendCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.send_available_balance), fontSize = 12.sp, color = TextSecondary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            (state.availableBalance ?: "—") + " " + coinShort,
                            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary
                        )
                        if (availFiat != null) {
                            Text("≈ $availFiat", fontSize = 13.sp, color = TextSecondary)
                        }
                    }
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(AccentBlue.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(coinShort.take(2), color = AccentBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        coil.compose.AsyncImage(
                            model = com.vaultex.ui.components.CryptoIcon.url(coinShort),
                            contentDescription = coinShort,
                            modifier = Modifier.size(46.dp).clip(CircleShape)
                        )
                    }
                }
            }

            // Recipient card
            SendCard {
                Text(
                    stringResource(R.string.send_recipient_label),
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SendField(
                        value = state.toAddress,
                        onValueChange = { viewModel.setToAddress(it) },
                        placeholder = when (state.selectedChain) {
                            "BTC"          -> stringResource(R.string.send_address_placeholder_btc)
                            "ETH", "BNB"   -> "0x..."
                            "TRX", "USDT"  -> "T..."
                            "SOL"          -> stringResource(R.string.send_address_placeholder_sol)
                            else           -> stringResource(R.string.send_address_placeholder_generic, state.selectedChain)
                        },
                        isError = state.toAddress.isNotEmpty() && !state.isAddressValid,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgTertiary)
                            .clickable { navController.navigate(Routes.SCANNER) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.qr_label),
                            color = AccentBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
                if (state.toAddress.isNotEmpty() && !state.isAddressValid) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.send_invalid_address, state.selectedChain),
                        fontSize = 12.sp,
                        color = AccentRed
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "📒 " + stringResource(R.string.address_book),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentBlue,
                    modifier = Modifier.clickable { navController.navigate(Routes.ADDRESS_BOOK) }
                )
            }

            // Amount card
            SendCard {
                Text(stringResource(R.string.amount), fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                SendField(
                    value = state.amount,
                    onValueChange = { viewModel.setAmount(it) },
                    placeholder = "0.00 $coinShort",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.fillMaxWidth()
                )
                if (amountFiat != null) {
                    Spacer(Modifier.height(6.dp))
                    Text("≈ $amountFiat", fontSize = 12.sp, color = TextSecondary)
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        state.availableBalance
                            ?.let { stringResource(R.string.send_balance_label, "$it $coinShort") }
                            ?: stringResource(R.string.send_balance_label, "—"),
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Text(
                        stringResource(R.string.max_label),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentBlue,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewModel.onMaxClicked() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Avertissement montant sous le minimum réseau
            if (state.dustWarning != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = androidx.compose.ui.graphics.Color(0xFFFFF3CD),
                    border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFE6AC00)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, null, tint = androidx.compose.ui.graphics.Color(0xFFB07800), modifier = Modifier.size(18.dp))
                        Text(
                            stringResource(R.string.send_dust_warning, state.dustWarning!!),
                            fontSize = 13.sp,
                            color = androidx.compose.ui.graphics.Color(0xFF7A5200)
                        )
                    }
                }
            }

            // Carte récapitulative : Réseau / Frais réseau / Tu vas envoyer / Total.
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SurfaceLight,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SummaryRow(stringResource(R.string.send_summary_network), netFull)
                    SummaryRow(
                        stringResource(R.string.send_summary_fee),
                        feeEstimate.ifEmpty { "…" },
                        sub = feeFiat?.let { "≈ $it" }
                    )
                    SummaryRow(
                        stringResource(R.string.send_summary_you_send),
                        "${state.amount.ifEmpty { "0" }} $coinShort",
                        sub = amountFiat?.let { "≈ $it" }
                    )
                    HorizontalDivider(color = BorderColor)
                    SummaryRow(
                        stringResource(R.string.send_summary_total),
                        formatTokenAmount(totalToken) + " $coinShort",
                        sub = totalFiat?.let { "≈ $it" },
                        emphasize = true
                    )
                    if (!isNative) {
                        Text(
                            stringResource(R.string.send_summary_fee_note, nativeFeeUnit(state.selectedChain, state.customToken)),
                            fontSize = 11.sp, color = TextSecondary
                        )
                    }
                }
            }

            // Error message
            if (state.error != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AccentRed.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, null, tint = AccentRed, modifier = Modifier.size(18.dp))
                        Text(state.error!!, fontSize = 13.sp, color = AccentRed)
                    }
                }
            }

            Spacer(Modifier.weight(1f, fill = false))
            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    // Étape de confirmation explicite (récap) avant la ré-auth.
                    showConfirm = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                enabled = state.isAddressValid && state.amount.isNotEmpty() && !state.isLoading,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentBlue,
                    contentColor = Color.White
                )
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.continue_btn), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
internal fun SendTokenChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) AccentBlue else BgTertiary
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else TextPrimary
        )
    }
}

@Composable
internal fun SendCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
internal fun SendField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceLight)
            .border(
                1.dp,
                if (isError) AccentRed else BorderColor,
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
        singleLine = true,
        enabled = enabled,
        textStyle = TextStyle(fontSize = 14.sp, color = TextPrimary),
        cursorBrush = SolidColor(AccentBlue),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(placeholder, fontSize = 14.sp, color = TextMuted)
                }
                inner()
            }
        }
    )
}

@Composable
private fun ConfirmRow(label: String, value: String, emphasize: Boolean = false) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = TextSecondary)
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.SemiBold,
            color = if (emphasize) AccentBlue else TextPrimary
        )
    }
}

private fun shortenAddress(a: String): String =
    if (a.length <= 14) a else "${a.take(8)}…${a.takeLast(6)}"

@Composable
private fun SummaryRow(label: String, value: String, sub: String? = null, emphasize: Boolean = false) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = TextSecondary)
        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
            Text(
                value,
                fontSize = if (emphasize) 15.sp else 13.sp,
                fontWeight = if (emphasize) FontWeight.Bold else FontWeight.SemiBold,
                color = if (emphasize) AccentBlue else TextPrimary
            )
            if (sub != null) Text(sub, fontSize = 11.sp, color = TextSecondary)
        }
    }
}

/** Titre de la monnaie avec sa norme : « USDT (TRC20) », « SHIB (ERC20) »… */
private fun coinTitleOf(chain: String, custom: CustomTokenLite?): String = when {
    custom != null -> "${custom.symbol} (${if (custom.blockchain == "BNB") "BEP20" else "ERC20"})"
    chain == "USDT" -> "USDT (TRC20)"
    chain == "USDT-ETH" -> "USDT (ERC20)"
    chain == "USDT-BNB" -> "USDT (BEP20)"
    else -> chain
}

/** Réseau complet : « TRC20 · Tron », « ERC20 · Ethereum », « Bitcoin »… */
private fun networkFullLabel(chain: String, custom: CustomTokenLite?): String = when {
    custom != null -> if (custom.blockchain == "BNB") "BEP20 · BNB Chain" else "ERC20 · Ethereum"
    chain == "BTC" -> "Bitcoin"
    chain == "ETH" -> "Ethereum"
    chain == "BNB" -> "BNB Chain"
    chain == "SOL" -> "Solana"
    chain == "TRX" -> "Tron"
    chain == "USDT" -> "TRC20 · Tron"
    chain == "USDT-ETH" -> "ERC20 · Ethereum"
    chain == "USDT-BNB" -> "BEP20 · BNB Chain"
    else -> chain
}

/** Monnaie dans laquelle les frais (gas) sont payés pour un token. */
private fun nativeFeeUnit(chain: String, custom: CustomTokenLite?): String = when {
    custom != null -> if (custom.blockchain == "BNB") "BNB" else "ETH"
    chain == "USDT-ETH" -> "ETH"
    chain == "USDT-BNB" -> "BNB"
    chain == "USDT" -> "TRX"
    else -> chain
}

/** Affiche un montant de token sans zéros superflus (max 8 décimales). */
private fun formatTokenAmount(v: Double): String =
    java.math.BigDecimal.valueOf(v)
        .setScale(8, java.math.RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()

/** Un réseau (blockchain) et toutes les monnaies qui lui sont liées. */
private data class SendNetworkUi(val key: String, val label: String, val coins: List<String>)

/**
 * Construit la liste des réseaux pour l'écran Envoyer. Chaque réseau regroupe
 * ses monnaies natives, ses USDT, et les tokens personnalisés ajoutés par
 * contrat sur cette chaîne (ETH ou BNB).
 */
private fun buildSendNetworks(custom: List<CustomTokenLite>): List<SendNetworkUi> = listOf(
    SendNetworkUi("BTC", "Bitcoin", listOf("BTC")),
    SendNetworkUi(
        "ETH", "Ethereum",
        listOf("ETH", "USDT-ETH") + custom.filter { it.blockchain == "ETH" }.map { it.symbol }
    ),
    SendNetworkUi(
        "BNB", "BNB Chain",
        listOf("BNB", "USDT-BNB") + custom.filter { it.blockchain == "BNB" }.map { it.symbol }
    ),
    SendNetworkUi("SOL", "Solana", listOf("SOL")),
    SendNetworkUi("TRX", "Tron", listOf("TRX", "USDT"))
)

/** Étiquette courte d'une monnaie (le réseau étant déjà affiché au-dessus). */
private fun coinLabel(sym: String): String = when (sym) {
    "USDT-ETH", "USDT-BNB" -> "USDT"
    else -> sym
}

// ══════════════════════════════════════════════════════════════════════
//  Flux d'envoi « pro » : Confirmation → Traitement → Succès (plein écran)
// ══════════════════════════════════════════════════════════════════════

/** Détails d'une transaction d'envoi, partagés par les 3 écrans de statut. */
internal data class SendDetail(
    val coinShort: String,
    val coinName: String,
    val toAddress: String,
    val netFull: String,
    val amount: String,
    val amountFiat: String?,
    val feeNative: String,
    val feeFiat: String?,
    val totalToken: String,
    val totalFiat: String?
)

private fun coinFullName(chain: String, custom: CustomTokenLite?): String = when {
    custom != null -> custom.symbol
    chain == "USDT" || chain == "USDT-ETH" || chain == "USDT-BNB" -> "Tether USD"
    chain == "BTC" -> "Bitcoin"
    chain == "ETH" -> "Ethereum"
    chain == "BNB" -> "BNB"
    chain == "SOL" -> "Solana"
    chain == "TRX" -> "Tron"
    else -> chain
}

private fun coinColorHex(coinShort: String): String = when (coinShort) {
    "USDT" -> "#26A17B"
    "BTC" -> "#F7931A"
    "ETH" -> "#627EEA"
    "BNB" -> "#F0B90B"
    "SOL" -> "#9945FF"
    "TRX" -> "#FF060A"
    else -> "#3B82F6"
}

private fun explorerName(chain: String, custom: CustomTokenLite?): String {
    val evm = custom?.blockchain
    return when {
        evm == "BNB" || chain == "BNB" || chain == "USDT-BNB" -> "BscScan"
        evm == "ETH" || chain == "ETH" || chain == "USDT-ETH" -> "Etherscan"
        chain == "BTC" -> "Blockstream"
        chain == "SOL" -> "Solscan"
        chain == "TRX" || chain == "USDT" -> "Tronscan"
        else -> "Explorer"
    }
}

private fun explorerUrl(chain: String, custom: CustomTokenLite?, hash: String): String {
    val evm = custom?.blockchain
    val base = when {
        evm == "BNB" || chain == "BNB" || chain == "USDT-BNB" -> "https://bscscan.com/tx/"
        evm == "ETH" || chain == "ETH" || chain == "USDT-ETH" -> "https://etherscan.io/tx/"
        chain == "BTC" -> "https://blockstream.info/tx/"
        chain == "SOL" -> "https://solscan.io/tx/"
        chain == "TRX" || chain == "USDT" -> "https://tronscan.org/#/transaction/"
        else -> "https://etherscan.io/tx/"
    }
    return base + hash
}

private fun shareReceipt(context: android.content.Context, d: SendDetail, hash: String) {
    val text = buildString {
        appendLine("VaultEx — Reçu de transaction")
        appendLine("Montant : ${d.amount} ${d.coinShort}")
        appendLine("Destinataire : ${d.toAddress}")
        appendLine("Réseau : ${d.netFull}")
        appendLine("Frais : ${d.feeNative}")
        appendLine("Hash : $hash")
    }
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
    }, null))
}

private fun shorten(a: String): String =
    if (a.length <= 18) a else "${a.take(10)}…${a.takeLast(6)}"

/** Carte récap (icône monnaie + montant, Destinataire, Réseau, Frais, [Total]). */
@Composable
private fun SendDetailCard(detail: SendDetail, showTotal: Boolean, onCopyAddress: (() -> Unit)? = null) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(4.dp)) {
            // Ligne monnaie + montant
            Row(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape)
                        .background(runCatching { Color(android.graphics.Color.parseColor(coinColorHex(detail.coinShort))) }.getOrDefault(AccentBlue)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(detail.coinShort.take(2), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    coil.compose.AsyncImage(
                        model = com.vaultex.ui.components.CryptoIcon.url(detail.coinShort),
                        contentDescription = detail.coinShort,
                        modifier = Modifier.size(42.dp).clip(CircleShape)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(detail.coinShort, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                    Text(detail.coinName, fontSize = 12.sp, color = TextSecondary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${detail.amount} ${detail.coinShort}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                    detail.amountFiat?.let { Text(it, fontSize = 11.sp, color = TextSecondary) }
                }
            }
            HorizontalDivider(color = BorderColor)
            DetailLine(Icons.Default.Send, stringResource(R.string.send_recipient_label), shorten(detail.toAddress),
                onCopy = onCopyAddress)
            HorizontalDivider(color = BorderColor)
            DetailLine(Icons.Default.Hub, stringResource(R.string.send_summary_network), detail.netFull)
            HorizontalDivider(color = BorderColor)
            DetailLine(Icons.Default.CreditCard, stringResource(R.string.send_summary_fee), detail.feeNative.ifEmpty { "…" }, sub = detail.feeFiat)
            if (showTotal) {
                HorizontalDivider(color = BorderColor)
                DetailLine(Icons.Default.AccountBalanceWallet, stringResource(R.string.send_summary_total), detail.totalToken, sub = detail.totalFiat, emphasize = true)
            }
        }
    }
}

@Composable
private fun DetailLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    sub: String? = null,
    emphasize: Boolean = false,
    onCopy: (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(BgTertiary), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(value, fontSize = 14.sp, fontWeight = if (emphasize) FontWeight.Bold else FontWeight.SemiBold, color = if (emphasize) AccentBlue else TextPrimary)
            sub?.let { Text(it, fontSize = 11.sp, color = TextSecondary) }
        }
        if (onCopy != null) {
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Default.ContentCopy, null, tint = AccentBlue,
                modifier = Modifier.size(20.dp).clickable { onCopy() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SendStatusScaffold(title: String, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgPrimary)
            )
        },
        containerColor = BgPrimary
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content
        )
    }
}

@Composable
private fun StatusHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, ringColor: Color, title: String, subtitle: String, titleColor: Color) {
    Box(Modifier.padding(top = 8.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(120.dp).clip(CircleShape).background(ringColor.copy(alpha = 0.12f)))
        Box(Modifier.size(88.dp).clip(CircleShape).background(ringColor), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(46.dp))
        }
    }
    Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = titleColor)
    Text(subtitle, fontSize = 14.sp, color = TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}

@Composable
internal fun SendConfirmScreen(detail: SendDetail, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    SendStatusScaffold(stringResource(R.string.send_confirm_title)) {
        SendDetailCard(detail, showTotal = true, onCopyAddress = {
            clipboard.setText(androidx.compose.ui.text.AnnotatedString(detail.toAddress))
        })
        // Avertissement irréversible
        Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFFF7E6), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WarningAmber, null, tint = Color(0xFFE6AC00), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.send_confirm_irreversible), fontSize = 12.sp, color = Color(0xFF7A5200))
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, BorderColor)
            ) { Text(stringResource(R.string.cancel), color = TextPrimary, fontWeight = FontWeight.SemiBold) }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
            ) { Text(stringResource(R.string.send_confirm_cta), color = Color.White, fontWeight = FontWeight.Bold) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.VerifiedUser, null, tint = AccentGreen, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.send_confirm_secured), fontSize = 11.sp, color = TextSecondary)
        }
    }
}

@Composable
internal fun SendProcessingScreen(detail: SendDetail) {
    SendStatusScaffold(stringResource(R.string.send_processing_appbar, detail.coinShort)) {
        StatusHeader(
            Icons.Default.HourglassEmpty, AccentBlue,
            stringResource(R.string.send_processing_title),
            stringResource(R.string.send_processing_subtitle),
            TextPrimary
        )
        SendDetailCard(detail, showTotal = true)
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = AccentBlue, trackColor = BgTertiary
        )
        Text(stringResource(R.string.send_processing_broadcasting), fontSize = 13.sp, color = TextSecondary)
        Surface(shape = RoundedCornerShape(12.dp), color = AccentBlue.copy(alpha = 0.08f), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VerifiedUser, null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(stringResource(R.string.send_processing_dont_close_title), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(stringResource(R.string.send_processing_dont_close_body), fontSize = 12.sp, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
internal fun SendPendingScreen(
    detail: SendDetail,
    confirmations: Int,
    target: Int,
    txHash: String,
    explorerName: String,
    explorerUrl: String,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val orange = Color(0xFFF59E0B)
    SendStatusScaffold(stringResource(R.string.send_processing_appbar, detail.coinShort)) {
        StatusHeader(
            Icons.Default.Schedule, orange,
            stringResource(R.string.send_pending_title),
            stringResource(R.string.send_pending_subtitle, detail.netFull),
            orange
        )
        SendDetailCard(detail, showTotal = true)
        Surface(shape = RoundedCornerShape(12.dp), color = orange.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, null, tint = orange, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(stringResource(R.string.send_pending_waiting_title), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(stringResource(R.string.send_pending_waiting_body), fontSize = 12.sp, color = TextSecondary)
                }
            }
        }
        // Compteur de confirmations X / Y
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.send_pending_confirmations), fontSize = 13.sp, color = TextSecondary)
                Text("$confirmations / $target", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = if (target > 0) (confirmations.toFloat() / target).coerceIn(0f, 1f) else 0f,
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = orange, trackColor = BgTertiary
            )
        }
        // Transaction ID + copie
        Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.send_success_txid), fontSize = 12.sp, color = TextSecondary)
                    Text(shorten(txHash), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                }
                Icon(Icons.Default.ContentCopy, null, tint = AccentBlue, modifier = Modifier.size(20.dp).clickable {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(txHash))
                })
            }
        }
        OutlinedButton(
            onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(explorerUrl))) } },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, AccentBlue),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue)
        ) {
            Text(stringResource(R.string.send_success_view_on, explorerName), fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp))
        }
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
        ) { Text(stringResource(R.string.send_pending_back_dashboard), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        Text(stringResource(R.string.send_pending_keep_tracking), fontSize = 11.sp, color = TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
internal fun SendSuccessScreen(
    detail: SendDetail,
    txHash: String,
    explorerName: String,
    explorerUrl: String,
    onShare: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    SendStatusScaffold(stringResource(R.string.send_processing_appbar, detail.coinShort)) {
        StatusHeader(
            Icons.Default.CheckCircle, AccentGreen,
            stringResource(R.string.send_success_title),
            stringResource(R.string.send_success_subtitle),
            AccentGreen
        )
        SendDetailCard(detail, showTotal = false, onCopyAddress = {
            clipboard.setText(androidx.compose.ui.text.AnnotatedString(detail.toAddress))
        })
        // Statut : diffusée
        Surface(shape = RoundedCornerShape(12.dp), color = AccentGreen.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.send_success_status_label), fontSize = 13.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.send_success_status_value), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Default.CheckCircle, null, tint = AccentGreen, modifier = Modifier.size(18.dp))
            }
        }
        // Transaction ID + copie
        Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.send_success_txid), fontSize = 12.sp, color = TextSecondary)
                    Text(shorten(txHash), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                }
                Icon(Icons.Default.ContentCopy, null, tint = AccentBlue, modifier = Modifier.size(20.dp).clickable {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(txHash))
                })
            }
        }
        OutlinedButton(
            onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(explorerUrl))) }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, AccentGreen),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGreen)
        ) {
            Text(stringResource(R.string.send_success_view_on, explorerName), fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp))
        }
        OutlinedButton(
            onClick = onShare,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
        ) {
            Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.send_success_share_receipt), fontWeight = FontWeight.SemiBold)
        }
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
        ) { Text(stringResource(R.string.send_success_done), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
    }
}
