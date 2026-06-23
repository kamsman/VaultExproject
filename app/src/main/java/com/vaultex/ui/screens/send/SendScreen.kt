package com.vaultex.ui.screens.send

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

    // Success dialog
    if (state.txHash != null) {
        AlertDialog(
            onDismissRequest = { viewModel.reset(); navController.popBackStack() },
            icon = { Icon(Icons.Default.CheckCircle, null, tint = VaultExColors.Success) },
            title = { Text(stringResource(R.string.send_tx_sent_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.send_tx_broadcast), fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.send_tx_hash_label), fontSize = 12.sp, color = TextSecondary)
                    Text(
                        state.txHash!!.take(20) + "…",
                        fontSize = 11.sp,
                        color = AccentBlue,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.reset(); navController.popBackStack() }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    // Écran de confirmation : récap clair (réseau / adresse / montant / frais
    // / total) AVANT la ré-authentification et l'envoi.
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            icon = { Icon(Icons.Default.Send, null, tint = AccentBlue) },
            title = { Text(stringResource(R.string.send_confirm_recap_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ConfirmRow(stringResource(R.string.send_confirm_network), netFull)
                    ConfirmRow(stringResource(R.string.send_recipient_label), shortenAddress(state.toAddress))
                    ConfirmRow(stringResource(R.string.amount), "${state.amount} $coinShort")
                    ConfirmRow(stringResource(R.string.send_fee_estimate_label).trimEnd(' ', ':'), feeEstimate.ifEmpty { "—" })
                    HorizontalDivider(color = BorderColor)
                    ConfirmRow(
                        stringResource(R.string.send_confirm_total),
                        "${state.amount} $coinShort" + if (feeEstimate.isNotEmpty()) " + ${feeEstimate}" else "",
                        emphasize = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirm = false
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        // m-05 : ré-authentification OBLIGATOIRE avant un envoi.
                        // Biométrie si disponible, sinon code de verrouillage de
                        // l'appareil. On n'envoie sans auth que si l'appareil n'a
                        // aucun verrouillage sécurisé (cas impossible à améliorer).
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
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                ) {
                    Text(stringResource(R.string.send_confirm_cta))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Overlay de chargement pendant l'envoi : loader « points en cercle ».
    if (state.isLoading) {
        Dialog(
            onDismissRequest = { },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(shape = RoundedCornerShape(20.dp), color = SplashNavyBottom) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    DotsCircleLoader(size = 76.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.send_in_progress),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
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
