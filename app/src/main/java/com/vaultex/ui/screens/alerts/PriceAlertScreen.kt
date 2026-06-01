package com.vaultex.ui.screens.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vaultex.data.local.entity.PriceAlertEntity
import com.vaultex.ui.theme.*
import com.vaultex.ui.viewmodel.PriceAlertViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceAlertScreen(navController: NavHostController) {

    val viewModel: PriceAlertViewModel = hiltViewModel()
    val alerts by viewModel.alerts.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alertes de prix", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Ajouter", tint = AccentGold)
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
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            if (alerts.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null,
                            tint = TextMuted, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Aucune alerte active", color = TextSecondary, fontSize = 16.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("Appuyez sur + pour en créer une", color = TextMuted, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(alerts, key = { it.id }) { alert ->
                        AlertRow(alert = alert, onDelete = { viewModel.deleteAlert(alert.id) })
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddAlertDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { symbol, condition, price ->
                viewModel.addAlert(symbol, condition, price)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AlertRow(alert: PriceAlertEntity, onDelete: () -> Unit) {
    val conditionLabel = if (alert.condition == "ABOVE") "au-dessus de" else "en-dessous de"
    val conditionColor = if (alert.condition == "ABOVE") androidx.compose.ui.graphics.Color(0xFF4CAF50) else AccentRed

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BgSecondary)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(alert.tokenSymbol, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Row {
                    Text("Notifier si ", color = TextSecondary, fontSize = 13.sp)
                    Text(conditionLabel, color = conditionColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(" ${"$"}${alert.targetPrice}", color = TextPrimary, fontSize = 13.sp)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = AccentRed)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAlertDialog(onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    val symbols = listOf("BTC", "ETH", "BNB", "SOL", "TRX")
    var selectedSymbol by remember { mutableStateOf("BTC") }
    var condition by remember { mutableStateOf("ABOVE") }
    var targetPrice by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgSecondary,
        title = { Text("Nouvelle alerte", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Token selector
                Text("Token", color = AccentGold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    symbols.forEach { s ->
                        FilterChip(selected = selectedSymbol == s, onClick = { selectedSymbol = s },
                            label = { Text(s, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentGold, selectedLabelColor = BgPrimary,
                                containerColor = BgPrimary, labelColor = TextSecondary))
                    }
                }

                // Condition
                Text("Condition", color = AccentGold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ABOVE" to "Au-dessus de", "BELOW" to "En-dessous de").forEach { (value, label) ->
                        FilterChip(selected = condition == value, onClick = { condition = value },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentGold, selectedLabelColor = BgPrimary,
                                containerColor = BgPrimary, labelColor = TextSecondary))
                    }
                }

                // Price
                Text("Prix cible (USD)", color = AccentGold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = targetPrice,
                    onValueChange = { targetPrice = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Ex: 50000", color = TextSecondary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentGold, unfocusedBorderColor = BgPrimary,
                        focusedContainerColor = BgPrimary, unfocusedContainerColor = BgPrimary,
                        cursorColor = AccentGold, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (targetPrice.toDoubleOrNull() != null) onConfirm(selectedSymbol, condition, targetPrice) },
                enabled = targetPrice.toDoubleOrNull() != null
            ) {
                Text("Créer", color = AccentGold, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler", color = TextSecondary) }
        }
    )
}
