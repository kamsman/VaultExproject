package com.vaultex.ui.screens.send

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.vaultex.ui.theme.VaultExColors

@Composable
fun SendScreen(navController: NavController) {
    val chains = listOf("BTC", "ETH", "BNB", "SOL", "TRX")
    var selectedChain by remember { mutableStateOf("ETH") }
    var toAddress by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    val isAddressValid = toAddress.length >= 26

    val feeEstimate = when (selectedChain) {
        "BTC" -> "~2 800 FCFA"
        "ETH" -> "~1 500 FCFA"
        "BNB" -> "~150 FCFA"
        "SOL" -> "~5 FCFA"
        "TRX" -> "~20 FCFA"
        else -> "--"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Envoyer", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = VaultExColors.Background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Chain selector
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Réseau", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        chains.forEach { chain ->
                            FilterChip(selected = selectedChain == chain, onClick = { selectedChain = chain }, label = { Text(chain) })
                        }
                    }
                }
            }

            // Address input
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Adresse destinataire", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    OutlinedTextField(
                        value = toAddress,
                        onValueChange = { toAddress = it },
                        placeholder = { Text("0x... ou adresse $selectedChain", color = VaultExColors.TextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        isError = toAddress.isNotEmpty() && !isAddressValid,
                        trailingIcon = {
                            IconButton(onClick = { navController.navigate("scanner") }) {
                                Icon(Icons.Default.QrCodeScanner, null, tint = VaultExColors.BluePrimary)
                            }
                        }
                    )
                    if (toAddress.isNotEmpty() && !isAddressValid) {
                        Text("Adresse invalide", fontSize = 12.sp, color = VaultExColors.Error)
                    }
                }
            }

            // Amount input
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Montant", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        placeholder = { Text("0.00") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        suffix = { Text(selectedChain, color = VaultExColors.TextSecondary) },
                        trailingIcon = { TextButton(onClick = { amount = "MAX" }) { Text("MAX", color = VaultExColors.BluePrimary, fontSize = 12.sp) } }
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Frais estimés :", fontSize = 12.sp, color = VaultExColors.TextSecondary)
                        Text(feeEstimate, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = { navController.navigate("send_confirm") },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = isAddressValid && amount.isNotEmpty(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VaultExColors.BluePrimary)
            ) {
                Text("Continuer", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }
    }
}
