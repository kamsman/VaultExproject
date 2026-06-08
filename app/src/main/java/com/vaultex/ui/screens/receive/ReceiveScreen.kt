package com.vaultex.ui.screens.receive

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.vaultex.ui.theme.VaultExColors

@Composable
fun ReceiveScreen(navController: NavController) {
    val chains = listOf("BTC", "ETH", "BNB", "SOL", "TRX")
    var selectedChain by remember { mutableStateOf("ETH") }
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    val addresses = mapOf(
        "BTC" to "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh",
        "ETH" to "0x742d35Cc6634C0532925a3b8D4C9B2A3C4D5E6F7",
        "BNB" to "0x742d35Cc6634C0532925a3b8D4C9B2A3C4D5E6F7",
        "SOL" to "7EcDhSYGxXyscszYEp35KHN8vvw3svAuLKTzXwCFLtV1",
        "TRX" to "TJRabPrwbZy45sbavfcjinPJC18kjpRTv8"
    )
    val currentAddress = addresses[selectedChain] ?: ""

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recevoir", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = VaultExColors.Background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Chain selector
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chains.forEach { chain ->
                    FilterChip(selected = selectedChain == chain, onClick = { selectedChain = chain; copied = false }, label = { Text(chain) })
                }
            }

            // QR Code
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val qrBitmap = remember(currentAddress) { generateQr(currentAddress, 512) }
                    qrBitmap?.let {
                        Image(bitmap = it.asImageBitmap(), contentDescription = "QR Code",
                            modifier = Modifier.size(220.dp).clip(RoundedCornerShape(12.dp)))
                    } ?: Box(Modifier.size(220.dp).background(VaultExColors.Background, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = VaultExColors.BluePrimary)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(selectedChain, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = VaultExColors.BluePrimary)
                }
            }

            // Address
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(currentAddress, fontSize = 12.sp, textAlign = TextAlign.Center, color = VaultExColors.TextPrimary,
                        modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = {
                            clipboard.setText(AnnotatedString(currentAddress)); copied = true
                        }, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                            Icon(if (copied) Icons.Default.CheckCircle else Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (copied) "Copié !" else "Copier")
                        }
                        Button(onClick = { }, shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VaultExColors.BluePrimary),
                            modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Partager")
                        }
                    }
                }
            }

            Text("Envoyez uniquement des $selectedChain à cette adresse.\nToute autre crypto sera perdue définitivement.",
                fontSize = 12.sp, color = VaultExColors.TextSecondary, textAlign = TextAlign.Center)
        }
    }
}

private fun generateQr(content: String, size: Int): Bitmap? {
    return try {
        val matrix: BitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) for (y in 0 until size) bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        bmp
    } catch (e: Exception) { null }
}
