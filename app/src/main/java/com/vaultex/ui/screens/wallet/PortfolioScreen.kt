package com.vaultex.ui.screens.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.vaultex.ui.navigation.Routes
import androidx.compose.ui.res.stringResource
import com.vaultex.R
import androidx.compose.material3.Button

// ✅ IMPORT CORRECT
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack

import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(navController: NavHostController) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Portefeuille", color = TextPrimary) },
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
                text = "Liste complète des actifs",
                color = TextPrimary
            )

            Spacer(Modifier.height(16.dp))

            Button(onClick = { navController.navigate(Routes.PORTFOLIO_TOKEN_DETAIL) }) {
                Text(stringResource(R.string.portfolio_view_token))
            }
        }
    }
}