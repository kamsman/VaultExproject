package com.vaultex.ui.screens.walletmanager

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.vaultex.R
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.BgTertiary
import com.vaultex.ui.theme.BorderColor
import com.vaultex.ui.theme.Surface as SurfaceColor
import com.vaultex.ui.theme.SurfaceLight
import com.vaultex.ui.theme.TextMuted
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary

private data class TokenItem(
    val symbol: String,
    val name: String,
    val network: String,
    val balance: String,
    val isVisible: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenManagerScreen(navController: NavController) {
    var searchQuery by remember { mutableStateOf("") }
    var customAddress by remember { mutableStateOf("") }
    val networks = remember { listOf("ETH", "BNB", "TRX", "SOL") }
    var selectedNetwork by remember { mutableStateOf("ETH") }
    val tokens = remember {
        mutableStateListOf(
            TokenItem("ETH", "Ethereum", "ETH", "1.234", true),
            TokenItem("USDT", "Tether", "ETH", "450.00", true),
            TokenItem("UNI", "Uniswap", "ETH", "", false),
            TokenItem("LINK", "Chainlink", "ETH", "12.5", true),
            TokenItem("BNB", "BNB", "BNB", "0.85", true),
            TokenItem("TRX", "Tron", "TRX", "1 200", true),
            TokenItem("SOL", "Solana", "SOL", "3.2", true)
        )
    }
    val filtered = tokens.filter { token ->
        token.network == selectedNetwork &&
            (searchQuery.isBlank() ||
                token.symbol.contains(searchQuery, true) ||
                token.name.contains(searchQuery, true))
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.token_mgr_title),
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
                actions = {
                    IconButton(onClick = { navController.navigate(Routes.ADD_TOKEN) }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.token_mgr_add_custom), tint = AccentBlue)
                    }
                    IconButton(onClick = { navController.navigate(Routes.MANAGE_TOKENS) }) {
                        Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.token_mgr_visibility), tint = AccentBlue)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgPrimary)
            )
        },
        containerColor = BgPrimary
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Filtres réseau
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    networks.forEach { network ->
                        val selected = network == selectedNetwork
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (selected) AccentBlue else SurfaceColor)
                                .border(1.dp, if (selected) AccentBlue else BorderColor, RoundedCornerShape(18.dp))
                                .clickable { selectedNetwork = network }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                network,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected) Color.White else TextSecondary
                            )
                        }
                    }
                }
            }

            // Liste des tokens
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        filtered.forEachIndexed { index, token ->
                            TokenRow(
                                token = token,
                                onToggle = { checked ->
                                    val realIndex = tokens.indexOfFirst { it.symbol == token.symbol && it.network == token.network }
                                    if (realIndex >= 0) tokens[realIndex] = tokens[realIndex].copy(isVisible = checked)
                                }
                            )
                            if (index < filtered.lastIndex) {
                                HorizontalDivider(color = SurfaceLight, thickness = 1.dp)
                            }
                        }
                        if (filtered.isEmpty()) {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.dashboard_no_assets), color = TextSecondary, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // Ajouter token personnalisé
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.token_add_custom_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(12.dp))
                        TextField(
                            value = customAddress,
                            onValueChange = { customAddress = it },
                            placeholder = { Text(stringResource(R.string.token_contract_placeholder), color = TextMuted, fontSize = 14.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = BgTertiary,
                                unfocusedContainerColor = BgTertiary,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = AccentBlue
                            )
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { searchQuery = customAddress.trim() },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(22.dp),
                                border = BorderStroke(1.5.dp, AccentBlue),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue)
                            ) {
                                Text(stringResource(R.string.token_search_btn), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Button(
                                onClick = { navController.navigate(Routes.ADD_TOKEN) },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(22.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = Color.White)
                            ) {
                                Text(stringResource(R.string.add), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TokenRow(token: TokenItem, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(AccentBlue),
            contentAlignment = Alignment.Center
        ) {
            Text(
                token.name.take(2).uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(token.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
            Text(token.symbol, fontSize = 12.sp, color = TextSecondary)
        }
        if (token.balance.isNotBlank()) {
            Text(token.balance, fontSize = 14.sp, color = TextPrimary)
            Spacer(Modifier.width(10.dp))
        }
        Switch(
            checked = token.isVisible,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccentBlue,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = BgTertiary,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}
