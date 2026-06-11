package com.vaultex.ui.screens.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

import com.vaultex.R
import com.vaultex.ui.components.PrimaryButton
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.*
import com.vaultex.ui.viewmodel.OnboardingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MnemonicVerifyScreen(
    navController: NavHostController,
    viewModel: OnboardingViewModel
) {
    val wordIndex = viewModel.verifyWordIndex
    var word by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mnemonic_verify_title), color = TextPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgPrimary)
            )
        },
        containerColor = BgPrimary
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(20.dp)
                .fillMaxSize()
        ) {

            Text(
                text = stringResource(R.string.mnemonic_verify_heading),
                color = TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.mnemonic_verify_instruction, wordIndex + 1),
                color = TextSecondary,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(34.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = BgSecondary)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {

                    Text(
                        text = stringResource(R.string.mnemonic_verify_word_label, wordIndex + 1),
                        color = AccentBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = word,
                        onValueChange = { word = it; error = "" },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        placeholder = { Text(stringResource(R.string.mnemonic_verify_placeholder), color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentBlue,
                            unfocusedBorderColor = BgSecondary,
                            focusedContainerColor = BgPrimary,
                            unfocusedContainerColor = BgPrimary,
                            cursorColor = AccentBlue
                        ),
                        isError = error.isNotEmpty()
                    )

                    if (error.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = error, color = AccentRed, fontSize = 14.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            PrimaryButton(
                text = stringResource(R.string.continue_btn),
                onClick = {
                    when {
                        word.isBlank() -> error = "Veuillez entrer le mot"
                        !viewModel.verifyWord(wordIndex, word) -> error = "Mot incorrect"
                        else -> navController.navigate(Routes.PIN_SETUP) {
                            popUpTo(Routes.WELCOME) { inclusive = true }
                        }
                    }
                }
            )
        }
    }
}
