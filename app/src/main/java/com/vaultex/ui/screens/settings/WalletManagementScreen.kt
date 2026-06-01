package com.vaultex.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vaultex.ui.theme.*
import com.vaultex.ui.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletManagementScreen(navController: NavHostController) {

    val dashboardVm: DashboardViewModel = hiltViewModel()
    val uiState by dashboardVm.uiState.collectAsState()
    val context = LocalContext.current

    val addresses = uiState.addresses
    val chains = listOf(
        Triple("BTC", NetworkBtc, addresses?.btc),
        Triple("ETH", NetworkEth, addresses?.eth),
        Triple("BNB", NetworkBnb, addresses?.bnb),
        Triple("SOL", NetworkSol, addresses?.sol),
        Triple("TRX", NetworkTrx, addresses?.trx)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestion du wallet", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgPrimary)
            )
        },
        containerColor = BgPrimary
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(BgPrimary, BgSecondary)))
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Wallet actif",
                color = AccentGold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = BgSecondary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    chains.forEachIndexed { index, (symbol, color, addr) ->
                        AddressRow(
                            symbol = symbol,
                            color = color,
                            address = addr ?: "—",
                            onCopy = {
                                if (addr != null) {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Adresse $symbol", addr))
                                }
                            }
                        )
                        if (index < chains.size - 1) {
                            HorizontalDivider(color = BgPrimary.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddressRow(symbol: String, color: Color, address: String, onCopy: () -> Unit) {
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) { kotlinx.coroutines.delay(1500); copied = false }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(symbol, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp,
            modifier = Modifier.width(40.dp))
        Text(
            if (address.length > 20) "${address.take(10)}…${address.takeLast(8)}" else address,
            color = TextSecondary, fontSize = 13.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = { onCopy(); copied = true }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.ContentCopy, contentDescription = null,
                tint = if (copied) AccentGold else TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}