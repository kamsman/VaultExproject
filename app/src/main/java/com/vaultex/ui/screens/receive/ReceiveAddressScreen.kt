package com.vaultex.ui.screens.receive

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

// ✅ IMPORT ICÔNE CORRECT
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack

import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.TextPrimary
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveAddressScreen(
    navController: NavHostController,
    blockchain: String
) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recevoir", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BgPrimary
                )
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
                text = "Réseau: $blockchain",
                color = TextPrimary
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = "QR Code ici",
                color = TextPrimary
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Adresse: 0x...abc",
                color = Color.White
            )
        }
    }
}