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
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vaultex.ui.theme.VaultExColors
import com.vaultex.ui.viewmodel.MobileMoneyViewModel

private data class MobileOperator(
    val name: String,
    val shortName: String,
    val color: Color,
    val flwNetwork: String
)

private val OPERATORS = listOf(
    MobileOperator("Orange Money", "Orange", Color(0xFFFF6600), "ORANGE"),
    MobileOperator("Wave",         "Wave",   Color(0xFF1B95C6), "WAVE"),
    MobileOperator("Moov Money",   "Moov",   Color(0xFF0065BD), "MOOV"),
    MobileOperator("Free Money",   "Free",   Color(0xFF009245), "FREE"),
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
            Surface(color = VaultExColors.BlueLight, shape = RoundedCornerShape(12.dp)) {
                Text(
                    "Frais : 1% via Flutterwave · Zone UEMOA",
                    modifier = Modifier.padding(12.dp),
                    fontSize = 13.sp,
                    color = VaultExColors.BluePrimary
                )
            }

            Text("Opérateur", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OPERATORS.forEach { op ->
                    val selected = state.selectedNetwork == op.flwNetwork
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) op.color.copy(alpha = 0.15f) else Color.White)
                            .border(1.dp, if (selected) op.color else VaultExColors.Border, RoundedCornerShape(10.dp))
                            .clickable { viewModel.setNetwork(op.flwNetwork) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            op.shortName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (selected) op.color else VaultExColors.TextSecondary
                        )
                    }
                }
            }

            Text("Numéro de téléphone", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            OutlinedTextField(
                value = state.phoneNumber,
                onValueChange = { viewModel.setPhone(it) },
                placeholder = { Text("+226 XX XX XX XX") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            Text("Montant (FCFA)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            OutlinedTextField(
                value = state.amountFcfa,
                onValueChange = { viewModel.setAmount(it) },
                placeholder = { Text("Ex: 5000") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                suffix = { Text("FCFA") }
            )

            if (state.amountFcfa.isNotEmpty() && state.amountFcfa.toDoubleOrNull() != null) {
                Surface(shape = RoundedCornerShape(12.dp), color = Color.White, tonalElevation = 1.dp) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Frais (1%)", color = VaultExColors.TextSecondary, fontSize = 13.sp)
                            Text("${String.format("%.0f", viewModel.fee)} FCFA", fontSize = 13.sp)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Vous recevez", fontWeight = FontWeight.SemiBold)
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
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Confirmer le transfert", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Confirmer") },
            text = {
                val op = OPERATORS.find { it.flwNetwork == state.selectedNetwork }?.name ?: state.selectedNetwork
                Text("Envoyer ${state.amountFcfa} FCFA vers ${state.phoneNumber} via $op ?")
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    viewModel.initiateTransfer()
                }) { Text("Confirmer") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("Annuler") }
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
        Text("Transfert initié !", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Référence : $txRef", fontSize = 13.sp, color = VaultExColors.TextSecondary)
        if (flwRef.isNotEmpty()) {
            Text("Flutterwave ref : $flwRef", fontSize = 12.sp, color = VaultExColors.TextSecondary)
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = onDone, shape = RoundedCornerShape(10.dp)) {
            Text("Retour")
        }
    }
}
