package com.vaultex.ui.screens.send

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.vaultex.R
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.BgTertiary
import com.vaultex.ui.theme.BorderColor
import com.vaultex.ui.theme.NetworkEth
import com.vaultex.ui.theme.SurfaceLight
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendConfirmScreen(navController: NavHostController) {

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.confirm_send),
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

            // Recap card
            SendCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.send_recap_title),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .background(NetworkEth, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("ET", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("0.05 ETH", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(
                        stringResource(R.string.approx_xof, "5 820"),
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }

                Spacer(Modifier.height(16.dp))

                RecapRow(stringResource(R.string.label_from), stringResource(R.string.my_wallet) + " · ETH")
                RecapDivider()
                RecapRow(stringResource(R.string.label_to), "0x742d…b12f")
                RecapDivider()
                RecapRow(stringResource(R.string.contacts_network), "Ethereum")
                RecapDivider()
                RecapRow(stringResource(R.string.label_fee), "~0.0002 ETH (~120 XOF)")
                RecapDivider()
                RecapRow(stringResource(R.string.label_total), "0.0502 ETH", valueColor = AccentBlue, valueBold = true)
            }

            // PIN hint banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgTertiary, RoundedCornerShape(10.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .width(4.dp)
                        .height(44.dp)
                        .background(AccentBlue, RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
                )
                Text(
                    "🔒 " + stringResource(R.string.send_confirm_pin_hint),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    fontSize = 13.sp,
                    color = TextPrimary
                )
            }

            Button(
                onClick = { navController.navigate(Routes.sendResult("0xDEMO")) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = Color.White)
            ) {
                Text(stringResource(R.string.send_confirm_and_send), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            OutlinedButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, AccentBlue),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue)
            ) {
                Text(stringResource(R.string.cancel), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun RecapRow(
    label: String,
    value: String,
    valueColor: Color = TextPrimary,
    valueBold: Boolean = false
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = TextSecondary)
        Text(
            value,
            fontSize = 13.sp,
            color = valueColor,
            fontWeight = if (valueBold) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun RecapDivider() =
    HorizontalDivider(color = SurfaceLight, thickness = 1.dp)
