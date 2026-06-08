package com.vaultex.ui.screens.settings

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.vaultex.ui.theme.VaultExColors

@Composable
fun BackupScreen(navController: NavController) {
    var showMnemonic by remember { mutableStateOf(false) }
    var pinVerified by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sauvegarde", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = VaultExColors.Background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Warning
            Surface(
                color = Color(0xFFFFF3CD),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Ne partagez jamais votre phrase secrète", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF92400E))
                        Text("Quiconque connaît votre phrase peut accéder à tous vos fonds.", fontSize = 13.sp, color = Color(0xFF92400E))
                    }
                }
            }

            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, tint = VaultExColors.BluePrimary, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Phrase de récupération", fontWeight = FontWeight.SemiBold)
                            Text("12 mots BIP-39", fontSize = 12.sp, color = VaultExColors.TextSecondary)
                        }
                    }

                    if (!showMnemonic) {
                        Button(
                            onClick = { showMnemonic = true },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VaultExColors.BluePrimary)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Révéler la phrase secrète")
                        }
                    } else {
                        val fakeMnemonic = "apple brave crystal dragon echo flame garden hero ivory jungle kite lemon"
                        val words = fakeMnemonic.split(" ")
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(VaultExColors.Background)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            words.chunked(3).forEachIndexed { rowIdx, rowWords ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    rowWords.forEachIndexed { colIdx, word ->
                                        val index = rowIdx * 3 + colIdx + 1
                                        MnemonicWord(index, word, Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                        Button(
                            onClick = { showMnemonic = false },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VaultExColors.Error)
                        ) { Text("Masquer") }
                    }
                }
            }

            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Checklist de sécurité", fontWeight = FontWeight.SemiBold)
                    ChecklistItem("Écrivez votre phrase sur papier")
                    ChecklistItem("Stockez dans un endroit sûr")
                    ChecklistItem("Ne prenez pas de photo")
                    ChecklistItem("Ne partagez avec personne")
                    ChecklistItem("Testez la récupération")
                }
            }
        }
    }
}

@Composable
private fun MnemonicWord(index: Int, word: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$index.", fontSize = 11.sp, color = VaultExColors.TextSecondary, modifier = Modifier.width(20.dp))
        Text(word, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ChecklistItem(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = VaultExColors.Success, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 13.sp, color = VaultExColors.TextSecondary)
    }
}
