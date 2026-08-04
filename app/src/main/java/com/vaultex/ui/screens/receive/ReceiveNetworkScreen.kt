package com.vaultex.ui.screens.receive

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.vaultex.R
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.BgTertiary
import com.vaultex.ui.theme.BorderColor
import com.vaultex.ui.theme.NetworkBnb
import com.vaultex.ui.theme.NetworkBtc
import com.vaultex.ui.theme.NetworkEth
import com.vaultex.ui.theme.NetworkSol
import com.vaultex.ui.theme.NetworkTrx
import com.vaultex.ui.theme.Surface as SurfaceColor
import com.vaultex.ui.theme.TextMuted
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary

private data class ReceiveNetwork(
    val code: String,        // route param: BTC, ETH, BNB, TRX, SOL
    val name: String,
    val initials: String,
    val color: Color,
    val demoAddress: String
)

private val NETWORKS = listOf(
    ReceiveNetwork("BTC", "Bitcoin",  "BT", NetworkBtc, "bc1q…x5y6"),
    ReceiveNetwork("ETH", "Ethereum", "ET", NetworkEth, "0x742d…b12f"),
    ReceiveNetwork("BNB", "BNB Chain","BN", NetworkBnb, "0x742d…b12f"),
    ReceiveNetwork("TRX", "Tron",     "TR", NetworkTrx, "TJXg4…k9l0"),
    ReceiveNetwork("SOL", "Solana",   "SO", NetworkSol, "HN7cA…9Rw2")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveNetworkScreen(navController: NavHostController) {

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.receive_network_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = AccentBlue)
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Text(
                stringResource(R.string.receive_select_chain),
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                fontWeight = FontWeight.Medium
            )

            NETWORKS.forEach { network ->
                val isHighlighted = network.code == "ETH"
                Surface(
                    onClick = { navController.navigate("receiveAddress/${network.code}") },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isHighlighted) BgTertiary else SurfaceColor,
                    border = BorderStroke(
                        if (isHighlighted) 1.5.dp else 1.dp,
                        if (isHighlighted) AccentBlue else BorderColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(network.color, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(network.initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(network.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = network.color.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    network.code,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = network.color
                                )
                            }
                            Text(network.demoAddress, fontSize = 11.sp, color = TextSecondary)
                        }
                        if (isHighlighted) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}
