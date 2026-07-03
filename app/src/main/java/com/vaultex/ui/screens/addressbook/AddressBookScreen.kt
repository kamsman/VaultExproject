package com.vaultex.ui.screens.addressbook

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vaultex.R
import com.vaultex.data.local.entity.ContactEntity
import com.vaultex.ui.components.VaultExBottomBar
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.AccentRed
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.NetworkBnb
import com.vaultex.ui.theme.NetworkBtc
import com.vaultex.ui.theme.NetworkEth
import com.vaultex.ui.theme.NetworkSol
import com.vaultex.ui.theme.NetworkTrx
import com.vaultex.ui.theme.Surface as SurfaceColor
import com.vaultex.ui.theme.TextMuted
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary
import com.vaultex.ui.theme.VaultExColors
import com.vaultex.ui.viewmodel.AddressBookViewModel
import org.json.JSONObject

private fun chainColor(chain: String): Color = when (chain.uppercase()) {
    "ETH" -> NetworkEth
    "BTC" -> NetworkBtc
    "BNB" -> NetworkBnb
    "SOL" -> NetworkSol
    "TRX" -> NetworkTrx
    else -> Color(0xFF1A6FE8)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressBookScreen(navController: NavHostController) {
    val viewModel: AddressBookViewModel = hiltViewModel()
    val contacts by viewModel.contacts.collectAsState()
    val ui by viewModel.ui.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var contactToDelete by remember { mutableStateOf<ContactEntity?>(null) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

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

    contactToDelete?.let { contact ->
        AlertDialog(
            onDismissRequest = { contactToDelete = null },
            title = { Text(stringResource(R.string.address_book_delete_title)) },
            text = { Text(contact.name, fontWeight = FontWeight.SemiBold) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteContact(contact.id)
                    contactToDelete = null
                }) { Text(stringResource(R.string.delete), color = AccentRed) }
            },
            dismissButton = {
                TextButton(onClick = { contactToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.address_book),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = AccentBlue
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgPrimary)
            )
        },
        bottomBar = { VaultExBottomBar(navController) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::openAddDialog,
                shape = CircleShape,
                containerColor = AccentBlue,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.contacts_add))
            }
        },
        containerColor = BgPrimary
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            SearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = stringResource(R.string.contacts_search)
            )
            Spacer(Modifier.height(12.dp))

            // ─── Filtre par réseau (maquette) ───
            var chainFilter by remember { mutableStateOf("Tous") }
            val chains = listOf("Tous", "ETH", "BNB", "TRX", "SOL", "BTC", "USDT")
            Row(
                Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                chains.forEach { c ->
                    val sel = chainFilter == c
                    Box(
                        Modifier.clip(RoundedCornerShape(50))
                            .background(if (sel) AccentBlue else SurfaceColor)
                            .clickable { chainFilter = c }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(c, fontSize = 12.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                            color = if (sel) Color.White else TextSecondary)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            val filtered = remember(contacts, searchQuery, chainFilter) {
                contacts
                    .filter { searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) }
                    .filter { chainFilter == "Tous" || it.addressesJson.contains("\"$chainFilter\"", ignoreCase = true) }
            }

            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Contacts,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.contacts_empty), color = TextSecondary, fontSize = 15.sp)
                        Text(stringResource(R.string.contacts_empty_hint), color = TextMuted, fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(filtered, key = { it.id }) { contact ->
                        ContactCard(
                            contact,
                            onCopy = { addr ->
                                clipboard.setText(androidx.compose.ui.text.AnnotatedString(addr))
                                android.widget.Toast.makeText(ctx, R.string.copied, android.widget.Toast.LENGTH_SHORT).show()
                            },
                            onDelete = { contactToDelete = contact }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, VaultExColors.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(placeholder, color = TextMuted, fontSize = 14.sp)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(AccentBlue),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ContactCard(contact: ContactEntity, onCopy: (String) -> Unit, onDelete: () -> Unit) {
    val addresses = runCatching {
        val json = JSONObject(contact.addressesJson)
        json.keys().asSequence().map { chain -> chain to json.getString(chain) }.toList()
    }.getOrDefault(emptyList())

    val initials = contact.name
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceColor,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { addresses.firstOrNull()?.let { (_, a) -> onCopy(a) } }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color(contact.avatarColor)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    initials,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    contact.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextPrimary
                )
                addresses.firstOrNull()?.let { (_, addr) ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${addr.take(6)}…${addr.takeLast(4)}",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                if (addresses.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        addresses.forEach { (chain, _) ->
                            ChainBadge(chain)
                        }
                    }
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.delete),
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ChainBadge(chain: String) {
    val color = chainColor(chain)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        coil.compose.AsyncImage(
            model = com.vaultex.ui.components.CryptoIcon.url(chain),
            contentDescription = chain,
            modifier = Modifier.size(12.dp).clip(CircleShape)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            chain.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
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
        containerColor = SurfaceColor,
        title = { Text(stringResource(R.string.contacts_new), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.contacts_name)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = onAddressChange,
                    label = { Text(stringResource(R.string.contacts_address)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedTextField(
                        value = chain,
                        onValueChange = {},
                        label = { Text(stringResource(R.string.contacts_network)) },
                        readOnly = true,
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            IconButton(onClick = { expanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
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
                    Text(error, color = AccentRed, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) { Text(stringResource(R.string.add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = TextSecondary) }
        }
    )
}
