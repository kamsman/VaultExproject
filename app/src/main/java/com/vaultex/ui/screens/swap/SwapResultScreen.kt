package com.vaultex.ui.screens.swap

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

// ✅ IMPORT CORRECT
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack

import com.vaultex.R
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwapResultScreen(
    navController: NavHostController,
    hash: String
) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.swap_result_title), color = TextPrimary) },
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
                text = stringResource(R.string.swap_result_success),
                color = TextPrimary
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.result_hash_fmt, hash),
                color = TextPrimary
            )
        }
    }
}