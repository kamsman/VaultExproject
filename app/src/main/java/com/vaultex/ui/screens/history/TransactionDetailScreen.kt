package com.vaultex.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vaultex.R
import com.vaultex.data.local.entity.TransactionEntity
import com.vaultex.ui.theme.*
import com.vaultex.ui.viewmodel.TransactionDetailViewModel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(navController: NavHostController, hash: String) {
    val viewModel: TransactionDetailViewModel = hiltViewModel()
    val tx by viewModel.transaction.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(hash) { viewModel.load(hash) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.tx_detail_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back), tint = AccentBlue)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgSecondary)
            )
        },
        containerColor = BgSecondary
    ) { padding ->
        when {
            isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentBlue)
            }
            tx == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.tx_detail_not_found), color = TextSecondary)
            }
            else -> TransactionDetailContent(tx!!, Modifier.padding(padding))
        }
    }
}

@Composable
private fun TransactionDetailContent(tx: TransactionEntity, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val incoming = tx.type.equals("receive", true) || tx.type.contains("reçu", true) || tx.type.contains("received", true)
    val accent = if (incoming) AccentGreen else AccentRed
    val sign = if (incoming) "+" else "-"

    val statusLabel = when (tx.status.lowercase()) {
        "confirmed" -> stringResource(R.string.tx_confirmed)
        "failed" -> stringResource(R.string.tx_failed)
        else -> stringResource(R.string.tx_pending)
    }
    val statusColor = when (tx.status.lowercase()) {
        "confirmed" -> AccentGreen
        "failed" -> AccentRed
        else -> AccentOrange
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        // Cercle directionnel + montant + statut
        Box(
            Modifier.size(80.dp).clip(CircleShape).background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (incoming) Icons.Default.CallReceived else Icons.Default.CallMade,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "$sign ${tx.amount} ${tx.tokenSymbol}",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Surface(shape = RoundedCornerShape(8.dp), color = statusColor.copy(alpha = 0.12f)) {
            Text(
                statusLabel,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = statusColor
            )
        }

        Spacer(Modifier.height(20.dp))

        // Carte des champs
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                DetailField(stringResource(R.string.label_from), tx.fromAddress.ellipsize())
                FieldDivider()
                DetailField(stringResource(R.string.label_to), tx.toAddress.ellipsize())
                FieldDivider()
                DetailField(stringResource(R.string.tx_detail_hash), tx.hash.ellipsize())
                FieldDivider()
                // Réseau en badge
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(stringResource(R.string.tx_detail_network), fontSize = 12.sp, color = TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    Surface(shape = RoundedCornerShape(6.dp), color = AccentOrange.copy(alpha = 0.12f)) {
                        Text(
                            tx.blockchain,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentOrange
                        )
                    }
                }
                FieldDivider()
                DetailField(
                    stringResource(R.string.tx_detail_date),
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(tx.timestamp))
                )
                FieldDivider()
                DetailField(stringResource(R.string.label_fee), "${tx.fee} ${tx.tokenSymbol}")
                tx.blockNumber?.let {
                    FieldDivider()
                    DetailField(stringResource(R.string.tx_detail_block), "#$it")
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        val url = explorerUrl(tx.blockchain, tx.hash)
        if (url != null) {
            Text(
                stringResource(R.string.tx_detail_explorer),
                color = AccentBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { uriHandler.openUri(url) }
                    .padding(8.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun String.ellipsize(max: Int = 28): String =
    if (length <= max) this else take(max) + "…"

private fun explorerUrl(chain: String, hash: String): String? = when (chain.uppercase()) {
    "BTC" -> "https://blockstream.info/tx/$hash"
    "ETH", "USDT-ETH" -> "https://etherscan.io/tx/$hash"
    "BNB", "USDT-BNB" -> "https://bscscan.com/tx/$hash"
    "SOL" -> "https://solscan.io/tx/$hash"
    "TRX", "USDT" -> "https://tronscan.org/#/transaction/$hash"
    else -> null
}

@Composable
private fun DetailField(label: String, value: String) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(label, fontSize = 12.sp, color = TextSecondary)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FieldDivider() {
    HorizontalDivider(color = SurfaceLight, thickness = 1.dp)
}
