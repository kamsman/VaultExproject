package com.vaultex.ui.screens.security

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.vaultex.R
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.AccentOrange
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.Surface
import com.vaultex.ui.theme.SurfaceLight
import com.vaultex.ui.theme.TextMuted
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityNotificationsScreen(navController: NavHostController) {

    var txAlerts by rememberSaveable { mutableStateOf(true) }
    var loginAlerts by rememberSaveable { mutableStateOf(true) }
    var lowBalanceAlerts by rememberSaveable { mutableStateOf(false) }
    var pinChangeAlerts by rememberSaveable { mutableStateOf(true) }
    var threshold by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.notif_security_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = AccentBlue
                        )
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
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    NotifToggleRow(
                        icon = Icons.Default.Notifications,
                        title = stringResource(R.string.notif_tx_alerts),
                        checked = txAlerts,
                        onCheckedChange = { txAlerts = it }
                    )
                    NotifDivider()
                    NotifToggleRow(
                        icon = Icons.Default.Login,
                        title = stringResource(R.string.notif_login_alerts),
                        checked = loginAlerts,
                        onCheckedChange = { loginAlerts = it }
                    )
                    NotifDivider()
                    NotifToggleRow(
                        icon = Icons.Default.Warning,
                        title = stringResource(R.string.notif_low_balance),
                        iconTint = AccentOrange,
                        checked = lowBalanceAlerts,
                        onCheckedChange = { lowBalanceAlerts = it }
                    )
                    NotifDivider()
                    NotifToggleRow(
                        icon = Icons.Default.Pin,
                        title = stringResource(R.string.notif_pin_change),
                        checked = pinChangeAlerts,
                        onCheckedChange = { pinChangeAlerts = it }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                stringResource(R.string.notif_low_balance_threshold),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMuted,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TextField(
                    value = threshold,
                    onValueChange = { threshold = it },
                    placeholder = {
                        Text(
                            stringResource(R.string.notif_threshold_placeholder),
                            fontSize = 14.sp,
                            color = TextSecondary
                        )
                    },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = TextPrimary),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SurfaceLight,
                        unfocusedContainerColor = SurfaceLight,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = AccentBlue
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                )
            }

            Spacer(Modifier.height(20.dp))
            FcmTokenCard()
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Jeton FCM de l'appareil — sert à envoyer un push de TEST depuis la console
 * Firebase (Cloud Messaging → « Envoyer un message test ») sans backend.
 * Ne s'affiche que si Firebase est correctement configuré (vrai google-services.json).
 */
@Composable
private fun FcmTokenCard() {
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

    Text(
        "Jeton push (test)",
        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextMuted,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            when {
                token != null -> {
                    Text(
                        token!!,
                        fontSize = 11.sp, color = TextSecondary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            clipboard.setText(AnnotatedString(token!!))
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, null, tint = AccentBlue, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Copier le jeton", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AccentBlue)
                    }
                }
                failed -> Text(
                    "Firebase n'est pas encore configuré. Ajoutez le vrai google-services.json pour activer le push.",
                    fontSize = 12.sp, color = AccentOrange
                )
                else -> Text("Chargement du jeton…", fontSize = 12.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun NotifDivider() {
    HorizontalDivider(color = SurfaceLight, thickness = 1.dp)
}

@Composable
private fun NotifToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconTint: Color = AccentBlue
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            title,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccentBlue,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = SurfaceLight
            )
        )
    }
}
