package com.vaultex.ui.screens.mobilemoney

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vaultex.R
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
import com.vaultex.ui.viewmodel.MobileMoneyViewModel
import java.util.Locale

private data class MobileOperator(
    val name: String,
    val shortName: String,
    val color: Color,
    val flwNetwork: String,
    val available: Boolean = true
)

// Flutterwave mobile_money_franco supports ORANGE, WAVE, MOOV, FREE in UEMOA.
// MTN Money is not in the UEMOA franco zone on Flutterwave — shown as disabled.
private val OPERATORS = listOf(
    MobileOperator("Orange Money", "Orange", Color(0xFFFF6600), "ORANGE"),
    MobileOperator("Wave",         "Wave",   Color(0xFF1B95C6), "WAVE"),
    MobileOperator("Moov Money",   "Moov",   Color(0xFF0065BD), "MOOV"),
    MobileOperator("Free Money",   "Free",   Color(0xFF009245), "FREE"),
    MobileOperator("MTN Money",    "MTN",    Color(0xFFFFCC00), "MTN",  available = false),
)

private fun formatXof(value: Double): String =
    String.format(Locale.US, "%,.0f", value).replace(",", " ")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileMoneyScreen(
    navController: NavController,
    viewModel: MobileMoneyViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showConfirmDialog by remember { mutableStateOf(false) }
    // Visual direction toggle (mockup) — only Crypto → CFA is backed by the API today.
    var cryptoToCfa by remember { mutableStateOf(true) }

    if (state.success) {
        SuccessScreen(
            txRef = state.txRef ?: "",
            flwRef = state.flwRef ?: "",
            onDone = {
                viewModel.resetResult()
                navController.popBackStack()
            }
        )
        return
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.mobile_money_title),
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
            // Fee notice banner with left accent bar
            Surface(color = BgTertiary, shape = RoundedCornerShape(10.dp)) {
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(
                        Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .background(AccentBlue)
                    )
                    Text(
                        stringResource(R.string.momo_fee_notice),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                }
            }

            // Direction toggle
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DirectionPill(
                    text = stringResource(R.string.momo_crypto_to_cfa),
                    selected = cryptoToCfa,
                    modifier = Modifier.weight(1f),
                    onClick = { cryptoToCfa = true }
                )
                DirectionPill(
                    text = stringResource(R.string.momo_cfa_to_crypto),
                    selected = !cryptoToCfa,
                    modifier = Modifier.weight(1f),
                    onClick = { cryptoToCfa = false }
                )
            }

            // Operator chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                OPERATORS.forEach { op ->
                    val selected = state.selectedNetwork == op.flwNetwork
                    OperatorChip(
                        operator = op,
                        selected = selected,
                        onClick = { viewModel.setNetwork(op.flwNetwork) }
                    )
                }
            }

            // Phone number
            MomoTextField(
                value = state.phoneNumber,
                onValueChange = { viewModel.setPhone(it) },
                placeholder = stringResource(R.string.momo_phone_placeholder),
                keyboardType = KeyboardType.Phone
            )

            // Amount
            MomoTextField(
                value = state.amountFcfa,
                onValueChange = { viewModel.setAmount(it) },
                placeholder = stringResource(R.string.momo_amount_placeholder),
                keyboardType = KeyboardType.Number
            )

            // Summary card
            Surface(shape = RoundedCornerShape(16.dp), color = SurfaceColor) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.label_fee), color = TextSecondary, fontSize = 14.sp)
                        Text(
                            "~${formatXof(viewModel.fee)} XOF",
                            fontSize = 14.sp,
                            color = TextPrimary
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.momo_you_receive), color = TextSecondary, fontSize = 14.sp)
                        Text(
                            "~${formatXof(viewModel.amountAfterFee)} XOF",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextPrimary
                        )
                    }
                }
            }

            state.error?.let { err ->
                Text(err, color = AccentRed, fontSize = 13.sp)
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { showConfirmDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                enabled = state.selectedNetwork.isNotEmpty()
                        && state.phoneNumber.isNotEmpty()
                        && state.amountFcfa.isNotEmpty()
                        && !state.isLoading,
                shape = RoundedCornerShape(27.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentBlue,
                    contentColor = Color.White
                )
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        stringResource(R.string.mobile_money_confirm_transfer),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(stringResource(R.string.confirm)) },
            text = {
                val op = OPERATORS.find { it.flwNetwork == state.selectedNetwork }?.name ?: state.selectedNetwork
                Text(stringResource(R.string.momo_confirm_message, state.amountFcfa, state.phoneNumber, op))
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    viewModel.initiateTransfer()
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun DirectionPill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) AccentBlue else SurfaceColor)
            .border(
                1.dp,
                if (selected) AccentBlue else BorderColor,
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else TextSecondary
        )
    }
}

@Composable
private fun OperatorChip(
    operator: MobileOperator,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    !operator.available -> SurfaceLight
                    selected -> operator.color.copy(alpha = 0.10f)
                    else -> SurfaceColor
                }
            )
            .border(
                if (selected && operator.available) 2.dp else 1.5.dp,
                if (!operator.available) BorderColor else operator.color,
                RoundedCornerShape(12.dp)
            )
            .then(if (operator.available) Modifier.clickable(onClick = onClick) else Modifier)
            .width(66.dp)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (!operator.available) {
                Text(
                    stringResource(R.string.momo_coming_soon),
                    fontSize = 8.sp,
                    color = TextMuted
                )
            }
            Text(
                operator.shortName.first().uppercase(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (operator.available) operator.color else TextMuted
            )
            Text(
                operator.shortName,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (operator.available) TextPrimary else TextMuted
            )
        }
    }
}

@Composable
private fun MomoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = TextMuted, fontSize = 14.sp) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = SurfaceLight,
            unfocusedContainerColor = SurfaceLight,
            disabledContainerColor = SurfaceLight,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = AccentBlue,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        )
    )
}

@Composable
private fun SuccessScreen(txRef: String, flwRef: String, onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = AccentGreen,
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.momo_transfer_initiated),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.momo_reference, txRef), fontSize = 13.sp, color = TextSecondary)
        if (flwRef.isNotEmpty()) {
            Text(stringResource(R.string.momo_flw_reference, flwRef), fontSize = 12.sp, color = TextSecondary)
        }
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onDone,
            shape = RoundedCornerShape(27.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = Color.White)
        ) {
            Text(stringResource(R.string.back))
        }
    }
}
