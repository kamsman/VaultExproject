package com.vaultex.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
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
fun ImportWalletScreen(
    navController: NavHostController,
    viewModel: OnboardingViewModel
) {
    var mnemonic by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var errorRes by remember { mutableStateOf<Int?>(null) }

    // Chemin « AJOUT d'un wallet » (PIN déjà posé) : succès → accueil du
    // NOUVEAU wallet actif.
    val saveState by viewModel.saveState.collectAsState()
    LaunchedEffect(saveState) {
        if (saveState is OnboardingViewModel.SaveState.Success) {
            viewModel.resetSaveState()
            navController.navigate(Routes.DASHBOARD) { popUpTo(0) { inclusive = true } }
        }
    }

    val words = mnemonic
        .lowercase()
        .replace("\n", " ")
        .replace(",", " ")
        .replace(";", " ")
        .trim()
        .split("\\s+".toRegex())
        .filter { it.isNotBlank() }

    val wordCount = words.size
    val isValidCount = wordCount == 12

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.import_title),
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = AccentBlue
                        )
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {

            Spacer(modifier = Modifier.height(8.dp))

            // Second emplacement du code anti-phishing, et le plus critique :
            // c'est ICI qu'une contrefaçon récolte la phrase de récupération.
            // Un utilisateur qui a pris l'habitude de voir son code doit
            // s'arrêter net s'il ne le voit pas avant de saisir ses 12 mots.
            val antiPhishing = remember { viewModel.antiPhishingCode() }
            if (antiPhishing.isNotBlank()) {
                com.vaultex.ui.components.AntiPhishingBanner(code = antiPhishing)
                Spacer(modifier = Modifier.height(14.dp))
            }

            Text(
                text = stringResource(R.string.import_enter_phrase),
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = mnemonic,
                onValueChange = { input ->
                    mnemonic = input
                        .lowercase()
                        .replace("\n", " ")
                        .replace(",", " ")
                        .replace(";", " ")
                        .replace(Regex("\\s+"), " ")
                        .trimStart()
                    errorRes = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                placeholder = { Text(stringResource(R.string.import_placeholder), color = TextMuted) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = BorderColor,
                    focusedContainerColor = BgPrimary,
                    unfocusedContainerColor = BgPrimary,
                    cursorColor = AccentBlue
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.import_word_count, wordCount),
                color = if (isValidCount) AccentBlue else TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Passphrase BIP39 optionnelle (« 13e mot ») — M-03
            Text(
                text = stringResource(R.string.passphrase_label),
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = passphrase,
                onValueChange = { passphrase = it; viewModel.setPassphrase(it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.passphrase_placeholder), color = TextMuted) },
                supportingText = { Text(stringResource(R.string.passphrase_hint), color = TextSecondary, fontSize = 11.sp) },
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = BorderColor,
                    focusedContainerColor = BgPrimary,
                    unfocusedContainerColor = BgPrimary,
                    cursorColor = AccentBlue
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Bandeau d'information à barre latérale bleue
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceLight)
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(AccentBlue)
                )
                Text(
                    text = stringResource(R.string.import_never_transmitted),
                    color = TextPrimary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)
                )
            }

            errorRes?.let { res ->
                Spacer(modifier = Modifier.height(14.dp))
                Text(text = stringResource(res), color = AccentRed, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(24.dp))

            PrimaryButton(
                text = stringResource(R.string.import_button),
                onClick = {
                    when {
                        mnemonic.isBlank() -> errorRes = R.string.import_error_empty
                        wordCount != 12 -> errorRes = R.string.import_error_count
                        !viewModel.setImportedMnemonic(words) -> errorRes = R.string.import_error_invalid
                        // AJOUT d'un wallet (PIN d'app déjà posé) : enregistrement
                        // direct, sans redemander de PIN.
                        viewModel.hasPin() -> viewModel.saveWallet(null)
                        else -> navController.navigate(Routes.PIN_SETUP) {
                            popUpTo(Routes.WELCOME) { inclusive = true }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
