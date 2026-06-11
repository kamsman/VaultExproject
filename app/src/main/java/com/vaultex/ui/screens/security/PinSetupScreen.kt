package com.vaultex.ui.screens.security

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
fun PinSetupScreen(
    navController: NavHostController,
    viewModel: OnboardingViewModel
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }
    var showConfirmPin by remember { mutableStateOf(false) }

    val saveState by viewModel.saveState.collectAsState()

    // Navigation automatique quand la sauvegarde réussit :
    // proposer la biométrie avant d'entrer dans l'app
    LaunchedEffect(saveState) {
        if (saveState is OnboardingViewModel.SaveState.Success) {
            viewModel.resetSaveState()
            navController.navigate(Routes.BIOMETRIC_SETUP) { popUpTo(0) }
        }
    }

    val isLoading = saveState is OnboardingViewModel.SaveState.Loading

    Scaffold(containerColor = BgPrimary) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(BgPrimary, BgSecondary)))
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(modifier = Modifier.height(70.dp))

            Box(
                modifier = Modifier
                    .size(120.dp)
                    .shadow(elevation = 25.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(AccentGold.copy(alpha = 0.25f), BgSecondary)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🔐", fontSize = 58.sp)
            }

            Spacer(modifier = Modifier.height(36.dp))

            Text(
                text = stringResource(R.string.set_pin),
                color = TextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.pin_setup_description),
                color = TextSecondary,
                fontSize = 15.sp,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(45.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.cardColors(containerColor = BgSecondary.copy(alpha = 0.95f))
            ) {
                Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 26.dp)) {

                    Text(stringResource(R.string.pin_setup_pin_label), color = AccentGold, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("••••••", color = TextSecondary) },
                        visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        trailingIcon = {
                            IconButton(onClick = { showPin = !showPin }) {
                                Icon(
                                    imageVector = if (showPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPin) stringResource(R.string.pin_setup_hide_pin) else stringResource(R.string.pin_setup_show_pin),
                                    tint = AccentGold
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        textStyle = LocalTextStyle.current.copy(
                            color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentGold,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = BgPrimary,
                            unfocusedContainerColor = BgPrimary,
                            cursorColor = AccentGold
                        )
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Text(stringResource(R.string.pin_confirm), color = AccentGold, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) confirmPin = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("••••••", color = TextSecondary) },
                        visualTransformation = if (showConfirmPin) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        trailingIcon = {
                            IconButton(onClick = { showConfirmPin = !showConfirmPin }) {
                                Icon(
                                    imageVector = if (showConfirmPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showConfirmPin) stringResource(R.string.pin_setup_hide_pin) else stringResource(R.string.pin_setup_show_pin),
                                    tint = AccentGold
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        textStyle = LocalTextStyle.current.copy(
                            color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentGold,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = BgPrimary,
                            unfocusedContainerColor = BgPrimary,
                            cursorColor = AccentGold
                        )
                    )

                    Spacer(modifier = Modifier.height(26.dp))

                    // Indicateur visuel PIN
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        repeat(6) { index ->
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(if (index < pin.length) AccentGold else BgPrimary)
                                    .border(1.dp, AccentGold.copy(alpha = 0.5f), CircleShape)
                            )
                        }
                    }

                    if (error.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(text = error, color = AccentRed, fontSize = 14.sp)
                    }

                    if (saveState is OnboardingViewModel.SaveState.Error) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = stringResource(R.string.pin_setup_error, (saveState as OnboardingViewModel.SaveState.Error).message),
                            color = AccentRed,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            PrimaryButton(
                text = if (isLoading) stringResource(R.string.pin_setup_saving) else stringResource(R.string.continue_btn),
                enabled = !isLoading,
                onClick = {
                    when {
                        pin.length != 6 -> error = "Le PIN doit contenir 6 chiffres"
                        pin != confirmPin -> error = "Les PIN ne correspondent pas"
                        else -> {
                            error = ""
                            // Sauvegarde mnémonique chiffrée + PIN hashé PBKDF2 → Dashboard
                            viewModel.saveWallet(pin)
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
