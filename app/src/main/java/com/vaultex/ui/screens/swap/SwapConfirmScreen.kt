package com.vaultex.ui.screens.swap

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.vaultex.ui.navigation.Routes
import androidx.compose.ui.res.stringResource
import com.vaultex.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.vaultex.ui.theme.VaultExColors

@Composable
fun SwapConfirmScreen(navController: NavHostController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Confirmation Swap", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultExColors.Background)
            )
        },
        containerColor = VaultExColors.Background
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Button(onClick = { navController.navigate(Routes.swapResult("0xDEMO")) }) {
                Text(stringResource(R.string.confirm))
            }
        }
    }
}
