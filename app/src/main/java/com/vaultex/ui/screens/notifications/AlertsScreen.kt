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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.vaultex.ui.theme.VaultExColors

@Composable
fun AlertsScreen(navController: NavController) {
    data class PriceAlert(
        val id: String,
        val token: String,
        val condition: String,
        val target: String,
        val current: String,
        var isActive: Boolean
    )

    val alerts = remember {
        mutableStateListOf(
            PriceAlert("1", "BTC", "au-dessus de", "35 000 000 FCFA", "34 200 000 FCFA", true),
            PriceAlert("2", "ETH", "en-dessous de", "2 500 000 FCFA", "2 770 000 FCFA", true),
            PriceAlert("3", "SOL", "au-dessus de", "100 000 FCFA", "62 500 FCFA", false),
        )
    }

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
                        Icon(Icons.Default.Add, contentDescription = "Ajouter", tint = VaultExColors.BluePrimary)
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
                    Icon(Icons.Default.Notifications, null, modifier = Modifier.size(64.dp), tint = VaultExColors.Border)
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
                items(alerts) { alert ->
                    AlertCard(
                        alert = alert,
                        onToggle = { alert.isActive = it },
                        onDelete = { alerts.remove(alert) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddAlertDialog(onDismiss = { showAddDialog = false }, onConfirm = { _, _, _ -> showAddDialog = false })
    }
}

@Composable
private fun AlertCard(
    alert: Any,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    @Suppress("UNCHECKED_CAST")
    data class AlertData(
        val token: String, val condition: String, val target: String,
        val current: String, val isActive: Boolean
    )

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = VaultExColors.CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("BTC", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Surface(shape = RoundedCornerShape(4.dp), color = VaultExColors.BlueLight) {
                        Text("Actif", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 11.sp, color = VaultExColors.BluePrimary)
                    }
                }
                Text("Alerte au-dessus de 35 000 000 FCFA", fontSize = 13.sp, color = VaultExColors.TextSecondary)
                Text("Prix actuel : 34 200 000 FCFA", fontSize = 12.sp, color = VaultExColors.TextSecondary)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Switch(checked = true, onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(checkedTrackColor = VaultExColors.BluePrimary))
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, null, tint = VaultExColors.Error, modifier = Modifier.size(18.dp))
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
                enabled = target.isNotEmpty(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VaultExColors.BluePrimary)
            ) { Text("Créer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
