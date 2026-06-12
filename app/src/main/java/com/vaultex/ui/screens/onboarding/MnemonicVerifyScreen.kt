package com.vaultex.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

import com.vaultex.R
import com.vaultex.ui.components.PrimaryButton
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.*
import com.vaultex.ui.viewmodel.OnboardingViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MnemonicVerifyScreen(
    navController: NavHostController,
    viewModel: OnboardingViewModel
) {
    val mnemonic by viewModel.mnemonic.collectAsState()

    // Pool mélangé (stable tant que la mnémonique ne change pas)
    val shuffled = remember(mnemonic) { mnemonic.withIndex().shuffled() }

    // Indices (dans le pool mélangé) sélectionnés, dans l'ordre de sélection
    var selected by remember(mnemonic) { mutableStateOf(listOf<Int>()) }
    var showError by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.mnemonic_verify_title),
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
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {

            Text(
                text = stringResource(R.string.mnemonic_verify_tap_order),
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Emplacements : mots choisis dans l'ordre + cases vides
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                repeat(mnemonic.size) { slot ->
                    if (slot < selected.size) {
                        val poolIndex = selected[slot]
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(BgSecondary)
                                .border(1.dp, AccentBlue, RoundedCornerShape(10.dp))
                                .clickable {
                                    selected = selected.filterNot { it == poolIndex }
                                    showError = false
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = shuffled[poolIndex].value,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minWidth = 72.dp, minHeight = 35.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Pool de mots à taper
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                shuffled.forEachIndexed { poolIndex, indexedWord ->
                    if (poolIndex !in selected) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(BgTertiary.copy(alpha = 0.6f))
                                .border(1.dp, AccentBlue.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                                .clickable {
                                    if (selected.size < mnemonic.size) {
                                        selected = selected + poolIndex
                                        showError = false
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = indexedWord.value,
                                color = TextPrimary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            if (showError) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.mnemonic_verify_wrong_order),
                    color = AccentRed,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            PrimaryButton(
                text = stringResource(R.string.confirm),
                enabled = mnemonic.isNotEmpty() && selected.size == mnemonic.size,
                onClick = {
                    val words = selected.map { shuffled[it].value }
                    val allCorrect = words.withIndex().all { (i, w) -> viewModel.verifyWord(i, w) }
                    if (allCorrect) {
                        navController.navigate(Routes.PIN_SETUP) {
                            popUpTo(Routes.WELCOME) { inclusive = true }
                        }
                    } else {
                        showError = true
                        selected = emptyList()
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
