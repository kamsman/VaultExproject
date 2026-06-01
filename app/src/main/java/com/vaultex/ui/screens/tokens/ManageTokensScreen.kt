package com.vaultex.ui.screens.tokens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

import com.vaultex.ui.theme.*

private data class NativeToken(
    val symbol: String,
    val name: String,
    val blockchain: String,
    val networkColor: Color
)

private val NATIVE_TOKENS = listOf(
    NativeToken("BTC", "Bitcoin", "Bitcoin Network", NetworkBtc),
    NativeToken("ETH", "Ethereum", "Ethereum Mainnet", NetworkEth),
    NativeToken("BNB", "BNB Chain", "BNB Smart Chain", NetworkBnb),
    NativeToken("SOL", "Solana", "Solana Mainnet", NetworkSol),
    NativeToken("TRX", "Tron", "Tron Network", NetworkTrx)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageTokensScreen(navController: NavHostController) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gérer les tokens", color = TextPrimary) },
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
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                "Tokens natifs supportés",
                color = AccentGold,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
            )

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(NATIVE_TOKENS) { token ->
                    NativeTokenRow(token)
                }
            }
        }
    }
}

@Composable
private fun NativeTokenRow(token: NativeToken) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BgSecondary)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Colored circle icon
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(token.networkColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    token.symbol.take(1),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(token.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(token.blockchain, color = TextSecondary, fontSize = 12.sp)
            }

            Text(
                token.symbol,
                color = token.networkColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}
