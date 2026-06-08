package com.vaultex.ui.screens.send

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vaultex.core.security.BiometricHelper
import com.vaultex.ui.theme.VaultExColors
import com.vaultex.ui.viewmodel.SendViewModel

@Composable
fun SendScreen(navController: NavController) {
    val viewModel: SendViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current as FragmentActivity
    val biometricHelper = remember { BiometricHelper(context) }

    val chains = listOf("BTC", "ETH", "BNB", "SOL", "TRX", "USDT")

    val feeEstimate = when (state.selectedChain) {
        "BTC"  -> "~2 800 FCFA"
        "ETH"  -> "~1 500 FCFA"
        "BNB"  -> "~150 FCFA"
        "SOL"  -> "~5 FCFA"
        "TRX"  -> "~20 FCFA"
        "USDT" -> "~20 FCFA (TRC20)"
        else   -> "--"
    }

    // Success dialog
    if (state.txHash != null) {
        AlertDialog(
            onDismissRequest = { viewModel.reset(); navController.popBackStack() },
            icon = { Icon(Icons.Default.CheckCircle, null, tint = VaultExColors.Success) },
            title = { Text("Transaction envoyée") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Votre transaction a été diffusée sur le réseau.", fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Hash :", fontSize = 12.sp, color = VaultExColors.TextSecondary)
                    Text(
                        state.txHash!!.take(20) + "…",
                        fontSize = 11.sp,
                        color = VaultExColors.BluePrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.reset(); navController.popBackStack() }) {
                    Text("Fermer")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Envoyer", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
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

            // Chain selector
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Réseau", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        chains.forEach { chain ->
                            FilterChip(
                                selected = state.selectedChain == chain,
                                onClick = { viewModel.setChain(chain) },
                                label = { Text(chain, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Address input
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Adresse destinataire", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    OutlinedTextField(
                        value = state.toAddress,
                        onValueChange = { viewModel.setToAddress(it) },
                        placeholder = {
                            Text(
                                when (state.selectedChain) {
                                    "BTC"  -> "bc1... ou 1... ou 3..."
                                    "ETH", "BNB" -> "0x..."
                                    "TRX", "USDT" -> "T..."
                                    "SOL"  -> "Adresse Solana Base58"
                                    else   -> "Adresse ${state.selectedChain}"
                                },
                                color = VaultExColors.TextSecondary,
                                fontSize = 13.sp
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        isError = state.toAddress.isNotEmpty() && !state.isAddressValid,
                        trailingIcon = {
                            IconButton(onClick = { navController.navigate("scanner") }) {
                                Icon(Icons.Default.QrCodeScanner, null, tint = VaultExColors.BluePrimary)
                            }
                        }
                    )
                    if (state.toAddress.isNotEmpty() && !state.isAddressValid) {
                        Text(
                            "Adresse ${state.selectedChain} invalide",
                            fontSize = 12.sp,
                            color = VaultExColors.Error
                        )
                    }
                }
            }

            // Amount input
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Montant", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    OutlinedTextField(
                        value = state.amount,
                        onValueChange = { viewModel.setAmount(it) },
                        placeholder = { Text("0.00") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        suffix = { Text(state.selectedChain, color = VaultExColors.TextSecondary) }
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Frais estimés :", fontSize = 12.sp, color = VaultExColors.TextSecondary)
                        Text(feeEstimate, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Error message
            if (state.error != null) {
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = VaultExColors.Error.copy(alpha = 0.1f))
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, null, tint = VaultExColors.Error, modifier = Modifier.size(18.dp))
                        Text(state.error!!, fontSize = 13.sp, color = VaultExColors.Error)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    val bioStatus = biometricHelper.checkAvailability()
                    if (bioStatus == BiometricHelper.BiometricStatus.AVAILABLE) {
                        biometricHelper.authenticate(
                            title = "Confirmer la transaction",
                            subtitle = "Envoi de ${state.amount} ${state.selectedChain} vers ${state.toAddress.take(12)}…",
                            onSuccess = { viewModel.send() },
                            onError = { _, msg ->
                                // user cancelled or error — no-op, stays on screen
                            }
                        )
                    } else {
                        viewModel.send()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = state.isAddressValid && state.amount.isNotEmpty() && !state.isLoading,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VaultExColors.BluePrimary)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Envoyer", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }
        }
    }
}
