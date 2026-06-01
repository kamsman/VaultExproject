package com.vaultex.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

import com.vaultex.ui.theme.*

private data class ChainRpc(
    val chainName: String,
    val symbol: String,
    val rpcUrl: String,
    val networkColor: Color
)

private val CHAIN_RPCS = listOf(
    ChainRpc("Ethereum", "ETH", "cloudflare-eth.com", NetworkEth),
    ChainRpc("BNB Smart Chain", "BNB", "bsc-dataseed.binance.org", NetworkBnb),
    ChainRpc("Bitcoin", "BTC", "blockstream.info", NetworkBtc),
    ChainRpc("Solana", "SOL", "api.mainnet-beta.solana.com", NetworkSol),
    ChainRpc("Tron", "TRX", "api.trongrid.io", NetworkTrx)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkSettingsScreen(navController: NavHostController) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paramètres réseau", color = TextPrimary) },
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(BgPrimary, BgSecondary)))
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                "Endpoints RPC actifs",
                color = AccentGold,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
            )

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(CHAIN_RPCS) { chain ->
                    ChainRpcCard(chain)
                }
            }
        }
    }
}

@Composable
private fun ChainRpcCard(chain: ChainRpc) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BgSecondary)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(chain.networkColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    chain.symbol.take(1),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(chain.chainName, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text(chain.rpcUrl, color = TextSecondary, fontSize = 12.sp)
            }

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(AccentGreen)
            )
        }
    }
}
