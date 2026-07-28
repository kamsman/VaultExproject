package com.vaultex.ui.screens.security

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vaultex.R
import com.vaultex.ui.theme.*
import com.vaultex.ui.viewmodel.AntiPhishingViewModel

/**
 * Réglage du code anti-phishing — et surtout, EXPLICATION.
 *
 * Un dispositif de sécurité que l'utilisateur ne comprend pas ne le protège
 * pas : il définira un code, l'oubliera, et ne remarquera jamais son absence
 * dans une contrefaçon. L'écran consacre donc plus de place à expliquer
 * comment s'en servir qu'au champ de saisie lui-même.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AntiPhishingScreen(navController: NavHostController) {
    val viewModel: AntiPhishingViewModel = hiltViewModel()
    val saved by viewModel.code.collectAsState()
    var input by remember(saved) { mutableStateOf(saved) }
    var justSaved by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.antiphishing_title),
                        fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.back), tint = AccentBlue)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgPrimary)
            )
        },
        containerColor = BgPrimary
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(2.dp))

            // ─── Le problème, en une phrase concrète ───
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AccentGreen.copy(alpha = 0.09f))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.VerifiedUser, null, tint = AccentGreen, modifier = Modifier.size(34.dp))
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.antiphishing_headline),
                    fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = TextPrimary, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.antiphishing_intro),
                    fontSize = 13.sp, lineHeight = 19.sp,
                    color = TextSecondary, textAlign = TextAlign.Center
                )
            }

            // ─── Comment s'en servir : les trois réflexes ───
            Text(
                stringResource(R.string.antiphishing_how_title),
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary
            )
            ExplainRow(
                Icons.Default.Visibility, AccentBlue,
                stringResource(R.string.antiphishing_step1_title),
                stringResource(R.string.antiphishing_step1_body)
            )
            ExplainRow(
                Icons.Default.Block, AccentRed,
                stringResource(R.string.antiphishing_step2_title),
                stringResource(R.string.antiphishing_step2_body)
            )
            ExplainRow(
                Icons.Default.CheckCircle, AccentGreen,
                stringResource(R.string.antiphishing_step3_title),
                stringResource(R.string.antiphishing_step3_body)
            )

            // ─── Saisie ───
            Text(
                stringResource(R.string.antiphishing_field_label),
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                modifier = Modifier.padding(top = 4.dp)
            )
            OutlinedTextField(
                value = input,
                onValueChange = {
                    // Court et mémorisable : un code qu'on ne retient pas ne
                    // sera jamais remarqué absent.
                    if (it.length <= 16) { input = it; justSaved = false }
                },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.antiphishing_placeholder), color = TextMuted) },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                ),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentGreen,
                    unfocusedBorderColor = BorderColor,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            Text(
                stringResource(R.string.antiphishing_field_hint),
                fontSize = 11.sp, lineHeight = 16.sp, color = TextMuted
            )

            Button(
                onClick = { viewModel.save(input); justSaved = true },
                enabled = input.isNotBlank() && input.trim() != saved,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
            ) {
                Text(stringResource(R.string.save), fontWeight = FontWeight.Bold, color = Color.White)
            }

            if (justSaved) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.antiphishing_saved),
                        fontSize = 12.sp, color = AccentGreen
                    )
                }
            }

            if (saved.isNotBlank()) {
                TextButton(
                    onClick = { viewModel.clear(); input = ""; justSaved = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.antiphishing_remove), color = AccentRed, fontSize = 13.sp)
                }
            }

            // ─── Limite honnête ───
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    stringResource(R.string.antiphishing_limit),
                    fontSize = 11.sp, lineHeight = 16.sp, color = TextMuted
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ExplainRow(icon: ImageVector, tint: Color, title: String, body: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(tint.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(body, fontSize = 12.sp, lineHeight = 17.sp, color = TextSecondary)
        }
    }
}
