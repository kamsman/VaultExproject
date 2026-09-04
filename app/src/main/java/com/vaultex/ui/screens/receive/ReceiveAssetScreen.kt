package com.vaultex.ui.screens.receive

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vaultex.R
import com.vaultex.ui.components.CryptoIcon
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.BgTertiary
import com.vaultex.ui.theme.BorderColor
import com.vaultex.ui.theme.Surface as SurfaceColor
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary
import com.vaultex.ui.viewmodel.ReceiveViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveAssetScreen(navController: NavController, symbol: String, chain: String) {
    val viewModel: ReceiveViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    val chainKey = chain.uppercase()
    val address = state.addresses[chainKey] ?: ""
    val title = detailTitle(symbol, chainKey)
    val greenNote = Color(0xFF22C55E)

    val qr = remember(address) { generateQr(address, 640, highEcc = true) }

    fun copy() {
        if (address.isEmpty()) return
        clipboard.setText(AnnotatedString(address))
        Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
    }
    fun share() {
        if (address.isEmpty()) return
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, address)
        }, null))
    }
    fun save() {
        val bmp = qr ?: return
        val ok = saveQrToGallery(context, bmp, "VaultEx-$symbol")
        Toast.makeText(
            context,
            context.getString(if (ok) R.string.receive_qr_saved else R.string.receive_qr_save_failed),
            Toast.LENGTH_SHORT
        ).show()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.receive_asset_title, title), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back), tint = AccentBlue)
                    }
                },
                /*
                LE PARTAGE N'EXISTE QU'UNE FOIS, EN BAS.

                Il figurait ici en icône nue ET dans la rangée d'actions sous
                le QR. Deux commandes identiques dans un même écran font
                hésiter — on cherche ce qui les distingue, il n'y a rien.

                C'est celle du bas qui reste : elle porte son nom, et elle est
                posée à côté du QR sur lequel elle agit, entre Copier et
                Enregistrer. Isolée en haut, l'icône ne disait même pas ce
                qu'elle partageait — l'écran ? l'adresse ? l'image ?
                */
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgPrimary)
            )
        },
        containerColor = BgPrimary
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Carte QR avec logo du token au centre
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                border = BorderStroke(1.dp, greenNote.copy(alpha = 0.3f)),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.receive_scan_qr), fontSize = 13.sp, color = Color(0xFF6B7280))
                    Spacer(Modifier.height(12.dp))
                    Box(contentAlignment = Alignment.Center) {
                        qr?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = stringResource(R.string.receive_qr_code),
                                modifier = Modifier.size(230.dp)
                            )
                        } ?: Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AccentBlue)
                        }
                        // Logo du token au centre (cercle blanc).
                        Box(
                            Modifier.size(52.dp).clip(CircleShape).background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).background(AccentBlue),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(symbol.take(2), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                coil.compose.AsyncImage(
                                    model = CryptoIcon.url(symbol),
                                    contentDescription = symbol,
                                    modifier = Modifier.size(44.dp).clip(CircleShape)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Badge RÉSEAU proéminent : dire au payeur sur quel réseau envoyer.
            // Une confusion USDT (TRC20 vs ERC20 vs BEP20) peut coûter les fonds.
            val netLong = receiveNetworkLong(symbol, chainKey)
            Surface(shape = RoundedCornerShape(50), color = AccentBlue.copy(alpha = 0.14f)) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Language, null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.receive_network_label, netLong),
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentBlue
                    )
                }
            }

            // Avertissement FORT pour les tokens (USDT* / ERC20 / BEP20) : un
            // envoi sur le mauvais réseau peut être perdu. Inutile pour les
            // natifs (une adresse BTC ne reçoit que du BTC, pas d'ambiguïté).
            val isToken = symbol.startsWith("USDT") || symbol !in listOf("BTC", "ETH", "BNB", "SOL", "TRX")
            if (isToken) {
                Spacer(Modifier.height(10.dp))
                Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFF59E0B).copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.receive_network_warning, netLong),
                            fontSize = 12.sp, color = TextPrimary, lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Text(stringResource(R.string.receive_your_asset_address, title), fontSize = 13.sp, color = TextSecondary)
            Spacer(Modifier.height(8.dp))

            // Adresse + bouton copier
            Surface(shape = RoundedCornerShape(12.dp), color = SurfaceColor, border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        address.ifEmpty { stringResource(R.string.receive_wallet_not_initialized) },
                        fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary,
                        textAlign = TextAlign.Start, modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy_address),
                        tint = AccentBlue, modifier = Modifier.size(22.dp).clickable { copy() }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Note de sécurité (verte)
            Surface(shape = RoundedCornerShape(10.dp), color = greenNote.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, null, tint = greenNote, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.receive_safe_share, symbol), fontSize = 12.sp, color = TextPrimary)
                }
            }

            Spacer(Modifier.height(22.dp))

            // Actions : Copier · Partager · Enregistrer le QR
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ReceiveAction(Icons.Default.ContentCopy, stringResource(R.string.copy_address)) { copy() }
                ReceiveAction(Icons.Default.Share, stringResource(R.string.share)) { share() }
                ReceiveAction(Icons.Default.Download, stringResource(R.string.receive_save_qr)) { save() }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ReceiveAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }.padding(8.dp)) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(BgTertiary), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = AccentBlue, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, color = TextSecondary)
    }
}

/** Nom de réseau LISIBLE (pour le badge + l'avertissement de réception). */
private fun receiveNetworkLong(symbol: String, chainKey: String): String = when {
    symbol == "USDT" -> "Tron · TRC20"
    symbol == "USDT-ETH" -> "Ethereum · ERC20"
    symbol == "USDT-BNB" -> "BNB Chain · BEP20"
    symbol == "BTC" -> "Bitcoin"
    symbol == "ETH" -> "Ethereum"
    symbol == "BNB" -> "BNB Chain"
    symbol == "SOL" -> "Solana"
    symbol == "TRX" -> "Tron"
    chainKey == "BNB" -> "BNB Chain · BEP20"
    chainKey == "ETH" -> "Ethereum · ERC20"
    else -> chainKey
}

/** Titre d'un actif à recevoir, à partir du symbole + chaîne de l'adresse. */
private fun detailTitle(symbol: String, chainKey: String): String = when {
    symbol == "USDT" -> "USDT (TRC20)"
    symbol == "USDT-ETH" -> "USDT (ERC20)"
    symbol == "USDT-BNB" -> "USDT (BEP20)"
    symbol in listOf("BTC", "ETH", "BNB", "SOL", "TRX") -> symbol
    chainKey == "ETH" -> "$symbol (ERC20)"
    chainKey == "BNB" -> "$symbol (BEP20)"
    else -> symbol
}
