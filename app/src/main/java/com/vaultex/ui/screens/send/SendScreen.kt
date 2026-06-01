package com.vaultex.ui.screens.send

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
fun SendScreen(navController: NavHostController) {

    var address by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var selectedChain by remember { mutableStateOf("ETH") }
    var showConfirm by remember { mutableStateOf(false) }

    val chains = listOf("BTC", "ETH", "BNB", "SOL", "TRX")

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

            // Sélecteur de chaîne
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

            // Adresse destinataire
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = BgSecondary)) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Adresse destinataire", color = AccentGold, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("0x... ou adresse $selectedChain", color = TextSecondary, fontSize = 13.sp) },
                        trailingIcon = {
                            IconButton(onClick = { /* QR scan TODO */ }) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = AccentGold)
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentGold, unfocusedBorderColor = BgPrimary,
                            focusedContainerColor = BgPrimary, unfocusedContainerColor = BgPrimary,
                            cursorColor = AccentGold
                        )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Montant
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

            // Info frais
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BgSecondary.copy(alpha = 0.6f))) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Frais estimés", color = TextSecondary, fontSize = 13.sp)
                    Text("— $selectedChain", color = TextMuted, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(32.dp))

            PrimaryButton(
                text = "Continuer",
                enabled = address.isNotBlank() && amount.isNotBlank() && amount.toDoubleOrNull() != null,
                onClick = { showConfirm = true }
            )

            Spacer(Modifier.height(32.dp))
        }

        if (showConfirm) {
            AlertDialog(
                onDismissRequest = { showConfirm = false },
                containerColor = BgSecondary,
                title = { Text("Confirmer l'envoi", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Réseau : $selectedChain", color = TextSecondary)
                        Text("Vers : ${address.take(12)}...${address.takeLast(6)}", color = TextSecondary)
                        Text("Montant : $amount $selectedChain", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showConfirm = false; navController.popBackStack() }) {
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
