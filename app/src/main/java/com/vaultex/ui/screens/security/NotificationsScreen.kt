package com.vaultex.ui.screens.security

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vaultex.R
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.AccentGreen
import com.vaultex.ui.theme.AccentOrange
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.BgTertiary
import com.vaultex.ui.theme.BorderColor
import com.vaultex.ui.theme.Surface as SurfaceColor
import com.vaultex.ui.theme.SurfaceLight
import com.vaultex.ui.theme.TextMuted
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary
import com.vaultex.ui.viewmodel.SecurityNotifViewModel
import java.text.NumberFormat
import java.util.Locale

private val P = Color(0xFF7C5CFC)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityNotificationsScreen(navController: NavHostController) {
    val viewModel: SecurityNotifViewModel = hiltViewModel()
    val txAlerts by viewModel.txAlerts.collectAsState()
    val loginAlerts by viewModel.loginAlerts.collectAsState()
    val lowBalanceAlerts by viewModel.lowBalanceAlerts.collectAsState()
    val pinChangeAlerts by viewModel.pinChangeAlerts.collectAsState()
    val thresholdXof by viewModel.thresholdXof.collectAsState()

    val enabledCount = listOf(txAlerts, loginAlerts, lowBalanceAlerts, pinChangeAlerts).count { it }
    var showDeviceDialog by remember { mutableStateOf(false) }

    if (showDeviceDialog) {
        AlertDialog(
            onDismissRequest = { showDeviceDialog = false },
            title = { Text(stringResource(R.string.secnotif_devices), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.secnotif_device_body,
                        "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"),
                    fontSize = 13.sp, lineHeight = 19.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showDeviceDialog = false }) { Text(stringResource(R.string.close)) }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.notif_security_title), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, stringResource(R.string.back), tint = P)
                    }
                },
                actions = {
                    Icon(Icons.Default.Shield, null, tint = P, modifier = Modifier.padding(end = 14.dp).size(20.dp))
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ─── En-tête : activées X/4 + niveau de sécurité ───
            Surface(shape = RoundedCornerShape(16.dp), color = SurfaceColor, border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp)) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(P.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.NotificationsActive, null, tint = P, modifier = Modifier.size(19.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(stringResource(R.string.secnotif_enabled_title), fontSize = 11.sp, color = TextSecondary)
                            Text("$enabledCount / 4", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = P)
                            Text(stringResource(R.string.secnotif_protected), fontSize = 10.sp, color = TextSecondary)
                        }
                    }
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(AccentGreen.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Shield, null, tint = AccentGreen, modifier = Modifier.size(19.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(stringResource(R.string.secnotif_level), fontSize = 11.sp, color = TextSecondary)
                            val (levelRes, levelColor) = when {
                                enabledCount >= 4 -> R.string.secnotif_level_excellent to AccentGreen
                                enabledCount >= 2 -> R.string.secnotif_level_good to AccentGreen
                                else -> R.string.secnotif_level_low to AccentOrange
                            }
                            Text(stringResource(levelRes), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = levelColor)
                            Text(stringResource(R.string.secnotif_no_risk), fontSize = 10.sp, color = TextSecondary)
                        }
                    }
                }
            }

            // ─── ALERTES ───
            Text(stringResource(R.string.secnotif_section_alerts).uppercase(), fontSize = 12.sp,
                fontWeight = FontWeight.Bold, color = TextMuted, modifier = Modifier.padding(start = 4.dp, top = 4.dp))

            AlertCard(Icons.Default.Send, P, stringResource(R.string.notif_tx_alerts),
                stringResource(R.string.secnotif_tx_desc), txAlerts, viewModel::setTx)

            AlertCard(Icons.Default.Login, AccentBlue, stringResource(R.string.notif_login_alerts),
                stringResource(R.string.secnotif_login_desc), loginAlerts, viewModel::setLogin)

            // Solde bas + seuil éditable
            Surface(shape = RoundedCornerShape(16.dp), color = SurfaceColor, border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AlertRowContent(Icons.Default.WarningAmber, AccentOrange, stringResource(R.string.notif_low_balance),
                        stringResource(R.string.secnotif_lowbal_desc), lowBalanceAlerts, viewModel::setLowBalance)
                    if (lowBalanceAlerts) {
                        Surface(shape = RoundedCornerShape(12.dp), color = BgTertiary, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(R.string.secnotif_threshold_label), fontSize = 12.sp, color = TextSecondary)
                                var editing by remember { mutableStateOf(false) }
                                var draft by remember(thresholdXof) { mutableStateOf(thresholdXof.toString()) }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = RoundedCornerShape(10.dp), color = SurfaceColor,
                                        border = BorderStroke(1.dp, if (editing) P else BorderColor), modifier = Modifier.weight(1f)) {
                                        if (editing) {
                                            BasicTextField(
                                                value = draft,
                                                onValueChange = { draft = it.filter { c -> c.isDigit() }.take(12) },
                                                singleLine = true,
                                                textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary),
                                                cursorBrush = SolidColor(P),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
                                            )
                                        } else {
                                            Text(
                                                NumberFormat.getNumberInstance(com.vaultex.core.session.LocaleManager.appLocale()).format(thresholdXof) + " XOF",
                                                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        onClick = {
                                            if (editing) draft.toLongOrNull()?.let { viewModel.setThreshold(it) }
                                            editing = !editing
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = P.copy(alpha = 0.14f),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(if (editing) Icons.Default.Check else Icons.Default.Edit, null, tint = P, modifier = Modifier.size(17.dp))
                                        }
                                    }
                                }
                                Text(stringResource(R.string.secnotif_threshold_hint), fontSize = 11.sp, color = TextSecondary)
                            }
                        }
                    }
                }
            }

            AlertCard(Icons.Default.Pin, AccentGreen, stringResource(R.string.notif_pin_change),
                stringResource(R.string.secnotif_pin_desc), pinChangeAlerts, viewModel::setPinChange)

            // ─── CENTRE DE SÉCURITÉ ───
            Text(stringResource(R.string.secnotif_center).uppercase(), fontSize = 12.sp,
                fontWeight = FontWeight.Bold, color = TextMuted, modifier = Modifier.padding(start = 4.dp, top = 4.dp))

            Surface(shape = RoundedCornerShape(16.dp), color = SurfaceColor, border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth()) {
                Column {
                    CenterRow(Icons.Default.Fingerprint, P, stringResource(R.string.secnotif_bio), stringResource(R.string.secnotif_bio_sub)) {
                        navController.navigate(Routes.BIOMETRIC_SETUP)
                    }
                    HorizontalDivider(color = SurfaceLight)
                    CenterRow(Icons.Default.Lock, AccentBlue, stringResource(R.string.secnotif_pin_change), stringResource(R.string.secnotif_pin_change_sub)) {
                        // Passe par la vérification du PIN actuel (comme l'écran Sécurité).
                        navController.navigate(Routes.PIN_CHANGE_VERIFY)
                    }
                    HorizontalDivider(color = SurfaceLight)
                    CenterRow(Icons.Default.History, AccentGreen, stringResource(R.string.secnotif_history), stringResource(R.string.secnotif_history_sub)) {
                        navController.navigate(Routes.LOGIN_HISTORY)
                    }
                    HorizontalDivider(color = SurfaceLight)
                    CenterRow(Icons.Default.Devices, P, stringResource(R.string.secnotif_devices), stringResource(R.string.secnotif_devices_sub)) {
                        showDeviceDialog = true
                    }
                }
            }

            // ─── État des notifications ───
            Surface(shape = RoundedCornerShape(16.dp), color = SurfaceColor, border = BorderStroke(1.dp, P.copy(alpha = 0.30f)), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(P.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Shield, null, tint = P, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.secnotif_state_title), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                        Text(stringResource(R.string.secnotif_state_sub), fontSize = 11.sp, color = TextSecondary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Surface(shape = RoundedCornerShape(8.dp), color = AccentGreen.copy(alpha = 0.14f)) {
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.secnotif_uptodate), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.Check, null, tint = AccentGreen, modifier = Modifier.size(12.dp))
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(stringResource(R.string.secnotif_saved_local), fontSize = 10.sp, color = TextSecondary)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

/** Carte d'alerte : icône colorée encadrée + titre + description + interrupteur. */
@Composable
private fun AlertCard(
    icon: ImageVector, tint: Color, title: String, desc: String,
    checked: Boolean, onChecked: (Boolean) -> Unit
) {
    Surface(shape = RoundedCornerShape(16.dp), color = SurfaceColor, border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.padding(14.dp)) { AlertRowContent(icon, tint, title, desc, checked, onChecked) }
    }
}

@Composable
private fun AlertRowContent(
    icon: ImageVector, tint: Color, title: String, desc: String,
    checked: Boolean, onChecked: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
            Text(desc, fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = AccentBlue,
                uncheckedThumbColor = Color.White, uncheckedTrackColor = SurfaceLight
            )
        )
    }
}

/** Ligne du centre de sécurité (icône + titre + sous-titre + chevron). */
@Composable
private fun CenterRow(icon: ImageVector, tint: Color, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(tint.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
            Text(sub, fontSize = 11.sp, color = TextSecondary)
        }
        Icon(Icons.Default.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(18.dp))
    }
}

/**
 * Jeton FCM de l'appareil — conservé pour les tests push depuis la console
 * Firebase (accessible via le centre de notifications si besoin).
 */
@Composable
fun FcmTokenCard() {
    val clipboard = LocalClipboardManager.current
    var token by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnCompleteListener { t ->
                    if (t.isSuccessful && !t.result.isNullOrBlank()) token = t.result else failed = true
                }
        } catch (_: Exception) { failed = true }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            when {
                token != null -> {
                    Text(token!!, fontSize = 11.sp, color = TextSecondary, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { clipboard.setText(AnnotatedString(token!!)) }
                    ) {
                        Icon(Icons.Default.ContentCopy, null, tint = AccentBlue, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Copier le jeton", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AccentBlue)
                    }
                }
                failed -> Text(
                    "Firebase n'est pas encore configuré.",
                    fontSize = 12.sp, color = AccentOrange
                )
                else -> Text("Chargement du jeton…", fontSize = 12.sp, color = TextSecondary)
            }
        }
    }
}
