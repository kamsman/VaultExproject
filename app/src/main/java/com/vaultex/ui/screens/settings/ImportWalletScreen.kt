package com.vaultex.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.vaultex.R
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.AccentRed
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.BorderColor
import com.vaultex.ui.theme.TextMuted
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportWalletScreen(navController: NavHostController) {
    var mnemonic by remember { mutableStateOf("") }
    val wordCount = mnemonic.trim().split(Regex("\\s+")).count { it.isNotBlank() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.import_wallet_title),
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = AccentBlue
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
        ) {
            // Bandeau d'avertissement : remplacement du wallet actuel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFDE8EC)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(4.dp).height(40.dp).background(AccentRed))
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.import_replace_warning),
                    fontSize = 12.sp,
                    color = TextPrimary
                )
            }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text(
                    stringResource(R.string.import_mnemonic_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.import_mnemonic_hint),
                    fontSize = 13.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = mnemonic,
                    onValueChange = { mnemonic = it },
                    placeholder = {
                        Text(stringResource(R.string.import_mnemonic_placeholder), color = TextMuted)
                    },
                    modifier = Modifier.fillMaxWidth().height(130.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue,
                        unfocusedBorderColor = BorderColor,
                        focusedContainerColor = Color(0xFFF8FAFF),
                        unfocusedContainerColor = Color(0xFFF8FAFF)
                    )
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.import_word_count, wordCount),
                    fontSize = 12.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(18.dp))
                Text(
                    stringResource(R.string.import_or),
                    fontSize = 13.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(18.dp))

                OutlinedButton(
                    onClick = { },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.5.dp, AccentBlue)
                ) {
                    Text(
                        stringResource(R.string.import_private_key),
                        color = AccentBlue,
                        fontWeight = FontWeight.Bold
                    )
                }

            }

            Column(Modifier.padding(16.dp)) {
                Button(
                    onClick = { },
                    enabled = wordCount == 12 || wordCount == 24,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentBlue,
                        contentColor = Color.White
                    )
                ) {
                    Text(stringResource(R.string.import_confirm), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
