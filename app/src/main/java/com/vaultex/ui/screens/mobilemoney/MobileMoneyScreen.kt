package com.vaultex.ui.screens.mobilemoney

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
fun MobileMoneyScreen(navController: NavController) {
    val operators = listOf(
        MobileOperator("Orange Money", "Orange", Color(0xFFFF6600)),
        MobileOperator("Wave", "Wave", Color(0xFF1B95C6)),
        MobileOperator("Moov Money", "Moov", Color(0xFF0065BD)),
        MobileOperator("Coris Money", "Coris", Color(0xFF009245)),
    )
    var selectedOperator by remember { mutableStateOf<MobileOperator?>(null) }
    var phoneNumber by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var selectedCrypto by remember { mutableStateOf("USDT") }
    val cryptos = listOf("USDT", "USDC", "BTC", "ETH", "BNB")
    var showConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mobile Money", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
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
            // Fee notice
            Surface(
                color = VaultExColors.BlueLight,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "Frais : 1% via CinetPay · Zone UEMOA",
                    modifier = Modifier.padding(12.dp),
                    fontSize = 13.sp,
                    color = VaultExColors.BluePrimary
                )
            }

            Text("Opérateur", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                operators.forEach { op ->
                    OperatorChip(op, selectedOperator == op) { selectedOperator = op }
                }
            }

            Text("Numéro de téléphone", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                placeholder = { Text("+226 XX XX XX XX") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            Text("Crypto à convertir", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                cryptos.forEach { crypto ->
                    FilterChip(
                        selected = selectedCrypto == crypto,
                        onClick = { selectedCrypto = crypto },
                        label = { Text(crypto) }
                    )
                }
            }

            Text("Montant (FCFA)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                placeholder = { Text("Ex: 5000") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                suffix = { Text("FCFA") }
            )

            if (amount.isNotEmpty()) {
                val amountDouble = amount.toDoubleOrNull() ?: 0.0
                val fee = amountDouble * 0.01
                val receive = amountDouble - fee
                Surface(shape = RoundedCornerShape(12.dp), color = Color.White, tonalElevation = 1.dp) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Frais (1%)", color = VaultExColors.TextSecondary, fontSize = 13.sp)
                            Text("${String.format("%.0f", fee)} FCFA", fontSize = 13.sp)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Vous recevez", fontWeight = FontWeight.SemiBold)
                            Text("${String.format("%.0f", receive)} FCFA", fontWeight = FontWeight.Bold, color = VaultExColors.Success)
                        }
                    }
                }
            }

            Button(
                onClick = { showConfirm = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = selectedOperator != null && phoneNumber.isNotEmpty() && amount.isNotEmpty(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VaultExColors.BluePrimary)
            ) {
                Text("Confirmer le transfert", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun OperatorChip(op: MobileOperator, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) op.color.copy(alpha = 0.15f) else Color.White)
            .border(1.dp, if (selected) op.color else VaultExColors.Border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(op.shortName, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (selected) op.color else VaultExColors.TextSecondary)
    }
}

private data class MobileOperator(val name: String, val shortName: String, val color: Color)
