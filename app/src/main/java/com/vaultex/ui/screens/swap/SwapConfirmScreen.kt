package com.vaultex.ui.screens.swap

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.vaultex.R
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.AccentRed
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.BgTertiary
import com.vaultex.ui.theme.BorderColor
import com.vaultex.ui.theme.NetworkEth
import com.vaultex.ui.theme.Surface as SurfaceColor
import com.vaultex.ui.theme.SurfaceLight
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwapConfirmScreen(navController: NavHostController) {
    // Données de démonstration (écran prototype, pas encore branché à un ViewModel)
    val fromToken = "ETH"
    val toToken = "USDT"
    val fromAmount = "0.5 ETH"
    val toAmount = "1 225 USDT"
    val rateValue = "1 ETH = 2 450 USDT"
    val feeValue = "1.5% (~18 USDT)"
    val slippageValue = "1%"
    val viaValue = "ChangeNOW"
    val receiveMinValue = "~1 206 USDT"

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.swap_confirm_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Carte récapitulative
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceColor,
                border = BorderStroke(1.dp, BorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            stringResource(R.string.swap_you_exchange),
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            TokenCircle(fromToken.take(2), NetworkEth)
                            Icon(
                                Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                            TokenCircle(toToken.take(2), AccentRed)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(fromAmount, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                            Text(toAmount, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    DetailRow(stringResource(R.string.swap_rate), rateValue)
                    Divider(color = SurfaceLight, thickness = 1.dp)
                    DetailRow(stringResource(R.string.swap_fee_vaultex_label), feeValue)
                    Divider(color = SurfaceLight, thickness = 1.dp)
                    DetailRow(stringResource(R.string.swap_slippage_max), slippageValue)
                    Divider(color = SurfaceLight, thickness = 1.dp)
                    DetailRow(stringResource(R.string.swap_via), viaValue, valueColor = AccentBlue)
                    Divider(color = SurfaceLight, thickness = 1.dp)
                    DetailRow(
                        stringResource(R.string.swap_receive_min),
                        receiveMinValue,
                        valueColor = AccentBlue,
                        valueBold = true
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // Bandeau PIN
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(BgTertiary)
                    .height(IntrinsicSize.Min)
            ) {
                Box(
                    Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(AccentBlue)
                )
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.swap_confirm_pin_hint),
                        fontSize = 12.sp,
                        color = TextPrimary
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Button(
                onClick = { navController.navigate(Routes.swapResult("0xDEMO")) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text(
                    stringResource(R.string.swap_confirm_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }

            Spacer(Modifier.height(14.dp))

            OutlinedButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, AccentBlue),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue)
            ) {
                Text(
                    stringResource(R.string.cancel),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun TokenCircle(label: String, color: Color) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label.uppercase(),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = TextPrimary,
    valueBold: Boolean = false
) =
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = TextSecondary)
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = if (valueBold) FontWeight.Bold else FontWeight.SemiBold,
            color = valueColor
        )
    }
