package com.vaultex.ui.screens.addressbook

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vaultex.data.local.entity.ContactEntity
import com.vaultex.ui.theme.*
import com.vaultex.ui.viewmodel.ContactViewModel
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressBookScreen(navController: NavHostController) {

    val viewModel: ContactViewModel = hiltViewModel()
    val contacts by viewModel.contacts.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Carnet d'adresses", color = TextPrimary) },
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

            if (contacts.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Person, contentDescription = null,
                            tint = TextMuted, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Aucun contact", color = TextSecondary, fontSize = 16.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("Appuyez sur + pour ajouter", color = TextMuted, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(contacts, key = { it.id }) { contact ->
                        ContactRow(contact = contact, onDelete = { viewModel.deleteContact(contact.id) })
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddContactDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, addresses ->
                viewModel.addContact(name, addresses)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun ContactRow(contact: ContactEntity, onDelete: () -> Unit) {
    val firstAddress = runCatching {
        val json = JSONObject(contact.addressesJson)
        val key = json.keys().next()
        "$key: ${json.getString(key)}"
    }.getOrDefault("")

    val avatarColor = Color(contact.avatarColor)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BgSecondary)
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(avatarColor.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Text(contact.name.take(1).uppercase(), color = Color.White,
                    fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(contact.name, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                if (firstAddress.isNotEmpty()) {
                    Text(
                        firstAddress.let { if (it.length > 36) "${it.take(20)}…${it.takeLast(8)}" else it },
                        color = TextMuted, fontSize = 12.sp
                    )
                }
                contact.notes?.let { Text(it, color = TextSecondary, fontSize = 12.sp) }
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = AccentRed)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddContactDialog(onDismiss: () -> Unit, onConfirm: (String, Map<String, String>) -> Unit) {
    var name by remember { mutableStateOf("") }
    var selectedChain by remember { mutableStateOf("ETH") }
    var address by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val chains = listOf("BTC", "ETH", "BNB", "SOL", "TRX")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgSecondary,
        title = { Text("Nouveau contact", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom", color = TextSecondary) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors()
                )

                Text("Réseau", color = AccentGold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    chains.forEach { chain ->
                        FilterChip(
                            selected = selectedChain == chain,
                            onClick = { selectedChain = chain },
                            label = { Text(chain, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentGold, selectedLabelColor = BgPrimary,
                                containerColor = BgPrimary, labelColor = TextSecondary
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Adresse $selectedChain", color = TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optionnel)", color = TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && address.isNotBlank()) {
                        onConfirm(name, mapOf(selectedChain to address))
                    }
                },
                enabled = name.isNotBlank() && address.isNotBlank()
            ) {
                Text("Ajouter", color = AccentGold, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler", color = TextSecondary) }
        }
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AccentGold,
    unfocusedBorderColor = BorderColor,
    focusedContainerColor = BgPrimary,
    unfocusedContainerColor = BgPrimary,
    cursorColor = AccentGold,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)
