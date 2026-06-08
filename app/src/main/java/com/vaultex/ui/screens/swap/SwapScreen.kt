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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.vaultex.ui.theme.VaultExColors

@Composable
fun SwapScreen(navController: NavController) {
    val tokens = listOf("ETH", "BNB", "USDT", "USDC", "BTC", "SOL", "TRX")
    var fromToken by remember { mutableStateOf("ETH") }
    var toToken by remember { mutableStateOf("USDT") }
    var fromAmount by remember { mutableStateOf("") }
    var isCrossChain by remember { mutableStateOf(false) }

    val toEstimate = if (fromAmount.isNotEmpty()) {
        val amt = fromAmount.toDoubleOrNull() ?: 0.0
        val net = amt * (1 - 0.015)
        String.format("%.6f", net * 2770.0)
    } else ""

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Swap", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = VaultExColors.Background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Mode selector
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ModeChip("Même chaîne (1inch)", !isCrossChain) { isCrossChain = false }
                ModeChip("Cross-chain (ChangeNOW)", isCrossChain) { isCrossChain = true }
            }

            // From card
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Vous envoyez", fontSize = 13.sp, color = VaultExColors.TextSecondary)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = fromAmount, onValueChange = { fromAmount = it },
                            placeholder = { Text("0.00") },
                            modifier = Modifier.weight(1f), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(10.dp)
                        )
                        TokenPill(fromToken) { }
                    }
                    Text("Solde : 0.45 $fromToken", fontSize = 12.sp, color = VaultExColors.TextSecondary)
                }
            }

            // Swap icon
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                IconButton(onClick = { val tmp = fromToken; fromToken = toToken; toToken = tmp; fromAmount = "" },
                    modifier = Modifier.background(VaultExColors.BluePrimary, RoundedCornerShape(12.dp)).size(40.dp)) {
                    Icon(Icons.Default.SwapVert, null, tint = Color.White)
                }
            }

            // To card
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Vous recevez (estimé)", fontSize = 13.sp, color = VaultExColors.TextSecondary)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = toEstimate, onValueChange = {},
                            placeholder = { Text("0.00") },
                            modifier = Modifier.weight(1f), singleLine = true, enabled = false,
                            shape = RoundedCornerShape(10.dp)
                        )
                        TokenPill(toToken) { }
                    }
                }
            }

            // Fee summary
            if (fromAmount.isNotEmpty()) {
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = VaultExColors.BlueLight)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FeeRow("Frais VaultEx (1.5%)", "${String.format("%.4f", (fromAmount.toDoubleOrNull() ?: 0.0) * 0.015)} $fromToken")
                        FeeRow("Route", if (isCrossChain) "ChangeNOW" else "1inch DEX")
                        FeeRow("Slippage max", "0.5%")
                    }
                }
            }

            Button(
                onClick = { navController.navigate("swap_confirm") },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = fromAmount.isNotEmpty() && fromToken != toToken,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VaultExColors.BluePrimary)
            ) { Text("Swap", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, fontSize = 12.sp) }, modifier = Modifier.weight(1f))
}

@Composable
private fun TokenPill(token: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(10.dp), color = VaultExColors.BlueLight, tonalElevation = 0.dp) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(token, fontWeight = FontWeight.Bold, color = VaultExColors.BluePrimary)
            Icon(Icons.Default.ArrowDropDown, null, tint = VaultExColors.BluePrimary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun FeeRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = VaultExColors.TextSecondary)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = VaultExColors.TextPrimary)
    }
}
