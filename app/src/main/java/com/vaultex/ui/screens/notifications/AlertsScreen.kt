package com.vaultex.ui.screens.notifications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vaultex.data.local.entity.PriceAlertEntity
import com.vaultex.ui.theme.VaultExColors
import com.vaultex.ui.viewmodel.AlertsViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(navController: NavController) {
    val viewModel: AlertsViewModel = hiltViewModel()
    val alerts by viewModel.alerts.collectAsState()
    val currentPrices by viewModel.currentPricesXof.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alertes prix", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Ajouter une alerte", tint = VaultExColors.BluePrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultExColors.Background)
            )
        },
        containerColor = VaultExColors.Background
    ) { padding ->
        if (alerts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(64.dp), tint = VaultExColors.Border)
                    Spacer(Modifier.height(16.dp))
                    Text("Aucune alerte configurée", color = VaultExColors.TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { showAddDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VaultExColors.BluePrimary)
                    ) { Text("Créer une alerte") }
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(alerts, key = { it.id }) { alert ->
                    AlertCard(
                        alert = alert,
                        currentPriceXof = currentPrices[alert.tokenSymbol],
                        onToggle = { viewModel.toggleAlert(alert.id, it) },
                        onDelete = { viewModel.deleteAlert(alert.id) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddAlertDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { token, condition, target ->
                viewModel.createAlert(token, condition, target)
                showAddDialog = false
            }
        )
    }
}

private fun formatXof(value: Double): String =
    NumberFormat.getNumberInstance(Locale.FRANCE).format(value.toLong()) + " FCFA"

@Composable
private fun AlertCard(
    alert: PriceAlertEntity,
    currentPriceXof: Double?,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val targetFormatted = alert.targetPrice.toDoubleOrNull()?.let(::formatXof) ?: "${alert.targetPrice} FCFA"

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = VaultExColors.CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(alert.tokenSymbol, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (alert.isActive) {
                        Surface(shape = RoundedCornerShape(4.dp), color = VaultExColors.BlueLight) {
                            Text(
                                "Actif", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 11.sp, color = VaultExColors.BluePrimary
                            )
                        }
                    }
                }
                Text("Alerte ${alert.condition} $targetFormatted", fontSize = 13.sp, color = VaultExColors.TextSecondary)
                currentPriceXof?.let {
                    Text("Prix actuel : ${formatXof(it)}", fontSize = 12.sp, color = VaultExColors.TextSecondary)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Switch(
                    checked = alert.isActive, onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(checkedTrackColor = VaultExColors.BluePrimary)
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Supprimer l'alerte", tint = VaultExColors.Error, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun AddAlertDialog(onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    var token by remember { mutableStateOf("BTC") }
    var condition by remember { mutableStateOf("au-dessus de") }
    var target by remember { mutableStateOf("") }
    val tokens = listOf("BTC", "ETH", "BNB", "SOL", "TRX", "USDT")
    val conditions = listOf("au-dessus de", "en-dessous de")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvelle alerte") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Token", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tokens.take(3).forEach { t ->
                        FilterChip(selected = token == t, onClick = { token = t }, label = { Text(t) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tokens.drop(3).forEach { t ->
                        FilterChip(selected = token == t, onClick = { token = t }, label = { Text(t) })
                    }
                }
                Text("Condition", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                conditions.forEach { c ->
                    FilterChip(selected = condition == c, onClick = { condition = c }, label = { Text(c) })
                }
                OutlinedTextField(
                    value = target, onValueChange = { target = it },
                    label = { Text("Prix cible (FCFA)") }, singleLine = true,
                    shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(token, condition, target) },
                enabled = target.toDoubleOrNull() != null,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VaultExColors.BluePrimary)
            ) { Text("Créer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
