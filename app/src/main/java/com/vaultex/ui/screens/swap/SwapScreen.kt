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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController

import com.vaultex.ui.components.PrimaryButton
import com.vaultex.ui.theme.*
import com.vaultex.ui.viewmodel.SwapViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwapScreen(navController: NavHostController) {

    val viewModel: SwapViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    // EVM chains only for 1inch
    val chains  = listOf("ETH", "BNB")
    val ethTokens = listOf("ETH", "USDT", "USDC", "WBTC")
    val bnbTokens = listOf("BNB", "USDT", "USDC", "WBTC")

    var selectedChain by remember { mutableStateOf("ETH") }
    var fromToken  by remember { mutableStateOf("ETH") }
    var toToken    by remember { mutableStateOf("USDT") }
    var fromAmount by remember { mutableStateOf("") }
    var slippage   by remember { mutableStateOf("0.5") }
    var showConfirm by remember { mutableStateOf(false) }

    val availableTokens = if (selectedChain == "ETH") ethTokens else bnbTokens

    // Reset tokens when chain changes
    LaunchedEffect(selectedChain) {
        fromToken = availableTokens.first()
        toToken   = availableTokens.drop(1).first()
        viewModel.reset()
    }

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

            // Chain selector
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = BgSecondary)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Réseau", color = AccentGold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        chains.forEach { chain ->
                            FilterChip(selected = selectedChain == chain,
                                onClick = { selectedChain = chain; viewModel.reset() },
                                label = { Text(chain, fontSize = 13.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentGold, selectedLabelColor = BgPrimary,
                                    containerColor = BgPrimary, labelColor = TextSecondary))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // From
            TokenSwapCard(
                label = "De",
                selectedToken = fromToken,
                tokens = availableTokens.filter { it != toToken },
                amount = fromAmount,
                onAmountChange = { fromAmount = it; viewModel.reset() },
                onTokenSelect  = { fromToken = it; viewModel.reset() }
            )

            Spacer(Modifier.height(8.dp))

            // Invert button
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(color = BgSecondary)
                IconButton(
                    onClick = {
                        val tmp = fromToken; fromToken = toToken; toToken = tmp
                        viewModel.reset()
                    },
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(AccentGold)
                ) {
                    Icon(Icons.Default.SwapVert, contentDescription = "Inverser", tint = BgPrimary)
                }
            }

            Spacer(Modifier.height(8.dp))

            // To
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = BgSecondary)) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Vers", color = AccentGold, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()) {
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { expanded = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGold)) {
                                Text(toToken, fontWeight = FontWeight.Bold)
                                Text(" ▼", color = TextSecondary, fontSize = 11.sp)
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
                                containerColor = BgSecondary) {
                                availableTokens.filter { it != fromToken }.forEach { token ->
                                    DropdownMenuItem(text = { Text(token, color = TextPrimary) },
                                        onClick = { toToken = token; expanded = false; viewModel.reset() })
                                }
                            }
                        }
                        // Quote result
                        when (val s = state) {
                            is SwapViewModel.UiState.QuoteReady ->
                                Text(s.toAmountFormatted, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            SwapViewModel.UiState.LoadingQuote ->
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = AccentGold, strokeWidth = 2.dp)
                            else ->
                                Text("≈ —", color = TextMuted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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

            Spacer(Modifier.height(12.dp))

            // VaultEx fee
            Card(colors = CardDefaults.cardColors(containerColor = AccentGold.copy(alpha = 0.08f)),
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Frais VaultEx", color = TextSecondary, fontSize = 13.sp)
                    Text("1.5%", color = AccentGold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Error
            if (state is SwapViewModel.UiState.Error) {
                Spacer(Modifier.height(12.dp))
                Text((state as SwapViewModel.UiState.Error).message, color = AccentRed, fontSize = 13.sp)
            }

            Spacer(Modifier.height(24.dp))

            val canQuote = fromAmount.toDoubleOrNull() != null && fromAmount.isNotBlank() &&
                           state !is SwapViewModel.UiState.LoadingQuote

            if (state is SwapViewModel.UiState.QuoteReady) {
                PrimaryButton(
                    text = if (state is SwapViewModel.UiState.Swapping) "Swap en cours…" else "Swapper maintenant",
                    enabled = state !is SwapViewModel.UiState.Swapping,
                    onClick = { showConfirm = true }
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = {
                    viewModel.getQuote(selectedChain, fromToken, toToken, fromAmount)
                }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGold)) {
                    Text("Rafraîchir le devis")
                }
            } else {
                PrimaryButton(
                    text = "Obtenir un devis",
                    enabled = canQuote,
                    onClick = { viewModel.getQuote(selectedChain, fromToken, toToken, fromAmount) }
                )
            }

            Spacer(Modifier.height(12.dp))
            Text("Swaps via 1inch DEX Aggregator", color = TextMuted, fontSize = 11.sp)
            Spacer(Modifier.height(32.dp))
        }

        // Success dialog
        if (state is SwapViewModel.UiState.Success) {
            AlertDialog(
                onDismissRequest = { viewModel.reset(); navController.popBackStack() },
                containerColor = BgSecondary,
                title = { Text("Swap effectué ✓", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentGold, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(4.dp))
                        Text("Hash :", color = TextSecondary, fontSize = 12.sp)
                        Text((state as SwapViewModel.UiState.Success).txHash,
                            color = AccentGold, fontSize = 11.sp,
                            maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.reset(); navController.popBackStack() }) {
                        Text("Fermer", color = AccentGold, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // Confirmation dialog
        if (showConfirm && state is SwapViewModel.UiState.QuoteReady) {
            val quote = state as SwapViewModel.UiState.QuoteReady
            AlertDialog(
                onDismissRequest = { showConfirm = false },
                containerColor = BgSecondary,
                title = { Text("Confirmer le swap", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Réseau : $selectedChain", color = TextSecondary)
                        Text("De : $fromAmount $fromToken", color = TextSecondary)
                        Text("Vers : ${quote.toAmountFormatted}", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("Slippage : $slippage%", color = TextMuted, fontSize = 12.sp)
                        Text("Frais VaultEx : 1.5%", color = TextMuted, fontSize = 12.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showConfirm = false
                        viewModel.executeSwap(selectedChain, fromToken, toToken, fromAmount, slippage)
                    }) {
                        Text("Confirmer", color = AccentGold, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirm = false }) {
                        Text("Annuler", color = TextSecondary)
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TokenSwapCard(
    label: String, selectedToken: String, tokens: List<String>,
    amount: String, onAmountChange: (String) -> Unit, onTokenSelect: (String) -> Unit
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
                    value = amount, onValueChange = onAmountChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("0.00", color = TextSecondary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, shape = RoundedCornerShape(12.dp),
                    textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentGold, unfocusedBorderColor = BgPrimary,
                        focusedContainerColor = BgPrimary, unfocusedContainerColor = BgPrimary, cursorColor = AccentGold)
                )
            }
        }
    }
}
