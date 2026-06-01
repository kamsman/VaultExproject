package com.vaultex.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vaultex.data.local.entity.TransactionEntity
import com.vaultex.ui.theme.*
import com.vaultex.ui.viewmodel.TxDetailViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(navController: NavHostController, hash: String) {

    val viewModel: TxDetailViewModel = hiltViewModel()
    val tx by viewModel.tx.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Détail transaction", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Retour", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgPrimary)
            )
        },
        containerColor = BgPrimary
    ) { padding ->

        if (tx == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentGold)
            }
            return@Scaffold
        }

        val t = tx!!
        val date = remember(t.timestamp) {
            SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.FRANCE).format(Date(t.timestamp))
        }
        val statusColor = when (t.status.lowercase()) {
            "confirmed" -> AccentGreen
            "pending"   -> AccentGold
            else        -> AccentRed
        }

        fun truncate(s: String, front: Int = 10, back: Int = 8): String =
            if (s.length > front + back + 3) "${s.take(front)}...${s.takeLast(back)}" else s

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(BgPrimary, BgSecondary)))
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Informations",
                color = AccentGold,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = BgSecondary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TxDetailRow("Hash", truncate(t.hash, 12, 8))
                    HorizontalDivider(color = BorderColor)
                    TxDetailRow("Type", t.type.replaceFirstChar { it.uppercaseChar() })
                    HorizontalDivider(color = BorderColor)
                    TxDetailRow("Blockchain", t.blockchain)
                    HorizontalDivider(color = BorderColor)
                    TxDetailRow("Montant", "${t.amount} ${t.tokenSymbol}")
                    HorizontalDivider(color = BorderColor)
                    TxDetailRow("Frais", t.fee)
                    HorizontalDivider(color = BorderColor)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Statut", color = TextSecondary, fontSize = 13.sp)
                        Text(
                            t.status.replaceFirstChar { it.uppercaseChar() },
                            color = statusColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                    HorizontalDivider(color = BorderColor)
                    TxDetailRow("Date", date)
                    HorizontalDivider(color = BorderColor)
                    TxDetailRow("Confirmations", t.confirmations.toString())
                    if (t.blockNumber != null) {
                        HorizontalDivider(color = BorderColor)
                        TxDetailRow("Bloc", t.blockNumber.toString())
                    }
                }
            }

            Text(
                "Adresses",
                color = AccentGold,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = BgSecondary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column {
                        Text("De", color = TextSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(truncate(t.fromAddress), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    HorizontalDivider(color = BorderColor)
                    Column {
                        Text("À", color = TextSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(truncate(t.toAddress), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun TxDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextSecondary, fontSize = 13.sp)
        Text(value, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}
