package com.vaultex.ui.screens.receive

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController

import com.vaultex.ui.theme.*
import com.vaultex.ui.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveScreen(navController: NavHostController) {

    val viewModel: DashboardViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var selectedChain by remember { mutableStateOf("ETH") }
    var copied by remember { mutableStateOf(false) }

    val chains = listOf("BTC", "ETH", "BNB", "SOL", "TRX")

    val address = when (selectedChain) {
        "BTC" -> uiState.addresses?.btc
        "ETH" -> uiState.addresses?.eth
        "BNB" -> uiState.addresses?.bnb
        "SOL" -> uiState.addresses?.sol
        "TRX" -> uiState.addresses?.trx
        else -> null
    } ?: "Chargement..."

    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(2000)
            copied = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recevoir", color = TextPrimary) },
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
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            // Sélecteur réseau
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = BgSecondary)) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Réseau", color = AccentGold, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        chains.forEach { chain ->
                            FilterChip(
                                selected = selectedChain == chain,
                                onClick = { selectedChain = chain; copied = false },
                                label = { Text(chain, fontSize = 13.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentGold,
                                    selectedLabelColor = BgPrimary,
                                    containerColor = BgPrimary,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // QR Code placeholder
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .background(BgSecondary, RoundedCornerShape(20.dp))
                    .border(2.dp, AccentGold.copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(color = AccentGold, modifier = Modifier.size(40.dp))
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("▓▓▓▓▓▓▓▓", color = AccentGold.copy(alpha = 0.3f), fontSize = 22.sp)
                        Text("▓  ▓  ▓▓", color = AccentGold.copy(alpha = 0.3f), fontSize = 22.sp)
                        Text("▓▓▓▓▓▓▓▓", color = AccentGold.copy(alpha = 0.3f), fontSize = 22.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("QR Code", color = TextMuted, fontSize = 11.sp)
                        Text("(intégration à venir)", color = TextMuted, fontSize = 10.sp)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Adresse
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = BgSecondary)) {
                Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Votre adresse $selectedChain", color = AccentGold, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = address,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("address", address))
                                copied = true
                            },
                            border = ButtonDefaults.outlinedButtonBorder.copy(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGold)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (copied) "Copié !" else "Copier")
                        }
                        OutlinedButton(
                            onClick = { /* Share intent TODO */ },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Partager")
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = AccentGold.copy(alpha = 0.08f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = "⚠️ N'envoyez que des actifs $selectedChain sur cette adresse. " +
                            "Envoyer un autre type d'actif entraînerait une perte définitive.",
                    color = AccentGold.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(14.dp)
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
