package com.vaultex.ui.screens.tokens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vaultex.R
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.Surface as SurfaceColor
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary
import com.vaultex.ui.viewmodel.AddTokenViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTokenScreen(navController: NavHostController) {
    val viewModel: AddTokenViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    // Token enregistré → on revient à l'écran précédent (Mes actifs).
    LaunchedEffect(state.saved) {
        if (state.saved) navController.popBackStack()
    }

    val errorText = when (state.error) {
        AddTokenViewModel.ERR_INVALID -> stringResource(R.string.add_token_invalid_address)
        AddTokenViewModel.ERR_NOT_FOUND -> stringResource(R.string.add_token_not_found)
        null -> null
        else -> state.error
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_token_title), color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = AccentBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgPrimary)
            )
        },
        containerColor = BgPrimary
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ─── Réseau ───────────────────────────────────────────
            Text(stringResource(R.string.add_token_network), color = TextSecondary, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NetworkChip(
                    label = stringResource(R.string.add_token_network_eth),
                    selected = state.network == "ETH",
                    modifier = Modifier.weight(1f)
                ) { viewModel.setNetwork("ETH") }
                NetworkChip(
                    label = stringResource(R.string.add_token_network_bnb),
                    selected = state.network == "BNB",
                    modifier = Modifier.weight(1f)
                ) { viewModel.setNetwork("BNB") }
            }

            // ─── Contrat ──────────────────────────────────────────
            OutlinedTextField(
                value = state.contract,
                onValueChange = { viewModel.setContract(it) },
                label = { Text(stringResource(R.string.add_token_contract_label)) },
                placeholder = { Text("0x…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = TextSecondary,
                    focusedLabelColor = AccentBlue,
                    unfocusedLabelColor = TextSecondary,
                    cursorColor = AccentBlue
                )
            )
            Text(stringResource(R.string.add_token_paste_hint), color = TextSecondary, fontSize = 12.sp)

            // ─── Détecter ─────────────────────────────────────────
            Button(
                onClick = { viewModel.detect() },
                enabled = !state.detecting && state.contract.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                if (state.detecting) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.add_token_detecting), color = Color.White)
                } else {
                    Text(stringResource(R.string.add_token_detect), color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }

            if (errorText != null) {
                Text(errorText, color = Color(0xFFEF4444), fontSize = 13.sp)
            }

            // ─── Infos détectées ──────────────────────────────────
            val symbol = state.detectedSymbol
            val decimals = state.detectedDecimals
            if (symbol != null && decimals != null) {
                Surface(shape = RoundedCornerShape(16.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.add_token_detected), color = TextSecondary, fontSize = 12.sp)
                        DetailRow(stringResource(R.string.add_token_symbol), symbol)
                        DetailRow(stringResource(R.string.add_token_decimals), decimals.toString())
                        val priceUsd = state.detectedPriceUsd
                        if (priceUsd != null) {
                            DetailRow(
                                stringResource(R.string.add_token_price),
                                "$" + (if (priceUsd >= 1.0) "%,.2f".format(priceUsd) else "%.6f".format(priceUsd))
                            )
                        }
                        Text(stringResource(R.string.add_token_verified), color = Color(0xFF22C55E), fontSize = 13.sp)
                        if (state.priceUnknown) {
                            Text(
                                stringResource(R.string.add_token_price_unknown),
                                color = Color(0xFFE6AC00), fontSize = 12.sp
                            )
                        }
                    }
                }

                Button(
                    onClick = { viewModel.save() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                ) {
                    Text(stringResource(R.string.add_token_confirm), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun NetworkChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) AccentBlue else SurfaceColor,
        modifier = modifier,
        onClick = onClick
    ) {
        Box(Modifier.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (selected) Color.White else TextSecondary,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary, fontSize = 14.sp)
        Text(value, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}
