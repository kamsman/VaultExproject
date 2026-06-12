package com.vaultex.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vaultex.R
import com.vaultex.ui.theme.*
import com.vaultex.ui.viewmodel.PanicPinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanicPinScreen(navController: NavHostController) {
    val viewModel: PanicPinViewModel = hiltViewModel()
    val saved by viewModel.saved.collectAsState()

    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }

    LaunchedEffect(saved) {
        if (saved == true) navController.popBackStack()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.panic_pin_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back), tint = AccentBlue)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgSecondary)
            )
        },
        containerColor = BgSecondary
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            // Icône sur carte rose
            Box(
                Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(AccentRed.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = AccentRed,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                stringResource(R.string.panic_pin_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.panic_description),
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(24.dp))

            // Bandeau d'avertissement à liseré rouge
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(AccentRed.copy(alpha = 0.08f)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(4.dp).height(44.dp).background(AccentRed))
                Spacer(Modifier.width(10.dp))
                Text(
                    "⚠ " + stringResource(R.string.panic_irreversible),
                    fontSize = 13.sp,
                    color = TextPrimary
                )
            }

            Spacer(Modifier.height(20.dp))

            PanicPinField(
                value = pin,
                onValueChange = { pin = it; mismatch = false },
                placeholder = stringResource(R.string.panic_define_hint)
            )
            Spacer(Modifier.height(14.dp))
            PanicPinField(
                value = confirmPin,
                onValueChange = { confirmPin = it; mismatch = false },
                placeholder = stringResource(R.string.panic_confirm_hint)
            )

            if (mismatch || saved == false) {
                Spacer(Modifier.height(10.dp))
                Text(
                    if (mismatch) stringResource(R.string.panic_mismatch) else stringResource(R.string.error_generic),
                    color = AccentRed,
                    fontSize = 13.sp
                )
            }

            Spacer(Modifier.height(22.dp))

            Button(
                onClick = {
                    if (pin == confirmPin) viewModel.savePin(pin) else mismatch = true
                },
                enabled = pin.length == 6 && confirmPin.length == 6,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentRed,
                    contentColor = Color.White
                )
            ) {
                Text(stringResource(R.string.panic_define_button), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun PanicPinField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) onValueChange(it) },
        placeholder = { Text(placeholder, color = TextMuted, fontSize = 14.sp) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccentBlue,
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = BgPrimary,
            unfocusedContainerColor = BgPrimary,
            cursorColor = AccentBlue
        )
    )
}
