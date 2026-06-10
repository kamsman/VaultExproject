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
import com.vaultex.ui.theme.VaultExColors
import com.vaultex.ui.viewmodel.MobileMoneyViewModel

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

@Composable
fun MobileMoneyScreen(
    navController: NavController,
    viewModel: MobileMoneyViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showConfirmDialog by remember { mutableStateOf(false) }

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
            TopAppBar(
                title = { Text(stringResource(R.string.mobile_money_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultExColors.Background)
            )
        },
        containerColor = VaultExColors.Background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(color = VaultExColors.BlueLight, shape = RoundedCornerShape(12.dp)) {
                Text(
                    stringResource(R.string.momo_fee_notice),
                    modifier = Modifier.padding(12.dp),
                    fontSize = 13.sp,
                    color = VaultExColors.BluePrimary
                )
            }

            Text(stringResource(R.string.operator), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                OPERATORS.forEach { op ->
                    val selected = state.selectedNetwork == op.flwNetwork
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when {
                                    !op.available -> VaultExColors.CardBackground.copy(alpha = 0.5f)
                                    selected -> op.color.copy(alpha = 0.15f)
                                    else -> VaultExColors.CardBackground
                                }
                            )
                            .border(1.dp,
                                if (!op.available) VaultExColors.Border
                                else if (selected) op.color
                                else VaultExColors.Border,
                                RoundedCornerShape(10.dp))
                            .then(if (op.available) Modifier.clickable { viewModel.setNetwork(op.flwNetwork) } else Modifier)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                op.shortName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (!op.available) Color(0xFFBBBBBB)
                                        else if (selected) op.color
                                        else VaultExColors.TextSecondary
                            )
                            if (!op.available) {
                                Text(stringResource(R.string.momo_coming_soon), fontSize = 9.sp, color = Color(0xFFBBBBBB))
                            }
                        }
                    }
                }
            }

            Text(stringResource(R.string.phone_number), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            OutlinedTextField(
                value = state.phoneNumber,
                onValueChange = { viewModel.setPhone(it) },
                placeholder = { Text("+226 XX XX XX XX") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            Text(stringResource(R.string.momo_amount_fcfa), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            OutlinedTextField(
                value = state.amountFcfa,
                onValueChange = { viewModel.setAmount(it) },
                placeholder = { Text(stringResource(R.string.momo_amount_example)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                suffix = { Text("FCFA") }
            )

            if (state.amountFcfa.isNotEmpty() && state.amountFcfa.toDoubleOrNull() != null) {
                Surface(shape = RoundedCornerShape(12.dp), color = VaultExColors.CardBackground, tonalElevation = 1.dp) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.momo_fee_label), color = VaultExColors.TextSecondary, fontSize = 13.sp)
                            Text("${String.format("%.0f", viewModel.fee)} FCFA", fontSize = 13.sp)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.momo_you_receive), fontWeight = FontWeight.SemiBold)
                            Text(
                                "${String.format("%.0f", viewModel.amountAfterFee)} FCFA",
                                fontWeight = FontWeight.Bold,
                                color = VaultExColors.Success
                            )
                        }
                    }
                }
            }

            state.error?.let { err ->
                Text(err, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            Button(
                onClick = { showConfirmDialog = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = state.selectedNetwork.isNotEmpty()
                        && state.phoneNumber.isNotEmpty()
                        && state.amountFcfa.isNotEmpty()
                        && !state.isLoading,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VaultExColors.BluePrimary)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(color = VaultExColors.TextOnPrimary, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.momo_confirm_transfer), fontWeight = FontWeight.SemiBold)
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
private fun SuccessScreen(txRef: String, flwRef: String, onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = VaultExColors.Success,
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.momo_transfer_initiated), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.momo_reference, txRef), fontSize = 13.sp, color = VaultExColors.TextSecondary)
        if (flwRef.isNotEmpty()) {
            Text(stringResource(R.string.momo_flw_reference, flwRef), fontSize = 12.sp, color = VaultExColors.TextSecondary)
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = onDone, shape = RoundedCornerShape(10.dp)) {
            Text(stringResource(R.string.back))
        }
    }
}
