package com.vaultex.ui.screens.pending

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vaultex.R
import com.vaultex.data.local.entity.PendingSendEntity
import com.vaultex.service.PendingSendWorker
import com.vaultex.ui.theme.*
import com.vaultex.ui.viewmodel.PendingSendsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingSendsScreen(navController: NavHostController) {
    val viewModel: PendingSendsViewModel = hiltViewModel()
    val items by viewModel.items.collectAsState()

    // Intention en attente de confirmation de relance (avertissement double-dépense)
    var retryTarget by remember { mutableStateOf<PendingSendEntity?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.pending_sends_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.back), tint = AccentBlue)
                    }
                },
                actions = {
                    if (items.any { it.status == PendingSendWorker.STATUS_SENT }) {
                        IconButton(onClick = { viewModel.clearSent() }) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                stringResource(R.string.pending_sends_clear_sent),
                                tint = TextSecondary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgPrimary)
            )
        },
        containerColor = BgPrimary
    ) { padding ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.pending_sends_empty),
                    color = TextSecondary,
                    fontSize = 15.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    PendingSendCard(
                        item = item,
                        onRetry = { retryTarget = item },
                        onDismiss = { viewModel.dismiss(item) }
                    )
                }
            }
        }
    }

    retryTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { retryTarget = null },
            title = { Text(stringResource(R.string.pending_sends_retry_warning_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.pending_sends_retry_warning_body), fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.retry(target)
                    retryTarget = null
                }) {
                    Text(stringResource(R.string.pending_sends_retry), color = AccentOrange)
                }
            },
            dismissButton = {
                TextButton(onClick = { retryTarget = null }) {
                    Text(stringResource(R.string.cancel), color = TextSecondary)
                }
            },
            containerColor = Surface
        )
    }
}

@Composable
private fun PendingSendCard(
    item: PendingSendEntity,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${item.amount} ${item.chain}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                StatusChip(item.status)
            }

            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.send_recipient_label) + " : " + truncate(item.toAddress),
                fontSize = 13.sp,
                color = TextSecondary
            )

            Text(
                formatDate(item.createdAt),
                fontSize = 11.sp,
                color = TextMuted,
                modifier = Modifier.padding(top = 2.dp)
            )

            if (!item.lastError.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(item.lastError, fontSize = 12.sp, color = AccentRed)
            }

            // Relance possible uniquement pour les échecs / blocages
            val canRetry = item.status == PendingSendWorker.STATUS_FAILED ||
                item.status == PendingSendWorker.STATUS_SENDING
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canRetry) {
                    OutlinedButton(onClick = onRetry) {
                        Text(stringResource(R.string.pending_sends_retry), color = AccentOrange)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.pending_sends_dismiss), color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val (label, color) = when (status) {
        PendingSendWorker.STATUS_PENDING -> stringResource(R.string.pending_sends_status_pending) to AccentBlue
        PendingSendWorker.STATUS_SENDING -> stringResource(R.string.pending_sends_status_sending) to AccentOrange
        PendingSendWorker.STATUS_SENT -> stringResource(R.string.pending_sends_status_sent) to VaultExColors.Success
        else -> stringResource(R.string.pending_sends_status_failed) to AccentRed
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun truncate(address: String): String =
    if (address.length <= 16) address else address.take(8) + "…" + address.takeLast(6)

private fun formatDate(epochMillis: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(epochMillis))
