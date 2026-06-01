package com.vaultex.ui.screens.swap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

import com.vaultex.ui.components.PrimaryButton
import com.vaultex.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwapScreen(navController: NavHostController) {

    val tokens = listOf("ETH", "BTC", "BNB", "SOL", "TRX", "USDT")
    var fromToken by remember { mutableStateOf("ETH") }
    var toToken by remember { mutableStateOf("USDT") }
    var fromAmount by remember { mutableStateOf("") }
    var slippage by remember { mutableStateOf("0.5") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Swap", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgPrimary)
            )
        },
        containerColor = BgPrimary
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(BgPrimary, BgSecondary)))
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            // Token source
            TokenSwapCard(
                label = "De",
                selectedToken = fromToken,
                tokens = tokens.filter { it != toToken },
                amount = fromAmount,
                onAmountChange = { fromAmount = it },
                onTokenSelect = { fromToken = it }
            )

            Spacer(Modifier.height(8.dp))

            // Bouton inverser
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(color = BgSecondary)
                IconButton(
                    onClick = {
                        val tmp = fromToken; fromToken = toToken; toToken = tmp
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(AccentGold)
                ) {
                    Icon(Icons.Default.SwapVert, contentDescription = "Inverser", tint = BgPrimary)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Token destination
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = BgSecondary)) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Vers", color = AccentGold, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()) {
                        // Token picker
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { expanded = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGold)) {
                                Text(toToken, fontWeight = FontWeight.Bold)
                                Text(" ▼", color = TextSecondary, fontSize = 11.sp)
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
                                containerColor = BgSecondary) {
                                tokens.filter { it != fromToken }.forEach { token ->
                                    DropdownMenuItem(text = { Text(token, color = TextPrimary) },
                                        onClick = { toToken = token; expanded = false })
                                }
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("≈ —", color = TextMuted, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("Quote indisponible", color = TextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Slippage
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BgSecondary)) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Slippage max", color = TextSecondary, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("0.1", "0.5", "1.0").forEach { s ->
                            FilterChip(selected = slippage == s, onClick = { slippage = s },
                                label = { Text("$s%", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentGold, selectedLabelColor = BgPrimary,
                                    containerColor = BgPrimary, labelColor = TextSecondary))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Info frais VaultEx
            Card(colors = CardDefaults.cardColors(containerColor = AccentGold.copy(alpha = 0.08f)),
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Frais VaultEx", color = TextSecondary, fontSize = 13.sp)
                    Text("1.5%", color = AccentGold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(32.dp))

            PrimaryButton(
                text = "Obtenir un devis",
                enabled = fromAmount.isNotBlank() && fromAmount.toDoubleOrNull() != null,
                onClick = { /* 1inch API TODO */ }
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Les swaps sont fournis via 1inch DEX Aggregator",
                color = TextMuted,
                fontSize = 11.sp
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TokenSwapCard(
    label: String,
    selectedToken: String,
    tokens: List<String>,
    amount: String,
    onAmountChange: (String) -> Unit,
    onTokenSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BgSecondary)) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(label, color = AccentGold, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box {
                    OutlinedButton(onClick = { expanded = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGold)) {
                        Text(selectedToken, fontWeight = FontWeight.Bold)
                        Text(" ▼", color = TextSecondary, fontSize = 11.sp)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
                        containerColor = BgSecondary) {
                        tokens.forEach { token ->
                            DropdownMenuItem(text = { Text(token, color = TextPrimary) },
                                onClick = { onTokenSelect(token); expanded = false })
                        }
                    }
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = onAmountChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("0.00", color = TextSecondary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentGold, unfocusedBorderColor = BgPrimary,
                        focusedContainerColor = BgPrimary, unfocusedContainerColor = BgPrimary, cursorColor = AccentGold)
                )
            }
        }
    }
}
