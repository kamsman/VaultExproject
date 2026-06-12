package com.vaultex.ui.screens.tokens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
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
import androidx.navigation.NavHostController
import com.vaultex.R
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.AccentRed
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.BgTertiary
import com.vaultex.ui.theme.Surface as SurfaceColor
import com.vaultex.ui.theme.SurfaceLight
import com.vaultex.ui.theme.TextMuted
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary

private data class ManagedToken(
    val symbol: String,
    val name: String,
    val standard: String,
    val isVisible: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageTokensScreen(navController: NavHostController) {
    var searchQuery by remember { mutableStateOf("") }
    val tokens = remember {
        mutableStateListOf(
            ManagedToken("ETH", "Ethereum", "ERC-20", true),
            ManagedToken("USDT", "USDT", "TRC-20", true),
            ManagedToken("LINK", "Chainlink", "ERC-20", true),
            ManagedToken("UNI", "Uniswap", "ERC-20", false),
            ManagedToken("AAVE", "Aave", "ERC-20", false),
            ManagedToken("MATIC", "Matic", "ERC-20", true)
        )
    }
    val filtered = tokens.filter {
        searchQuery.isBlank() ||
            it.symbol.contains(searchQuery, true) ||
            it.name.contains(searchQuery, true)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.manage_tokens_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
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
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.token_mgr_search), color = TextMuted, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SurfaceColor,
                        unfocusedContainerColor = SurfaceColor,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = AccentBlue
                    )
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        filtered.forEachIndexed { index, token ->
                            ManagedTokenRow(
                                token = token,
                                onToggle = { checked ->
                                    val realIndex = tokens.indexOfFirst { it.symbol == token.symbol }
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
        }
    }
}

@Composable
private fun ManagedTokenRow(token: ManagedToken, onToggle: (Boolean) -> Unit) {
    val isTrc = token.standard.equals("TRC-20", ignoreCase = true)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isTrc) AccentRed else AccentBlue),
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
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isTrc) AccentRed.copy(alpha = 0.12f) else BgTertiary)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    token.standard,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isTrc) AccentRed else AccentBlue
                )
            }
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
