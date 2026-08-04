package com.vaultex.ui.screens.settings

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

// ✅ IMPORT ICÔNE CORRECT
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack

import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletManagementScreen(navController: NavHostController) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wallet_management_title), color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
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
                text = "Liste wallets multi-comptes",
                color = TextPrimary
            )

            Spacer(Modifier.height(16.dp))

            Button(onClick = { navController.navigate(Routes.walletAddVerify("settingsImport")) }) {
                Text(stringResource(R.string.import_wallet))
            }
        }
    }
}