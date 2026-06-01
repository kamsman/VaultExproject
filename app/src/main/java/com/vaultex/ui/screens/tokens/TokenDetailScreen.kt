package com.vaultex.ui.screens.tokens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController

import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.*
import com.vaultex.ui.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenDetailScreen(navController: NavHostController, symbol: String) {

    val dashboardVm: DashboardViewModel = hiltViewModel()
    val uiState by dashboardVm.uiState.collectAsState()

    val token = uiState.tokens.find { it.symbol == symbol } ?: uiState.tokens.firstOrNull()

    var selectedRange by remember { mutableStateOf("1S") }
    val ranges = listOf("1J", "1S", "1M", "3M", "1A")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(token?.symbol ?: "Token", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgPrimary)
            )
        },
        containerColor = BgPrimary
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(BgPrimary, BgSecondary)))
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {

            Spacer(Modifier.height(16.dp))

            // ── Prix et variation ─────────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = BgSecondary)) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(token?.name ?: "—", color = TextSecondary, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        token?.valueUsd ?: "—",
                        color = TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Balance : ${token?.balance ?: "—"} ${token?.symbol ?: ""}",
                        color = TextSecondary, fontSize = 14.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Graphe prix (Canvas) ──────────────────────────────────────────
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = BgSecondary)) {
                Column(modifier = Modifier.padding(16.dp)) {

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ranges.forEach { r ->
                            FilterChip(selected = selectedRange == r, onClick = { selectedRange = r },
                                label = { Text(r, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentGold, selectedLabelColor = BgPrimary,
                                    containerColor = BgPrimary, labelColor = TextSecondary))
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Graphe synthétique — remplacer par MPAndroidChart ou Vico quand données réelles dispo
                    PriceChart(modifier = Modifier.fillMaxWidth().height(160.dp))

                    Spacer(Modifier.height(8.dp))
                    Text("Données CoinGecko · intégration complète à venir",
                        color = TextMuted, fontSize = 10.sp, modifier = Modifier.align(Alignment.End))
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Actions rapides ───────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { navController.navigate(Routes.SEND) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGold)
                ) {
                    Icon(Icons.Default.CallMade, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Envoyer", color = BgPrimary, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { navController.navigate(Routes.RECEIVE) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGold)
                ) {
                    Icon(Icons.Default.CallReceived, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Recevoir", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Adresse ───────────────────────────────────────────────────────
            token?.address?.takeIf { it.isNotBlank() }?.let { addr ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BgSecondary)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Adresse", color = AccentGold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text(addr, color = TextPrimary, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PriceChart(modifier: Modifier) {
    // Données fictives pour l'affichage — remplacer par CoinGeckoChartDto
    val points = listOf(0.3f, 0.5f, 0.4f, 0.6f, 0.55f, 0.7f, 0.65f, 0.8f, 0.75f, 0.9f, 0.85f, 1.0f)

    androidx.compose.foundation.Canvas(modifier = modifier.clip(RoundedCornerShape(12.dp))) {
        val w = size.width
        val h = size.height
        val step = w / (points.size - 1)

        val path = Path()
        val fillPath = Path()

        points.forEachIndexed { i, v ->
            val x = i * step
            val y = h - (v * h * 0.8f) - h * 0.05f
            if (i == 0) { path.moveTo(x, y); fillPath.moveTo(x, h) }
            path.lineTo(x, y)
            fillPath.lineTo(x, y)
        }
        fillPath.lineTo(w, h)
        fillPath.close()

        drawPath(fillPath, brush = Brush.verticalGradient(
            listOf(AccentGold.copy(alpha = 0.25f), Color.Transparent)))
        drawPath(path, color = AccentGold, style = Stroke(width = 2.dp.toPx()))

        // Points de début / fin
        val lastX = (points.size - 1) * step
        val lastY = h - (points.last() * h * 0.8f) - h * 0.05f
        drawCircle(AccentGold, radius = 4.dp.toPx(), center = Offset(lastX, lastY))
    }
}
