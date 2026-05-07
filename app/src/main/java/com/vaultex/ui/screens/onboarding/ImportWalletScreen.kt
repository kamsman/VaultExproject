package com.vaultex.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.vaultex.ui.components.PrimaryButton
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.*
import com.vaultex.ui.utils.WalletMemory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportWalletScreen(
    navController: NavHostController
) {

    /*
    =========================
    STATES
    =========================
     */

    var mnemonic by remember {
        mutableStateOf("")
    }

    var error by remember {
        mutableStateOf("")
    }

    /*
    =========================
    CLEAN WORDS
    =========================
     */

    val words = mnemonic
        .lowercase()
        .replace("\n", " ")
        .replace(",", " ")
        .replace(";", " ")
        .trim()
        .split("\\s+".toRegex())
        .filter { it.isNotBlank() }

    val wordCount = words.size

    /*
    =========================
    VALID
    =========================
     */

    val isValid = wordCount == 12

    /*
    =========================
    UI
    =========================
     */

    Scaffold(
        containerColor = BgPrimary
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            BgPrimary,
                            BgSecondary
                        )
                    )
                )
                .padding(padding)
                .imePadding()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(horizontal = 24.dp),

            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Spacer(
                modifier = Modifier.height(70.dp)
            )

            /*
            =========================
            ICON
            =========================
             */

            Text(
                text = "📥",
                fontSize = 70.sp
            )

            Spacer(
                modifier = Modifier.height(28.dp)
            )

            /*
            =========================
            TITLE
            =========================
             */

            Text(
                text = "Importer un wallet",
                color = TextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(14.dp)
            )

            /*
            =========================
            DESCRIPTION
            =========================
             */

            Text(
                text =
                    "Entrez votre phrase de récupération de 12 mots.",

                color = TextSecondary,
                fontSize = 15.sp,
                lineHeight = 24.sp
            )

            Spacer(
                modifier = Modifier.height(40.dp)
            )

            /*
            =========================
            CARD
            =========================
             */

            Card(
                modifier = Modifier.fillMaxWidth(),

                shape = RoundedCornerShape(30.dp),

                colors = CardDefaults.cardColors(
                    containerColor =
                        BgSecondary.copy(alpha = 0.95f)
                )
            ) {

                Column(
                    modifier = Modifier.padding(22.dp)
                ) {

                    /*
                    =========================
                    LABEL
                    =========================
                     */

                    Text(
                        text = "Phrase secrète",
                        color = AccentGold,
                        fontSize = 15.sp,
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
                        value = mnemonic,

                        onValueChange = { input ->

                            mnemonic = input
                                .lowercase()
                                .replace("\n", " ")
                                .replace(",", " ")
                                .replace(";", " ")
                                .replace(Regex("\\s+"), " ")
                                .trimStart()

                            error = ""
                        },

                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp),

                        placeholder = {

                            Text(
                                text =
                                    "word1 word2 word3 ...",

                                color = TextSecondary
                            )
                        },

                        keyboardOptions =
                            KeyboardOptions(
                                capitalization =
                                    KeyboardCapitalization.None
                            ),

                        shape = RoundedCornerShape(22.dp),

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
                            )
                    )

                    Spacer(
                        modifier = Modifier.height(18.dp)
                    )

                    /*
                    =========================
                    WORD COUNTER
                    =========================
                     */

                    Text(
                        text = "$wordCount / 12 mots",

                        color =
                            if (isValid)
                                AccentGold
                            else
                                TextSecondary,

                        fontSize = 14.sp
                    )

                    /*
                    =========================
                    ERROR
                    =========================
                     */

                    if (error.isNotEmpty()) {

                        Spacer(
                            modifier = Modifier.height(14.dp)
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
                modifier = Modifier.height(30.dp)
            )

            /*
            =========================
            WARNING
            =========================
             */

            Card(
                colors = CardDefaults.cardColors(
                    containerColor =
                        AccentRed.copy(alpha = 0.12f)
                ),

                shape = RoundedCornerShape(18.dp)
            ) {

                Text(
                    text =
                        "⚠️ Ne partagez jamais votre phrase secrète. Toute personne ayant accès à cette phrase peut contrôler votre wallet.",

                    color = AccentRed,

                    fontSize = 13.sp,

                    lineHeight = 22.sp,

                    modifier = Modifier.padding(18.dp)
                )
            }

            Spacer(
                modifier = Modifier.weight(1f)
            )

            /*
            =========================
            BUTTON
            =========================
             */

            PrimaryButton(
                text = "Continuer",

                onClick = {

                    when {

                        mnemonic.isBlank() -> {

                            error =
                                "Veuillez entrer votre phrase secrète"
                        }

                        wordCount != 12 -> {

                            error =
                                "La phrase doit contenir 12 mots"
                        }

                        else -> {

                            /*
                            =========================
                            SAVE IMPORTED WORDS
                            =========================
                             */

                            WalletMemory.mnemonicWords = words

                            /*
                            =========================
                            GO TO PIN
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

            Spacer(
                modifier = Modifier.height(40.dp)
            )
        }
    }
}