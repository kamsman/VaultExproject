package com.vaultex.ui.screens.send

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.vaultex.ui.viewmodel.SendViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(navController: NavHostController, preselectedSymbol: String = "") {

    val viewModel: SendViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    val chains = listOf("BTC", "ETH", "BNB", "SOL", "TRX")
    val initialChain = if (preselectedSymbol in chains) preselectedSymbol else "ETH"

    var address by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var selectedChain by remember { mutableStateOf(initialChain) }
    var showConfirm by remember { mutableStateOf(false) }

    val feeLabel = when (selectedChain) {
        "BTC" -> "~sat/vbyte × 250 vbytes"
        "ETH" -> "~21 000 gas × gasPrice"
        "BNB" -> "~21 000 gas × gasPrice"
        "SOL" -> "~0.000005 SOL"
        "TRX" -> "~1 TRX"
        else  -> "—"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Envoyer", color = TextPrimary) },
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
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Réseau", color = AccentGold, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        chains.forEach { chain ->
                            FilterChip(
                                selected = selectedChain == chain,
                                onClick = { selectedChain = chain },
                                label = { Text(chain, fontSize = 13.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentGold,
                                    selectedLabelColor = BgPrimary,
                                    containerColor = BgPrimary,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Recipient address
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = BgSecondary)) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Adresse destinataire", color = AccentGold, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("0x… ou adresse $selectedChain", color = TextSecondary, fontSize = 13.sp) },
                        trailingIcon = {
                            IconButton(onClick = { /* QR scan */ }) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = AccentGold)
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentGold, unfocusedBorderColor = BgPrimary,
                            focusedContainerColor = BgPrimary, unfocusedContainerColor = BgPrimary,
                            cursorColor = AccentGold, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                        )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Amount
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = BgSecondary)) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Montant ($selectedChain)", color = AccentGold, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("0.00", color = TextSecondary) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentGold, unfocusedBorderColor = BgPrimary,
                            focusedContainerColor = BgPrimary, unfocusedContainerColor = BgPrimary,
                            cursorColor = AccentGold
                        )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Fee info
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BgSecondary.copy(alpha = 0.6f))) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Frais estimés", color = TextSecondary, fontSize = 13.sp)
                    Text(feeLabel, color = TextMuted, fontSize = 13.sp)
                }
            }

            // Error display
            if (state is SendViewModel.UiState.Error) {
                Spacer(Modifier.height(12.dp))
                Text((state as SendViewModel.UiState.Error).message, color = AccentRed, fontSize = 13.sp)
            }

            Spacer(Modifier.height(32.dp))

            PrimaryButton(
                text = "Continuer",
                enabled = address.isNotBlank() && amount.toDoubleOrNull() != null && state !is SendViewModel.UiState.Loading,
                onClick = { viewModel.reset(); showConfirm = true }
            )

            Spacer(Modifier.height(32.dp))
        }

        // Confirmation / result dialog
        if (showConfirm) {
            val currentState = state
            AlertDialog(
                onDismissRequest = {
                    if (currentState !is SendViewModel.UiState.Loading) {
                        showConfirm = false
                        if (currentState is SendViewModel.UiState.Success) navController.popBackStack()
                    }
                },
                containerColor = BgSecondary,
                title = {
                    Text(
                        when (currentState) {
                            is SendViewModel.UiState.Success -> "Transaction envoyée ✓"
                            is SendViewModel.UiState.Error   -> "Erreur"
                            else -> "Confirmer l'envoi"
                        },
                        color = TextPrimary, fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    when (currentState) {
                        SendViewModel.UiState.Loading -> {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = AccentGold)
                                    Spacer(Modifier.height(12.dp))
                                    Text("Signature et envoi en cours…", color = TextSecondary, fontSize = 13.sp)
                                }
                            }
                        }
                        is SendViewModel.UiState.Success -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentGold,
                                    modifier = Modifier.size(40.dp).align(Alignment.CenterHorizontally))
                                Spacer(Modifier.height(4.dp))
                                Text("Hash de transaction :", color = TextSecondary, fontSize = 12.sp)
                                Text(
                                    currentState.txHash,
                                    color = AccentGold, fontSize = 11.sp,
                                    maxLines = 3, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        is SendViewModel.UiState.Error -> {
                            Text(currentState.message, color = AccentRed, fontSize = 13.sp)
                        }
                        else -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Réseau : $selectedChain", color = TextSecondary)
                                Text("Vers : ${if (address.length > 20) "${address.take(10)}…${address.takeLast(6)}" else address}", color = TextSecondary)
                                Text("Montant : $amount $selectedChain", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                },
                confirmButton = {
                    when (currentState) {
                        is SendViewModel.UiState.Success -> {
                            TextButton(onClick = { showConfirm = false; navController.popBackStack() }) {
                                Text("Fermer", color = AccentGold, fontWeight = FontWeight.Bold)
                            }
                        }
                        SendViewModel.UiState.Loading -> {}
                        else -> {
                            TextButton(onClick = { viewModel.send(selectedChain, address, amount) }) {
                                Text("Confirmer", color = AccentGold, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
                dismissButton = {
                    if (currentState !is SendViewModel.UiState.Loading && currentState !is SendViewModel.UiState.Success) {
                        TextButton(onClick = { showConfirm = false }) {
                            Text("Annuler", color = TextSecondary)
                        }
                    }
                }
            )
        }
    }
}
