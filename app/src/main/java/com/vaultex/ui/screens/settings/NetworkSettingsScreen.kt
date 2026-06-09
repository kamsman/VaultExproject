package com.vaultex.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.vaultex.ui.theme.VaultExColors

private data class RpcEntry(val label: String, val url: String)

private val RPC_ENTRIES = listOf(
    RpcEntry("Ethereum (ETH)", "https://rpc.ankr.com/eth/"),
    RpcEntry("BNB Smart Chain", "https://bsc-dataseed.binance.org/"),
    RpcEntry("Bitcoin (BTC)", "https://blockstream.info/api/"),
    RpcEntry("Solana (SOL)", "https://api.mainnet-beta.solana.com/"),
    RpcEntry("TRON (TRX/USDT)", "https://api.trongrid.io/"),
    RpcEntry("Etherscan (ETH history)", "https://api.etherscan.io/"),
    RpcEntry("BscScan (BNB history)", "https://api.bscscan.com/"),
    RpcEntry("ChangeNOW (swap)", "https://api.changenow.io/v1/")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkSettingsScreen(navController: NavHostController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paramètres réseau", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = VaultExColors.Background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Endpoints RPC utilisés (mainnet)",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = VaultExColors.TextSecondary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            RPC_ENTRIES.forEach { entry ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(entry.label, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text(entry.url, fontSize = 12.sp, color = VaultExColors.BluePrimary)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = VaultExColors.BlueLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Support testnet et RPC personnalisé disponible dans une prochaine version.",
                    fontSize = 12.sp,
                    color = VaultExColors.BluePrimary,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}
