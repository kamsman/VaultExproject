package com.vaultex.ui.screens.receive

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.vaultex.R
import com.vaultex.ui.theme.VaultExColors
import com.vaultex.ui.viewmodel.ReceiveViewModel

@Composable
fun ReceiveScreen(navController: NavController) {
    val viewModel: ReceiveViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    val chains = listOf("BTC", "ETH", "BNB", "SOL", "TRX")
    var selectedChain by remember { mutableStateOf("ETH") }
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    val currentAddress = state.addresses[selectedChain] ?: ""

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.receive_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VaultExColors.Background)
            )
        },
        containerColor = VaultExColors.Background
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VaultExColors.BluePrimary)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chains.forEach { chain ->
                        FilterChip(
                            selected = selectedChain == chain,
                            onClick = { selectedChain = chain; copied = false },
                            label = { Text(chain) }
                        )
                    }
                }

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = VaultExColors.CardBackground),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        val qrBitmap = remember(currentAddress) { generateQr(currentAddress, 512) }
                        qrBitmap?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = stringResource(R.string.receive_qr_code),
                                modifier = Modifier.size(220.dp).clip(RoundedCornerShape(12.dp))
                            )
                        } ?: Box(
                            Modifier.size(220.dp).background(VaultExColors.Background, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentAddress.isEmpty())
                                Icon(Icons.Default.ErrorOutline, null, tint = VaultExColors.Error, modifier = Modifier.size(48.dp))
                            else
                                CircularProgressIndicator(color = VaultExColors.BluePrimary)
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(selectedChain, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = VaultExColors.BluePrimary)
                    }
                }

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = VaultExColors.CardBackground)
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (currentAddress.isEmpty()) {
                            Text(stringResource(R.string.receive_wallet_not_initialized), fontSize = 13.sp, color = VaultExColors.Error)
                        } else {
                            Text(
                                currentAddress,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                color = VaultExColors.TextPrimary,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(
                                    onClick = { clipboard.setText(AnnotatedString(currentAddress)); copied = true },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        if (copied) Icons.Default.CheckCircle else Icons.Default.ContentCopy,
                                        null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (copied) stringResource(R.string.copied) else stringResource(R.string.copy))
                                }
                                Button(
                                    onClick = { },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = VaultExColors.BluePrimary),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.share))
                                }
                            }
                        }
                    }
                }

                Text(
                    stringResource(R.string.receive_warning, selectedChain),
                    fontSize = 12.sp,
                    color = VaultExColors.TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun generateQr(content: String, size: Int): Bitmap? {
    if (content.isEmpty()) return null
    return try {
        val matrix: BitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) for (y in 0 until size)
            bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        bmp
    } catch (_: Exception) { null }
}
