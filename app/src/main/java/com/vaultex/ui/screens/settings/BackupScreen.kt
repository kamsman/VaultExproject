package com.vaultex.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vaultex.R
import com.vaultex.ui.theme.VaultExColors
import com.vaultex.ui.viewmodel.BackupViewModel

@Composable
fun BackupScreen(navController: NavController) {
    val viewModel: BackupViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    if (state.showPinDialog) {
        PinConfirmDialog(
            pin = state.pinInput,
            error = state.pinError,
            onDigit = viewModel::setPinInput,
            onBackspace = { val p = state.pinInput; if (p.isNotEmpty()) viewModel.setPinInput(p.dropLast(1)) },
            onDismiss = viewModel::dismissPinDialog
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultExColors.Background)
            )
        },
        containerColor = VaultExColors.Background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(color = Color(0xFFFFF3CD), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Warning, null, tint = Color(0xFFB45309), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(stringResource(R.string.backup_warning_title), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF92400E))
                        Text(stringResource(R.string.backup_warning_body), fontSize = 13.sp, color = Color(0xFF92400E))
                    }
                }
            }

            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = VaultExColors.CardBackground)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, tint = VaultExColors.BluePrimary, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.backup_recovery_phrase), fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.backup_bip39_subtitle), fontSize = 12.sp, color = VaultExColors.TextSecondary)
                        }
                    }

                    if (!state.isRevealed) {
                        Button(
                            onClick = viewModel::requestReveal,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VaultExColors.BluePrimary)
                        ) {
                            Icon(Icons.Default.Visibility, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.backup_reveal))
                        }
                    } else {
                        val words = (state.mnemonic ?: "").split(" ").filter { it.isNotEmpty() }
                        if (words.isEmpty()) {
                            Text(stringResource(R.string.backup_load_error), color = VaultExColors.Error, fontSize = 13.sp)
                        } else {
                            Column(
                                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                    .background(VaultExColors.Background).padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                words.chunked(3).forEachIndexed { rowIdx, rowWords ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        rowWords.forEachIndexed { colIdx, word ->
                                            MnemonicWord(rowIdx * 3 + colIdx + 1, word, Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                        Button(
                            onClick = viewModel::hide,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VaultExColors.Error)
                        ) { Text(stringResource(R.string.backup_hide)) }
                    }
                }
            }

            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = VaultExColors.CardBackground)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.backup_checklist_title), fontWeight = FontWeight.SemiBold)
                    ChecklistItem(stringResource(R.string.backup_check_write))
                    ChecklistItem(stringResource(R.string.backup_check_store))
                    ChecklistItem(stringResource(R.string.backup_check_no_photo))
                    ChecklistItem(stringResource(R.string.backup_check_no_share))
                    ChecklistItem(stringResource(R.string.backup_check_test))
                }
            }
        }
    }
}

@Composable
private fun PinConfirmDialog(
    pin: String,
    error: String?,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pin_confirm), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(6) { i ->
                        Box(
                            modifier = Modifier.size(12.dp).clip(CircleShape)
                                .background(if (i < pin.length) VaultExColors.BluePrimary else VaultExColors.Border)
                        )
                    }
                }
                if (error != null) {
                    Text(error, color = VaultExColors.Error, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
                val digits = listOf("1","2","3","4","5","6","7","8","9","","0","⌫")
                digits.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { d ->
                            if (d.isEmpty()) {
                                Spacer(Modifier.size(56.dp))
                            } else {
                                FilledTonalButton(
                                    onClick = { if (d == "⌫") onBackspace() else onDigit(pin + d) },
                                    modifier = Modifier.size(56.dp),
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(0.dp)
                                ) { Text(d, fontSize = 18.sp, fontWeight = FontWeight.Medium) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun MnemonicWord(index: Int, word: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(VaultExColors.BlueLight).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$index.", fontSize = 11.sp, color = VaultExColors.TextSecondary, modifier = Modifier.width(20.dp))
        Text(word, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ChecklistItem(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CheckCircle, null, tint = VaultExColors.Success, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 13.sp, color = VaultExColors.TextSecondary)
    }
}
