package com.vaultex.ui.screens.addressbook

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vaultex.data.local.entity.ContactEntity
import com.vaultex.ui.theme.VaultExColors
import com.vaultex.ui.viewmodel.AddressBookViewModel
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressBookScreen(navController: NavHostController) {
    val viewModel: AddressBookViewModel = hiltViewModel()
    val contacts by viewModel.contacts.collectAsState()
    val ui by viewModel.ui.collectAsState()

    if (ui.showAddDialog) {
        AddContactDialog(
            name = ui.newName,
            address = ui.newAddress,
            chain = ui.newChain,
            error = ui.error,
            onNameChange = viewModel::setName,
            onAddressChange = viewModel::setAddress,
            onChainChange = viewModel::setChain,
            onSave = viewModel::saveContact,
            onDismiss = viewModel::closeDialog
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Carnet d'adresses", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::openAddDialog,
                containerColor = VaultExColors.BluePrimary
            ) {
                Icon(Icons.Default.Add, null, tint = Color.White)
            }
        },
        containerColor = VaultExColors.Background
    ) { padding ->
        if (contacts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Contacts, null, tint = VaultExColors.TextSecondary, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Aucun contact", color = VaultExColors.TextSecondary, fontSize = 15.sp)
                    Text("Appuyez sur + pour ajouter un contact", color = VaultExColors.TextSecondary, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(contacts, key = { it.id }) { contact ->
                    ContactCard(contact, onDelete = { viewModel.deleteContact(contact.id) })
                }
            }
        }
    }
}

@Composable
private fun ContactCard(contact: ContactEntity, onDelete: () -> Unit) {
    val addresses = runCatching {
        val json = JSONObject(contact.addressesJson)
        json.keys().asSequence().map { chain -> chain to json.getString(chain) }.toList()
    }.getOrDefault(emptyList())

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color(contact.avatarColor),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        contact.name.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(contact.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                addresses.forEach { (chain, addr) ->
                    Text(
                        "$chain: ${addr.take(12)}…${addr.takeLast(6)}",
                        fontSize = 12.sp,
                        color = VaultExColors.TextSecondary
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, null, tint = VaultExColors.Error, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun AddContactDialog(
    name: String,
    address: String,
    chain: String,
    error: String?,
    onNameChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onChainChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val chains = listOf("ETH", "BNB", "BTC", "SOL", "TRX", "USDT")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouveau contact") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Nom") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = onAddressChange,
                    label = { Text("Adresse") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedTextField(
                        value = chain,
                        onValueChange = {},
                        label = { Text("Réseau") },
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { expanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        chains.forEach { c ->
                            DropdownMenuItem(text = { Text(c) }, onClick = { onChainChange(c); expanded = false })
                        }
                    }
                }
                if (error != null) {
                    Text(error, color = VaultExColors.Error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave) { Text("Ajouter") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
