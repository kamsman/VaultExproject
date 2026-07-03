package com.vaultex.ui.screens.help

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.vaultex.R
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.BorderColor
import com.vaultex.ui.theme.Surface as SurfaceColor
import com.vaultex.ui.theme.SurfaceLight
import com.vaultex.ui.theme.TextMuted
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary

/** Canaux de contact du support VaultEx. */
private const val SUPPORT_EMAIL = "support@vaultex.app"
// Les mails partent vers les deux adresses (aucun message perdu si l'une n'est pas relevée).
private const val SUPPORT_EMAILS = "support@vaultex.app,mouminekoanda0@gmail.com"
private const val SUPPORT_WHATSAPP = "22672306526"           // wa.me : international sans « + »
private const val SUPPORT_TELEGRAM = "https://t.me/vaultexWallet"
private const val SUPPORT_PHONE = "+226 79 38 84 55"
private const val SUPPORT_PHONE_DIAL = "+22679388455"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(navController: NavHostController) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }

    // FAQ : (question, réponse) — libellés localisés (FR/EN/AR).
    val faq = listOf(
        stringResource(R.string.help_q_create) to stringResource(R.string.help_a_create),
        stringResource(R.string.help_q_send) to stringResource(R.string.help_a_send),
        stringResource(R.string.help_q_receive) to stringResource(R.string.help_a_receive),
        stringResource(R.string.help_q_swap) to stringResource(R.string.help_a_swap),
        stringResource(R.string.help_q_fees) to stringResource(R.string.help_a_fees),
        stringResource(R.string.help_q_secure) to stringResource(R.string.help_a_secure),
        stringResource(R.string.help_q_recover) to stringResource(R.string.help_a_recover),
        stringResource(R.string.help_q_momo) to stringResource(R.string.help_a_momo)
    )
    val filtered = if (query.isBlank()) faq
        else faq.filter { it.first.contains(query, true) || it.second.contains(query, true) }

    Scaffold(
        containerColor = BgPrimary,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_help), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.back), tint = AccentBlue)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgPrimary)
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ─── Recherche ───
            Surface(shape = RoundedCornerShape(14.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 14.sp, color = TextPrimary),
                        cursorBrush = SolidColor(AccentBlue),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (query.isEmpty()) Text(stringResource(R.string.help_search), fontSize = 14.sp, color = TextMuted)
                            inner()
                        }
                    )
                }
            }

            // ─── FAQ ───
            Text(stringResource(R.string.help_faq_title), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = TextSecondary, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
            if (filtered.isEmpty()) {
                Text(stringResource(R.string.help_no_result), fontSize = 13.sp, color = TextSecondary,
                    modifier = Modifier.padding(start = 4.dp))
            } else {
                filtered.forEach { (q, a) -> FaqCard(q, a) }
            }

            // ─── Contact ───
            Text(stringResource(R.string.help_contact_support), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = TextSecondary, modifier = Modifier.padding(start = 4.dp, top = 10.dp))

            ContactRow(
                icon = Icons.Default.Chat,
                iconBg = Color(0xFF22C55E),
                title = stringResource(R.string.help_whatsapp),
                subtitle = stringResource(R.string.help_whatsapp_subtitle)
            ) {
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://wa.me/$SUPPORT_WHATSAPP"))
                    )
                }
            }
            ContactRow(
                icon = Icons.Default.Send,
                iconBg = Color(0xFF229ED9),
                title = "Telegram",
                subtitle = "@vaultexWallet"
            ) {
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(SUPPORT_TELEGRAM))
                    )
                }
            }
            ContactRow(
                icon = Icons.Default.Email,
                iconBg = AccentBlue,
                title = stringResource(R.string.help_email),
                subtitle = SUPPORT_EMAIL
            ) {
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_SENDTO,
                            android.net.Uri.parse("mailto:$SUPPORT_EMAILS?subject=VaultEx%20Support"))
                    )
                }
            }
            ContactRow(
                icon = Icons.Default.Call,
                iconBg = Color(0xFF7C5CFC),
                title = stringResource(R.string.help_call),
                subtitle = SUPPORT_PHONE
            ) {
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_DIAL,
                            android.net.Uri.parse("tel:$SUPPORT_PHONE_DIAL"))
                    )
                }
            }

            // ─── Version ───
            Text(
                stringResource(R.string.help_version, com.vaultex.BuildConfig.VERSION_NAME),
                fontSize = 12.sp, color = TextMuted,
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/** Carte FAQ dépliante (question + chevron ; réponse animée). */
@Composable
private fun FaqCard(question: String, answer: String) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(14.dp), color = SurfaceColor,
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Column(Modifier.clickable { expanded = !expanded }.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(AccentBlue.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.HelpOutline, null, tint = AccentBlue, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(question, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ExpandMore, null, tint = TextSecondary,
                    modifier = Modifier.size(20.dp).rotate(if (expanded) 180f else 0f))
            }
            AnimatedVisibility(expanded) {
                Column {
                    HorizontalDivider(Modifier.padding(vertical = 10.dp), color = SurfaceLight)
                    Text(answer, fontSize = 13.sp, color = TextSecondary, lineHeight = 19.sp)
                }
            }
        }
    }
}

/** Ligne de contact (icône colorée + titre + sous-titre + chevron). */
@Composable
private fun ContactRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp), color = SurfaceColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(iconBg.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconBg, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(subtitle, fontSize = 12.sp, color = TextSecondary)
            }
            Icon(Icons.Default.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}
