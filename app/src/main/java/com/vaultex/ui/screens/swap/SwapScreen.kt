package com.vaultex.ui.screens.swap

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
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
import com.vaultex.ui.components.VaultExBottomBar
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

    // Retour haptique quand l'échange est créé (adresse de dépôt disponible)
    LaunchedEffect(state.payinAddress) {
        if (state.payinAddress != null) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val tokens = listOf("ETH", "BNB", "USDT", "BTC", "SOL", "TRX")

    // Résultat du swap : afficher la payin address
    if (state.payinAddress != null) {
        AlertDialog(
            onDismissRequest = { viewModel.resetSwap() },
            icon = { Icon(Icons.Default.SwapHoriz, null, tint = AccentBlue) },
            title = { Text(stringResource(R.string.swap_created_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.swap_send_instruction, state.fromAmount, state.fromToken), fontSize = 14.sp)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BgTertiary
                    ) {
                        Text(
                            state.payinAddress!!,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp),
                            color = AccentBlue,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(stringResource(R.string.swap_id_label, state.swapId?.take(16) ?: ""), fontSize = 11.sp, color = TextSecondary)
                }
            },
            confirmButton = {
                Button(onClick = {
                    clipboard.setText(AnnotatedString(state.payinAddress!!))
                }) { Text(stringResource(R.string.copy_address)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.resetSwap() }) { Text(stringResource(R.string.close)) }
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
                        viewModel.executeSwap()
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
                            stringResource(R.string.swap_exchange),
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Carte « De »
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceColor,
                border = BorderStroke(1.dp, BorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.label_from), fontSize = 12.sp, color = TextSecondary)
                    TokenPill(state.fromToken, tokens) { viewModel.setFromToken(it) }
                    AmountField(
                        value = state.fromAmount,
                        onValueChange = { viewModel.setFromAmount(it) },
                        enabled = true,
                        placeholder = "0.00"
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.balance_label, "—"),
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Text(
                            stringResource(R.string.max_label),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentBlue
                        )
                    }
                }
            }

            // Bouton d'inversion
            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                Surface(
                    onClick = { viewModel.swapTokens() },
                    shape = CircleShape,
                    color = SurfaceColor,
                    border = BorderStroke(1.dp, BorderColor),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.SwapVert,
                            contentDescription = stringResource(R.string.swap_invert_tokens),
                            tint = AccentBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Carte « Vers »
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceColor,
                border = BorderStroke(1.dp, BorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.swap_to_label), fontSize = 12.sp, color = TextSecondary)
                    TokenPill(state.toToken, tokens) { viewModel.setToToken(it) }
                    AmountField(
                        value = if (state.toAmount.isNotEmpty()) "≈ ${state.toAmount}" else "",
                        onValueChange = {},
                        enabled = false,
                        placeholder = "≈ 0.00"
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Récapitulatif taux / frais / route
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SurfaceColor,
                border = BorderStroke(1.dp, BorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                    val from = state.fromAmount.toDoubleOrNull()
                    val to = state.toAmount.toDoubleOrNull()
                    val rateValue = if (from != null && from > 0.0 && to != null && to > 0.0)
                        stringResource(
                            R.string.swap_rate_format,
                            state.fromToken,
                            String.format("%.2f", to / from),
                            state.toToken
                        )
                    else "—"
                    SummaryRow(stringResource(R.string.swap_rate), rateValue)
                    Divider(color = SurfaceLight, thickness = 1.dp)
                    // Frais réseau (gas) — inclus dans le devis ChangeNOW
                    SummaryRow(
                        stringResource(R.string.swap_network_fee),
                        stringResource(R.string.swap_network_included)
                    )
                    Divider(color = SurfaceLight, thickness = 1.dp)
                    // Frais de service VaultEx — distincts du réseau
                    SummaryRow(
                        stringResource(R.string.swap_fee_vaultex_label),
                        "${state.vaultexFeePercent}%"
                    )
                    Divider(color = SurfaceLight, thickness = 1.dp)
                    // Montant net reçu estimé
                    SummaryRow(
                        stringResource(R.string.swap_you_receive_est),
                        if (to != null && to > 0.0) "${String.format("%.6f", to)} ${state.toToken}" else "—",
                        valueColor = TextPrimary
                    )
                    Divider(color = SurfaceLight, thickness = 1.dp)
                    SummaryRow(
                        stringResource(R.string.swap_via),
                        "ChangeNOW",
                        valueColor = AccentBlue
                    )
                }
            }

            // Erreur
            if (state.error != null) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AccentRed.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth()
                ) {
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
                    modifier = Modifier
                        .size(24.dp)
                        .background(tokenColor(current), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        current.take(2).uppercase(),
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
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
