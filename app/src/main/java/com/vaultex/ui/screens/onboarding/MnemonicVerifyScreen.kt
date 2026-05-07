package com.vaultex.ui.screens.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material3.*

import androidx.compose.runtime.*

import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.navigation.NavHostController

import com.vaultex.ui.components.PrimaryButton
import com.vaultex.ui.navigation.Routes

import com.vaultex.ui.theme.*

import com.vaultex.ui.utils.WalletMemory

import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MnemonicVerifyScreen(
    navController: NavHostController
) {

    /*
    =========================
    RANDOM WORD INDEX
    =========================
     */

    val randomIndex = remember {

        Random.nextInt(12)
    }

    /*
    =========================
    REAL WORD
    =========================
     */

    val correctWord =
        WalletMemory.mnemonicWords
            .getOrNull(randomIndex)
            ?: ""

    /*
    =========================
    STATES
    =========================
     */

    var word by remember {
        mutableStateOf("")
    }

    var error by remember {
        mutableStateOf("")
    }

    /*
    =========================
    UI
    =========================
     */

    Scaffold(
        topBar = {

            TopAppBar(
                title = {

                    Text(
                        text = "Vérification",
                        color = TextPrimary
                    )
                },

                colors = TopAppBarDefaults
                    .topAppBarColors(
                        containerColor = BgPrimary
                    )
            )
        },

        containerColor = BgPrimary
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(20.dp)
                .fillMaxSize()
        ) {

            /*
            =========================
            TITLE
            =========================
             */

            Text(
                text = "Confirmez votre phrase",
                color = TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            /*
            =========================
            DESCRIPTION
            =========================
             */

            Text(
                text =
                    "Entrez le mot numéro ${randomIndex + 1} de votre phrase de récupération.",

                color = TextSecondary,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )

            Spacer(
                modifier = Modifier.height(34.dp)
            )

            /*
            =========================
            INPUT CARD
            =========================
             */

            Card(
                modifier = Modifier.fillMaxWidth(),

                shape = RoundedCornerShape(24.dp),

                colors = CardDefaults.cardColors(
                    containerColor = BgSecondary
                )
            ) {

                Column(
                    modifier = Modifier.padding(20.dp)
                ) {

                    Text(
                        text = "Mot numéro ${randomIndex + 1}",
                        color = AccentGold,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(
                        modifier = Modifier.height(14.dp)
                    )

                    /*
                    =========================
                    INPUT
                    =========================
                     */

                    OutlinedTextField(
                        value = word,

                        onValueChange = {

                            word = it
                            error = ""
                        },

                        modifier = Modifier.fillMaxWidth(),

                        singleLine = true,

                        shape = RoundedCornerShape(18.dp),

                        placeholder = {

                            Text(
                                text = "Entrez le mot",
                                color = TextSecondary
                            )
                        },

                        colors =
                            OutlinedTextFieldDefaults.colors(

                                focusedBorderColor =
                                    AccentGold,

                                unfocusedBorderColor =
                                    BgSecondary,

                                focusedContainerColor =
                                    BgPrimary,

                                unfocusedContainerColor =
                                    BgPrimary,

                                cursorColor =
                                    AccentGold
                            ),

                        isError = error.isNotEmpty()
                    )

                    /*
                    =========================
                    ERROR
                    =========================
                     */

                    if (error.isNotEmpty()) {

                        Spacer(
                            modifier = Modifier.height(12.dp)
                        )

                        Text(
                            text = error,
                            color = AccentRed,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(
                modifier = Modifier.weight(1f)
            )

            /*
            =========================
            CONTINUE
            =========================
             */

            PrimaryButton(
                text = "Continuer",

                onClick = {

                    when {

                        word.isBlank() -> {

                            error =
                                "Veuillez entrer le mot"
                        }

                        word.trim().lowercase()
                                != correctWord.lowercase() -> {

                            error =
                                "Mot incorrect"
                        }

                        else -> {

                            /*
                            =========================
                            GO TO PIN SETUP
                            =========================
                             */

                            navController.navigate(
                                Routes.PIN_SETUP
                            ) {

                                popUpTo(Routes.WELCOME) {
                                    inclusive = true
                                }
                            }
                        }
                    }
                }
            )
        }
    }
}