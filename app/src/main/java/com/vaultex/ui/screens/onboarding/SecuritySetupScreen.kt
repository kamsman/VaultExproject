package com.vaultex.ui.screens.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.vaultex.R
import com.vaultex.ui.components.PrimaryButton
import com.vaultex.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySetupScreen(navController: NavHostController) {

    var pin by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.security_setup_title), color = TextPrimary) },
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
                text = stringResource(R.string.security_create_pin),
                color = TextPrimary,
                fontSize = 18.sp
            )

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it },
                label = { Text(stringResource(R.string.pin_label)) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.weight(1f))

            // ✅ BOUTON CORRIGÉ
            PrimaryButton(
                text = stringResource(R.string.continue_btn),
                onClick = {
                    // navigation simple
                    navController.navigate("dashboard")
                }
            )
        }
    }
}