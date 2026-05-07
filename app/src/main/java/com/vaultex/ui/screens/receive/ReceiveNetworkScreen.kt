package com.vaultex.ui.screens.receive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

// ✅ IMPORT ICÔNE
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack

import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveNetworkScreen(navController: NavHostController) {

    val networks = listOf("BTC", "ETH", "BNB", "SOL", "TRX")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choisir un réseau", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgPrimary)
            )
        },
        containerColor = BgPrimary
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {

            Text(
                text = "Sélectionnez la blockchain pour recevoir des fonds",
                color = TextPrimary
            )

            Spacer(Modifier.height(20.dp))

            networks.forEach { network ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable {
                            navController.navigate("receiveAddress/$network")
                        }
                ) {
                    Text(
                        text = network,
                        modifier = Modifier.padding(16.dp),
                        color = TextPrimary
                    )
                }
            }
        }
    }
}