package com.vaultex.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.AccentOrange
import com.vaultex.ui.theme.AccentRed
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.BgTertiary
import com.vaultex.ui.theme.BorderColor
import com.vaultex.ui.theme.Surface as SurfaceColor
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary
import com.vaultex.ui.viewmodel.BackupViewModel

@OptIn(ExperimentalMaterial3Api::class)
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
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.backup), fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back), tint = AccentBlue)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgPrimary)
            )
        },
        containerColor = BgPrimary
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Bandeau d'avertissement
            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xFFFFF4E5)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(4.dp).height(40.dp).background(AccentOrange))
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.backup_device_warning),
                    fontSize = 12.sp,
                    color = TextPrimary
                )
            }

            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ─── Phrase de récupération ───
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                    border = BorderStroke(1.dp, BorderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(BgTertiary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                stringResource(R.string.backup_recovery_phrase),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = TextPrimary
                            )
                        }
                        Text(
                            stringResource(R.string.backup_pin_required),
                            fontSize = 13.sp,
                            color = TextSecondary
                        )

                        if (state.isRevealed) {
                            val words = (state.mnemonic ?: "").split(" ").filter { it.isNotEmpty() }
                            if (words.isEmpty()) {
                                Text(stringResource(R.string.backup_load_error), color = AccentRed, fontSize = 13.sp)
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    words.chunked(2).forEachIndexed { rowIdx, rowWords ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            rowWords.forEachIndexed { colIdx, word ->
                                                MnemonicChip(rowIdx * 2 + colIdx + 1, word, Modifier.weight(1f))
                                            }
                                            if (rowWords.size == 1) Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bouton afficher / masquer
                if (!state.isRevealed) {
                    OutlinedButton(
                        onClick = viewModel::requestReveal,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.5.dp, AccentBlue)
                    ) {
                        Text(
                            stringResource(R.string.backup_show_phrase),
                            color = AccentBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = viewModel::hide,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.5.dp, AccentRed)
                    ) {
                        Text(
                            stringResource(R.string.backup_hide),
                            color = AccentRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // ─── Exporter clé privée ───
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                    border = BorderStroke(1.dp, BorderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(BgTertiary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Key, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                stringResource(R.string.backup_export_key),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = TextPrimary
                            )
                        }
                        Text(
                            stringResource(R.string.backup_select_chain),
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf("BTC", "ETH", "BNB").forEach { chain ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = SurfaceColor,
                                    border = BorderStroke(1.dp, BorderColor),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        chain,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
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
                                .background(if (i < pin.length) AccentBlue else BorderColor)
                        )
                    }
                }
                if (error != null) {
                    Text(error, color = AccentRed, fontSize = 12.sp, textAlign = TextAlign.Center)
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
private fun MnemonicChip(index: Int, word: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(BgTertiary)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$index.", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.width(24.dp))
        Text(word, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
    }
}
