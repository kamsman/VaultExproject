package com.vaultex.ui.screens.security

import androidx.activity.ComponentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController

import com.vaultex.core.security.PinVerificationResult
import com.vaultex.ui.components.PrimaryButton
import com.vaultex.ui.theme.*
import com.vaultex.ui.viewmodel.PinUnlockViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinUnlockScreen(
    navController: NavHostController,
    destination: String
) {
    val viewModel: PinUnlockViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var pin by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }
    var biometricError by remember { mutableStateOf<String?>(null) }

    val biometricEnabled = viewModel.isBiometricEnabled
    val biometricAvailable = remember {
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    // Build BiometricPrompt (requires ComponentActivity from biometric 1.2.0+)
    val biometricPrompt = remember {
        val activity = context as ComponentActivity
        BiometricPrompt(activity, ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    navController.navigate(destination) { popBackStack() }
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                        biometricError = errString.toString()
                    }
                }
                override fun onAuthenticationFailed() {}
            })
    }

    val promptInfo = remember {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("VaultEx")
            .setSubtitle("Déverrouillez avec votre empreinte digitale")
            .setNegativeButtonText("Utiliser le PIN")
            .build()
    }

    // Auto-trigger biometric on screen open if enabled and available
    LaunchedEffect(Unit) {
        if (biometricEnabled && biometricAvailable) {
            biometricPrompt.authenticate(promptInfo)
        }
    }

    LaunchedEffect(state.result) {
        when (state.result) {
            is PinVerificationResult.Valid ->
                navController.navigate(destination) { popBackStack() }
            is PinVerificationResult.PanicTriggered ->
                navController.navigate("welcome") { popUpTo(0) }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vérification PIN", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = TextPrimary)
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
                .background(Brush.verticalGradient(listOf(BgPrimary, BgSecondary)))
                .padding(padding)
                .imePadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Text("🔐", fontSize = 56.sp)
            Spacer(Modifier.height(20.dp))
            Text("Entrez votre PIN", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Requis pour accéder à cette section", color = TextSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(40.dp))

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = BgSecondary)) {
                Column(modifier = Modifier.padding(24.dp)) {

                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("••••••", color = TextSecondary) },
                        visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        trailingIcon = {
                            IconButton(onClick = { showPin = !showPin }) {
                                Icon(if (showPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null, tint = AccentGold)
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentGold, unfocusedBorderColor = BgPrimary,
                            focusedContainerColor = BgPrimary, unfocusedContainerColor = BgPrimary,
                            cursorColor = AccentGold)
                    )

                    Spacer(Modifier.height(16.dp))

                    // Dot indicator
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        repeat(6) { i ->
                            Box(modifier = Modifier.size(12.dp).clip(CircleShape)
                                .background(if (i < pin.length) AccentGold else BgPrimary)
                                .border(1.dp, AccentGold.copy(alpha = 0.5f), CircleShape))
                        }
                    }

                    // Error messages
                    val errMsg = when (val r = state.result) {
                        is PinVerificationResult.Invalid ->
                            "PIN incorrect · ${r.remainingAttempts} tentative(s) restante(s)"
                        is PinVerificationResult.Locked ->
                            "Verrouillé · réessayez dans ${r.unlockInSeconds}s"
                        else -> biometricError ?: state.error
                    }
                    errMsg?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = AccentRed, fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            PrimaryButton(
                text = if (state.isLoading) "Vérification..." else "Confirmer",
                enabled = pin.length == 6 && !state.isLoading,
                onClick = { viewModel.verify(pin) }
            )

            // Biometric button
            if (biometricEnabled && biometricAvailable) {
                Spacer(Modifier.height(20.dp))
                OutlinedButton(
                    onClick = { biometricError = null; biometricPrompt.authenticate(promptInfo) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGold),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Utiliser l'empreinte digitale")
                }
            }
        }
    }
}
