package com.vaultex.ui.screens.send

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.vaultex.R
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.BgTertiary
import com.vaultex.ui.theme.SurfaceLight
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendFormScreen(navController: NavHostController) {

    val chains = listOf("BTC", "ETH", "BNB", "TRX", "SOL")
    var selectedChain by remember { mutableStateOf("ETH") }
    var address by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("0.05") }
    var selectedSpeed by remember { mutableStateOf(1) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.send_form_title, selectedChain),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = AccentBlue)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgPrimary)
            )
        },
        containerColor = BgPrimary
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Token chips row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                chains.forEach { chain ->
                    SendTokenChip(
                        label = chain,
                        selected = selectedChain == chain,
                        onClick = { selectedChain = chain }
                    )
                }
            }

            // Recipient + amount card
            SendCard {
                Text(stringResource(R.string.send_form_recipient_label), fontSize = 13.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SendField(
                        value = address,
                        onValueChange = { address = it },
                        placeholder = stringResource(R.string.send_form_address_placeholder),
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgTertiary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = stringResource(R.string.qr_label),
                            tint = AccentBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SurfaceLight,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "✓ " + stringResource(R.string.send_address_valid),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        fontSize = 12.sp,
                        color = AccentBlue
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    stringResource(R.string.send_form_amount_label, selectedChain),
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SendField(
                        value = amount,
                        onValueChange = { amount = it },
                        placeholder = "0.00",
                        modifier = Modifier.weight(1f)
                    )
                    Surface(shape = RoundedCornerShape(12.dp), color = SurfaceLight) {
                        Text(
                            "$selectedChain ▾",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.approx_xof, "5 820") + " · " +
                            stringResource(R.string.balance_label, "0.812 $selectedChain"),
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Text(
                        stringResource(R.string.max_label),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentBlue
                    )
                }
            }

            // Network fee card
            SendCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.network_fee), fontSize = 13.sp, color = TextSecondary)
                    Text(stringResource(R.string.send_fee_speed_caption), fontSize = 11.sp, color = TextSecondary)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    FeeSpeedOption(
                        label = "🐢 " + stringResource(R.string.fee_slow),
                        sub = "~60 XOF",
                        selected = selectedSpeed == 0,
                        onClick = { selectedSpeed = 0 },
                        modifier = Modifier.weight(1f)
                    )
                    FeeSpeedOption(
                        label = "⚡ " + stringResource(R.string.fee_normal),
                        sub = "~120 XOF",
                        selected = selectedSpeed == 1,
                        onClick = { selectedSpeed = 1 },
                        modifier = Modifier.weight(1f)
                    )
                    FeeSpeedOption(
                        label = "🚀 " + stringResource(R.string.fee_fast),
                        sub = "~240 XOF",
                        selected = selectedSpeed == 2,
                        onClick = { selectedSpeed = 2 },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { navController.navigate(Routes.SEND_CONFIRM) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = Color.White)
            ) {
                Text(stringResource(R.string.confirm_send), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun FeeSpeedOption(
    label: String,
    sub: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) AccentBlue else SurfaceLight,
        modifier = modifier
    ) {
        Column(
            Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) Color.White else TextPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                sub,
                fontSize = 10.sp,
                color = if (selected) Color.White.copy(alpha = 0.85f) else TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
