package com.vaultex.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

import com.vaultex.R
import com.vaultex.ui.components.GlassCard
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
    var revealed by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.seed_phrase_title), color = TextPrimary) },
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

            Card(
                colors = CardDefaults.cardColors(containerColor = AccentRed.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.mnemonic_display_warning),
                    color = AccentRed,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(14.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            GlassCard {
                if (mnemonic.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AccentGold)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.height(280.dp)
                    ) {
                        itemsIndexed(mnemonic) { i, word ->
                            Row(
                                modifier = Modifier
                                    .background(BgSecondary, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${i + 1}.", color = TextMuted, fontSize = 11.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (revealed) word else "••••••",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = { revealed = !revealed },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = AccentGold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (revealed) stringResource(R.string.mnemonic_hide) else stringResource(R.string.mnemonic_show),
                        color = AccentGold
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            PrimaryButton(
                text = stringResource(R.string.continue_btn),
                onClick = { navController.navigate(Routes.MNEMONIC_VERIFY) },
                enabled = mnemonic.isNotEmpty()
            )
        }
    }
}
