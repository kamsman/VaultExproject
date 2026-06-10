package com.vaultex.ui.screens.swap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vaultex.R
import com.vaultex.ui.theme.VaultExColors
import com.vaultex.ui.viewmodel.SwapViewModel

@Composable
fun SwapScreen(navController: NavController) {
    val viewModel: SwapViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current

    val tokens = listOf("ETH", "BNB", "USDT", "BTC", "SOL", "TRX")

    // Résultat du swap : afficher la payin address
    if (state.payinAddress != null) {
        AlertDialog(
            onDismissRequest = { viewModel.resetSwap() },
            icon = { Icon(Icons.Default.SwapHoriz, null, tint = VaultExColors.BluePrimary) },
            title = { Text(stringResource(R.string.swap_created_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.swap_send_instruction, state.fromAmount, state.fromToken), fontSize = 14.sp)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = VaultExColors.BlueLight
                    ) {
                        Text(
                            state.payinAddress!!,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp),
                            color = VaultExColors.BluePrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(stringResource(R.string.swap_id_label, state.swapId?.take(16) ?: ""), fontSize = 11.sp, color = VaultExColors.TextSecondary)
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
            TopAppBar(
                title = { Text(stringResource(R.string.action_swap), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultExColors.Background)
            )
        },
        containerColor = VaultExColors.Background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Mode selector
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ModeChip(stringResource(R.string.swap_mode_same_chain), !state.isCrossChain) { /* TODO */ }
                ModeChip(stringResource(R.string.swap_mode_cross_chain), state.isCrossChain) { /* always cross-chain for now */ }
            }

            // From
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = VaultExColors.CardBackground)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.swap_you_send), fontSize = 13.sp, color = VaultExColors.TextSecondary)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = state.fromAmount,
                            onValueChange = { viewModel.setFromAmount(it) },
                            placeholder = { Text("0.00") },
                            modifier = Modifier.weight(1f), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(10.dp)
                        )
                        TokenPill(state.fromToken, tokens) { viewModel.setFromToken(it) }
                    }
                }
            }

            // Swap icon
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                IconButton(
                    onClick = { viewModel.swapTokens() },
                    modifier = Modifier.background(VaultExColors.BluePrimary, RoundedCornerShape(12.dp)).size(40.dp)
                ) {
                    Icon(Icons.Default.SwapVert, contentDescription = stringResource(R.string.swap_invert_tokens), tint = VaultExColors.TextOnPrimary)
                }
            }

            // To
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = VaultExColors.CardBackground)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.swap_you_receive_estimated), fontSize = 13.sp, color = VaultExColors.TextSecondary)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = state.toAmount,
                            onValueChange = {},
                            placeholder = { Text("0.00") },
                            modifier = Modifier.weight(1f), singleLine = true,
                            enabled = false, shape = RoundedCornerShape(10.dp)
                        )
                        TokenPill(state.toToken, tokens) { viewModel.setToToken(it) }
                    }
                }
            }

            // Fee summary
            if (state.fromAmount.isNotEmpty()) {
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = VaultExColors.BlueLight)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FeeRow(stringResource(R.string.swap_fee_vaultex), "${state.estimatedFee.ifEmpty { "—" }} ${state.fromToken}")
                        FeeRow(stringResource(R.string.swap_route), "ChangeNOW cross-chain")
                        FeeRow(stringResource(R.string.swap_slippage_max), "0.5%")
                    }
                }
            }

            // Error
            if (state.error != null) {
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = VaultExColors.Error.copy(alpha = 0.1f))
                ) {
                    Text(state.error!!, fontSize = 13.sp, color = VaultExColors.Error, modifier = Modifier.padding(12.dp))
                }
            }

            Button(
                onClick = { viewModel.executeSwap() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = state.fromAmount.isNotEmpty() && state.fromToken != state.toToken && !state.isLoading,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VaultExColors.BluePrimary)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = VaultExColors.TextOnPrimary, strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.action_swap), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) =
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, fontSize = 12.sp) }, modifier = Modifier.weight(1f))

@Composable
private fun TokenPill(current: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(10.dp),
            color = VaultExColors.BlueLight
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(current, fontWeight = FontWeight.Bold, color = VaultExColors.BluePrimary)
                Icon(Icons.Default.ArrowDropDown, null, tint = VaultExColors.BluePrimary, modifier = Modifier.size(18.dp))
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
private fun FeeRow(label: String, value: String) =
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = VaultExColors.TextSecondary)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = VaultExColors.TextPrimary)
    }
