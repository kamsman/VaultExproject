package com.vaultex.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.viewmodel.PanicPinViewModel

@Composable
fun PanicPinScreen(navController: NavController) {
    val viewModel: PanicPinViewModel = hiltViewModel()
    val saved by viewModel.saved.collectAsState()

    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(0) }
    var mismatch by remember { mutableStateOf(false) }
    val maxLen = 6

    // Sauvegarde réussie → retour
    LaunchedEffect(saved) {
        if (saved == true) navController.popBackStack()
    }

    // Quand confirmPin est complet → vérifier et sauvegarder
    LaunchedEffect(confirmPin) {
        if (confirmPin.length == maxLen) {
            if (confirmPin == pin) {
                viewModel.savePin(pin)
            } else {
                mismatch = true
                confirmPin = ""
                step = 0
                pin = ""
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A0A0A))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(48.dp))
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "Retour",
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.Start).size(24.dp)
                    .clickable { navController.popBackStack() }
            )
            Spacer(Modifier.height(32.dp))
            Text(
                if (step == 0) "PIN de Panique" else "Confirmer le PIN",
                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Ce PIN efface toutes les données du wallet\nsi saisi à l'écran de déverrouillage.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            if (mismatch) {
                Text("Les PINs ne correspondent pas", color = Color(0xFFEF4444), fontSize = 13.sp)
                LaunchedEffect(Unit) { kotlinx.coroutines.delay(2000); mismatch = false }
            }
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val current = if (step == 0) pin else confirmPin
                repeat(maxLen) { i ->
                    Box(
                        modifier = Modifier.size(14.dp).clip(CircleShape)
                            .background(if (i < current.length) Color(0xFFEF4444) else Color.White.copy(alpha = 0.2f))
                    )
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val digits = listOf("1","2","3","4","5","6","7","8","9","","0","⌫")
            digits.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    row.forEach { d ->
                        if (d.isEmpty()) {
                            Spacer(Modifier.size(72.dp))
                        } else {
                            Button(
                                onClick = {
                                    if (d == "⌫") {
                                        if (step == 0 && pin.isNotEmpty()) pin = pin.dropLast(1)
                                        else if (step == 1 && confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
                                    } else {
                                        if (step == 0 && pin.length < maxLen) {
                                            pin += d
                                            if (pin.length == maxLen) step = 1
                                        } else if (step == 1 && confirmPin.length < maxLen) {
                                            confirmPin += d
                                        }
                                    }
                                },
                                modifier = Modifier.size(72.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f))
                            ) {
                                Text(d, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
