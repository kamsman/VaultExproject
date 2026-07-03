package com.vaultex.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vaultex.R
import com.vaultex.core.security.BiometricHelper
import com.vaultex.ui.components.CryptoIcon
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.AccentRed
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.BgTertiary
import com.vaultex.ui.theme.BorderColor
import com.vaultex.ui.theme.Surface as SurfaceColor
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary
import com.vaultex.ui.viewmodel.BackupAuthAction
import com.vaultex.ui.viewmodel.BackupViewModel

/* Accents fixes du prototype (fonds/textes = thème actuel). */
private val P = Color(0xFF7C5CFC)          // violet
private val Warn = Color(0xFFF59E0B)       // jaune avertissement
private val Ok = Color(0xFF22C55E)         // vert statut

private val EXPORT_CHAINS = listOf("BTC", "ETH", "BNB", "SOL", "TRX")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(navController: NavController) {
    val viewModel: BackupViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current

    // Biométrie (empreinte) en alternative au PIN, comme la maquette.
    val activity = LocalContext.current as? androidx.fragment.app.FragmentActivity
    val biometricHelper = remember { activity?.let { BiometricHelper(it) } }
    val bioAvailable = remember {
        biometricHelper?.checkAvailability() == BiometricHelper.BiometricStatus.AVAILABLE
    }

    if (state.showPinDialog) {
        SecurityVerifDialog(
            subtitle = stringResource(
                if (state.pendingAction == BackupAuthAction.KEY) R.string.backup_verif_key
                else R.string.backup_verif_phrase
            ),
            pin = state.pinInput,
            error = state.pinError,
            showFingerprint = bioAvailable,
            onDigit = { viewModel.setPinInput(state.pinInput + it) },
            onBackspace = { if (state.pinInput.isNotEmpty()) viewModel.setPinInput(state.pinInput.dropLast(1)) },
            onFingerprint = {
                biometricHelper?.authenticateStrongOrCredential(
                    title = activity?.getString(R.string.backup_verif_title) ?: "",
                    subtitle = "",
                    onSuccess = { viewModel.onAuthSuccess() },
                    onError = { _, _ -> }
                )
            },
            onDismiss = viewModel::dismissPinDialog
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.backup), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.back), tint = P)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgPrimary)
            )
        },
        containerColor = BgPrimary
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ─── Bandeau « Sauvegarde avant de changer d'appareil » ───
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = P.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, P.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, null, tint = P, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.backup_device_warning), fontSize = 13.sp, color = TextPrimary)
                }
            }

            // ─── Phrase de récupération ───
            Surface(shape = RoundedCornerShape(16.dp), color = SurfaceColor, border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(P.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Lock, null, tint = P, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.backup_recovery_phrase), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                            Text(stringResource(R.string.backup_phrase_desc), fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp)
                        }
                    }

                    if (!state.isRevealed) {
                        Button(
                            onClick = viewModel::requestReveal,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = P, contentColor = Color.White)
                        ) {
                            Text(stringResource(R.string.backup_show_phrase), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.Visibility, null, modifier = Modifier.size(18.dp))
                        }
                    } else {
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
                        OutlinedButton(
                            onClick = viewModel::hide,
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, AccentRed)
                        ) { Text(stringResource(R.string.backup_hide), color = AccentRed, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                    }

                    // « Garde-la en sécurité » (3 puces)
                    Surface(shape = RoundedCornerShape(12.dp), color = Warn.copy(alpha = 0.08f), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.WarningAmber, null, tint = Warn, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.backup_keep_safe_title), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Warn)
                            }
                            Text("•  " + stringResource(R.string.backup_keep_safe_1), fontSize = 12.sp, color = TextSecondary)
                            Text("•  " + stringResource(R.string.backup_keep_safe_2), fontSize = 12.sp, color = TextSecondary)
                            Text("•  " + stringResource(R.string.backup_keep_safe_3), fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                }
            }

            // ─── Exporter la clé privée ───
            Surface(shape = RoundedCornerShape(16.dp), color = SurfaceColor, border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(P.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Key, null, tint = P, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.backup_export_key), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                            Text(stringResource(R.string.backup_export_desc), fontSize = 12.sp, color = TextSecondary)
                        }
                    }

                    // Sélecteur de chaîne (BTC / ETH / BNB / SOL / TRX)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EXPORT_CHAINS.forEach { chain ->
                            val selected = chain == state.selectedChain
                            Surface(
                                onClick = { viewModel.selectChain(chain) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) P.copy(alpha = 0.10f) else BgTertiary,
                                border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) P else BorderColor)
                            ) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    coil.compose.AsyncImage(
                                        model = CryptoIcon.url(chain), contentDescription = chain,
                                        modifier = Modifier.size(20.dp).clip(CircleShape)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(chain, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                                    if (selected) {
                                        Spacer(Modifier.width(6.dp))
                                        Box(Modifier.size(16.dp).clip(CircleShape).background(P), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(11.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (state.exportedKey == null) {
                        // « Continuer pour exporter la clé privée »
                        Surface(
                            onClick = viewModel::requestExport,
                            shape = RoundedCornerShape(12.dp),
                            color = P.copy(alpha = 0.10f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.IosShare, null, tint = P, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(stringResource(R.string.backup_export_continue), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ChevronRight, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    } else {
                        // Clé exportée : affichage monospace + copie + avertissement
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.backup_key_of, state.selectedChain), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                            Surface(shape = RoundedCornerShape(10.dp), color = BgTertiary, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    state.exportedKey!!,
                                    fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = TextPrimary,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                    .clickable { clipboard.setText(AnnotatedString(state.exportedKey!!)) }
                                    .padding(6.dp)) {
                                Icon(Icons.Default.ContentCopy, null, tint = P, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.copy), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = P)
                                Spacer(Modifier.weight(1f))
                                Text(stringResource(R.string.backup_hide), fontSize = 13.sp, color = AccentRed,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { viewModel.hideKey() }.padding(4.dp))
                            }
                            Text(stringResource(R.string.backup_key_warning), fontSize = 11.sp, color = AccentRed, lineHeight = 15.sp)
                        }
                    }
                }
            }

            // ─── État de sécurité ───
            Surface(shape = RoundedCornerShape(16.dp), color = SurfaceColor, border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, null, tint = P, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.backup_security_status), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                    }
                    StatusRow(Icons.Default.Pin, stringResource(R.string.backup_status_pin), true)
                    StatusRow(Icons.Default.Shield, stringResource(R.string.backup_status_phrase), state.phraseBackedUp)
                    StatusRow(Icons.Default.Fingerprint, stringResource(R.string.backup_status_bio), bioAvailable)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Ligne d'état : icône + libellé + coche verte (ok) ou cercle vide. */
@Composable
private fun StatusRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f))
        if (ok) {
            Box(Modifier.size(20.dp).clip(CircleShape).background(Ok.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, null, tint = Ok, modifier = Modifier.size(13.dp))
            }
        } else {
            Surface(shape = CircleShape, color = Color.Transparent, border = BorderStroke(1.5.dp, TextSecondary.copy(alpha = 0.4f)), modifier = Modifier.size(18.dp)) {}
        }
    }
}

/**
 * Vérification de sécurité (maquette) : bouclier violet, 6 points, pavé
 * numérique en cercles, « ou » + empreinte, Annuler.
 */
@Composable
private fun SecurityVerifDialog(
    subtitle: String,
    pin: String,
    error: String?,
    showFingerprint: Boolean,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onFingerprint: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = BgPrimary,
            border = BorderStroke(1.dp, P.copy(alpha = 0.4f))
        ) {
            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Bouclier + cadenas violet
                Box(Modifier.size(64.dp).clip(CircleShape).background(P.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Lock, null, tint = P, modifier = Modifier.size(28.dp))
                }
                Text(stringResource(R.string.backup_verif_title), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextPrimary)
                Text(subtitle, fontSize = 13.sp, color = TextSecondary, textAlign = TextAlign.Center, lineHeight = 18.sp)

                // 6 points
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(6) { i ->
                        Box(
                            Modifier.size(11.dp).clip(CircleShape)
                                .background(if (i < pin.length) P else TextSecondary.copy(alpha = 0.25f))
                        )
                    }
                }
                if (error != null) Text(error, color = AccentRed, fontSize = 12.sp, textAlign = TextAlign.Center)

                // Pavé numérique (cercles à bordure, comme la maquette)
                listOf(listOf("1","2","3"), listOf("4","5","6"), listOf("7","8","9"), listOf("", "0", "⌫")).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        row.forEach { d ->
                            if (d.isEmpty()) Spacer(Modifier.size(58.dp))
                            else Surface(
                                onClick = { if (d == "⌫") onBackspace() else onDigit(d) },
                                shape = CircleShape, color = Color.Transparent,
                                border = BorderStroke(1.dp, P.copy(alpha = 0.35f)),
                                modifier = Modifier.size(58.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (d == "⌫") Icon(Icons.Default.Backspace, null, tint = TextPrimary, modifier = Modifier.size(20.dp))
                                    else Text(d, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                }
                            }
                        }
                    }
                }

                if (showFingerprint) {
                    // ── ou ──
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        HorizontalDivider(Modifier.weight(1f), color = BorderColor)
                        Text("  " + stringResource(R.string.backup_or) + "  ", fontSize = 12.sp, color = TextSecondary)
                        HorizontalDivider(Modifier.weight(1f), color = BorderColor)
                    }
                    OutlinedButton(
                        onClick = onFingerprint,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, P.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = P)
                    ) {
                        Icon(Icons.Default.Fingerprint, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.backup_use_fingerprint), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }

                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel), color = P, fontSize = 14.sp)
                }
            }
        }
    }
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
