package com.vaultex.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
fun MnemonicDisplayScreen(
    navController: NavHostController,
    viewModel: OnboardingViewModel
) {
    // La génération se fait dans le ViewModel (thread-safe, hors Compose tree)
    LaunchedEffect(Unit) {
        viewModel.generateMnemonic()
    }

    val mnemonic by viewModel.mnemonic.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.mnemonic_title),
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

            // Bandeau d'avertissement orange à barre latérale
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AccentOrange.copy(alpha = 0.12f))
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(AccentOrange)
                )
                Text(
                    text = "⚠ " + stringResource(R.string.backup_warning_title),
                    color = TextPrimary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (mnemonic.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AccentBlue)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.height(280.dp)
                ) {
                    itemsIndexed(mnemonic) { i, word ->
                        Column(
                            modifier = Modifier
                                .background(BgTertiary.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text("${i + 1}", color = TextSecondary, fontSize = 10.sp)
                            Text(
                                text = word,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            PrimaryButton(
                text = stringResource(R.string.mnemonic_saved_button),
                onClick = { navController.navigate(Routes.MNEMONIC_VERIFY) },
                enabled = mnemonic.isNotEmpty()
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
