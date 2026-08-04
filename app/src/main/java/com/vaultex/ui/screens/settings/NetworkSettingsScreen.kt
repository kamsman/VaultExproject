package com.vaultex.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.RestartAlt
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
import com.vaultex.ui.theme.AccentGreen
import com.vaultex.ui.theme.AccentRed
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.NetworkBnb
import com.vaultex.ui.theme.NetworkBtc
import com.vaultex.ui.theme.NetworkEth
import com.vaultex.ui.theme.NetworkSol
import com.vaultex.ui.theme.NetworkTrx
import com.vaultex.ui.theme.Surface
import com.vaultex.ui.theme.SurfaceLight
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextMuted
import com.vaultex.ui.theme.TextSecondary
import com.vaultex.ui.viewmodel.NetworkSettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkSettingsScreen(navController: NavHostController) {
    val viewModel: NetworkSettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    val scope = rememberCoroutineScope()
    // chain -> latence mesurée en ms (LATENCY_FAILED si échec)
    val latencies = remember { mutableStateMapOf<String, Long>() }

    // Test AUTOMATIQUE de chaque endpoint à l'ouverture → le badge affiche le
    // VRAI statut (Connecté / Hors ligne) au lieu d'un « Connecté » codé en dur.
    LaunchedEffect(Unit) {
        state.entries.forEach { e ->
            scope.launch { latencies[e.chain] = measureLatency(e.current) }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.network_settings_title),
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
                actions = {
                    IconButton(onClick = viewModel::reset) {
                        Icon(
                            Icons.Default.RestartAlt,
                            contentDescription = stringResource(R.string.network_reset),
                            tint = TextSecondary
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(0.dp))

            state.entries.forEach { entry ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(chainColor(entry.chain), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    entry.chain.take(2).uppercase(),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                entry.label,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            StatusPill(latencies[entry.chain])
                        }

                        TextField(
                            value = entry.current,
                            onValueChange = { viewModel.setUrl(entry.chain, it) },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = TextSecondary),
                            shape = RoundedCornerShape(10.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = SurfaceLight,
                                unfocusedContainerColor = SurfaceLight,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = AccentBlue
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            latencies[entry.chain]?.let { ms ->
                                LatencyPill(ms)
                                Spacer(Modifier.width(8.dp))
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    latencies[entry.chain] = measureLatency(entry.current)
                                }
                            }) {
                                Text(
                                    stringResource(R.string.network_settings_test),
                                    color = AccentBlue,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            TextButton(onClick = { viewModel.setUrl(entry.chain, entry.default) }) {
                                Text(
                                    stringResource(R.string.network_reset),
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = viewModel::save,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text(
                    if (state.saved) stringResource(R.string.network_saved) else stringResource(R.string.network_save),
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

private const val LATENCY_FAILED = -1L

private suspend fun measureLatency(url: String): Long = withContext(Dispatchers.IO) {
    try {
        val start = System.currentTimeMillis()
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "HEAD"
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.responseCode
        connection.disconnect()
        System.currentTimeMillis() - start
    } catch (e: Exception) {
        LATENCY_FAILED
    }
}

private fun chainColor(chain: String): Color = when (chain) {
    "btc" -> NetworkBtc
    "eth", "etherscan" -> NetworkEth
    "bnb", "bscscan" -> NetworkBnb
    "sol" -> NetworkSol
    "trx" -> NetworkTrx
    else -> Color(0xFF1A6FE8)
}

@Composable
private fun StatusPill(latencyMs: Long?) {
    // null = test en cours ; LATENCY_FAILED = injoignable ; sinon = connecté.
    val (color, label) = when {
        latencyMs == null -> TextMuted to stringResource(R.string.network_testing)
        latencyMs == LATENCY_FAILED -> AccentRed to stringResource(R.string.network_offline)
        else -> AccentGreen to stringResource(R.string.network_connected)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

@Composable
private fun LatencyPill(ms: Long) {
    val failed = ms == LATENCY_FAILED
    val color = if (failed) AccentRed else AccentGreen
    Text(
        text = if (failed) {
            stringResource(R.string.network_settings_test_failed)
        } else {
            stringResource(R.string.network_settings_latency_format, ms)
        },
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}
