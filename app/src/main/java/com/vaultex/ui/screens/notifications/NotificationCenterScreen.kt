package com.vaultex.ui.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vaultex.core.session.NotifItem
import com.vaultex.ui.components.CryptoIcon
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.BorderColor
import com.vaultex.ui.theme.Surface as SurfaceColor
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary
import com.vaultex.ui.viewmodel.NotificationCenterViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCenterScreen(navController: NavHostController) {
    val viewModel: NotificationCenterViewModel = hiltViewModel()
    val items by viewModel.items.collectAsState()

    // À l'ouverture, on marque tout comme lu (la pastille disparaît).
    LaunchedEffect(Unit) { viewModel.markAllRead() }

    Scaffold(
        containerColor = BgPrimary,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Notifications", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Retour", tint = AccentBlue)
                    }
                },
                actions = {
                    if (items.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clear() }) {
                            Text("Effacer", color = AccentBlue, fontSize = 13.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgPrimary)
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.NotificationsNone, null, tint = TextSecondary, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(12.dp))
                Text("Aucune notification", color = TextSecondary, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items, key = { it.id }) { NotifRow(it) }
            }
        }
    }
}

@Composable
private fun NotifRow(item: NotifItem) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            // Logo crypto si dispo, sinon pastille bleue.
            Box(Modifier.size(40.dp).clip(CircleShape).background(AccentBlue.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                if (item.symbol != null) {
                    coil.compose.AsyncImage(
                        model = CryptoIcon.url(item.symbol),
                        contentDescription = item.symbol,
                        modifier = Modifier.size(40.dp).clip(CircleShape)
                    )
                } else {
                    Icon(Icons.Default.NotificationsNone, null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                Text(item.body, fontSize = 12.sp, color = TextSecondary)
                Text(formatTime(item.timestamp), fontSize = 11.sp, color = TextSecondary.copy(alpha = 0.7f))
            }
            if (!item.read) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(AccentBlue))
            }
        }
    }
}

private fun formatTime(ts: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(ts))
