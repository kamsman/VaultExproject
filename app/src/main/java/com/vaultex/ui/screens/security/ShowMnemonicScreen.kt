package com.vaultex.ui.screens.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController

import com.vaultex.ui.theme.*
import com.vaultex.ui.viewmodel.ShowMnemonicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowMnemonicScreen(navController: NavHostController) {

    val viewModel: ShowMnemonicViewModel = hiltViewModel()
    val words by viewModel.words.collectAsState()
    var revealed by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        viewModel.load()
        onDispose { viewModel.clear() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Phrase mnémonique", color = TextPrimary) },
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
            Spacer(Modifier.height(20.dp))

            Card(colors = CardDefaults.cardColors(containerColor = AccentRed.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    "⚠️ Ne partagez jamais ces mots. Toute personne les possédant peut " +
                    "accéder à vos fonds. Notez-les sur papier dans un endroit sécurisé.",
                    color = AccentRed, fontSize = 13.sp, lineHeight = 20.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = BgSecondary)) {
                Column(modifier = Modifier.padding(16.dp)) {

                    if (words.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp),
                            contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AccentGold)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.height(280.dp)
                        ) {
                            itemsIndexed(words) { i, word ->
                                Row(
                                    modifier = Modifier
                                        .background(BgPrimary, RoundedCornerShape(10.dp))
                                        .padding(horizontal = 10.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${i + 1}.", color = TextMuted, fontSize = 11.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (revealed) word else "••••••",
                                        color = TextPrimary, fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    TextButton(onClick = { revealed = !revealed }, modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            imageVector = if (revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null, tint = AccentGold
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (revealed) "Masquer" else "Afficher les mots", color = AccentGold)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
