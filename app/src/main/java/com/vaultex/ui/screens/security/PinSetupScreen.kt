package com.vaultex.ui.screens.security

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff

import androidx.compose.material3.*

import androidx.compose.runtime.*

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.navigation.NavHostController

import com.vaultex.ui.components.PrimaryButton
import com.vaultex.ui.navigation.Routes

import com.vaultex.ui.theme.*

import com.vaultex.ui.utils.PinMemory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinSetupScreen(
    navController: NavHostController
) {

    /*
    =========================
    STATES
    =========================
     */

    var pin by remember {
        mutableStateOf("")
    }

    var confirmPin by remember {
        mutableStateOf("")
    }

    var error by remember {
        mutableStateOf("")
    }

    /*
    =========================
    SHOW / HIDE
    =========================
     */

    var showPin by remember {
        mutableStateOf(false)
    }

    var showConfirmPin by remember {
        mutableStateOf(false)
    }

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
            LOCK ICON
            =========================
             */

            Box(
                modifier = Modifier
                    .size(120.dp)
                    .shadow(
                        elevation = 25.dp,
                        shape = CircleShape
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                AccentGold.copy(alpha = 0.25f),
                                BgSecondary
                            )
                        )
                    ),

                contentAlignment = Alignment.Center
            ) {

                Text(
                    text = "🔐",
                    fontSize = 58.sp
                )
            }

            Spacer(
                modifier = Modifier.height(36.dp)
            )

            /*
            =========================
            TITLE
            =========================
             */

            Text(
                text = "Créer un code PIN",
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
                    "Votre PIN sera demandé à chaque ouverture du wallet.",

                color = TextSecondary,
                fontSize = 15.sp,
                lineHeight = 24.sp
            )

            Spacer(
                modifier = Modifier.height(45.dp)
            )

            /*
            =========================
            PREMIUM CARD
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
                    modifier = Modifier
                        .padding(
                            horizontal = 22.dp,
                            vertical = 26.dp
                        )
                ) {

                    /*
                    =========================
                    PIN TITLE
                    =========================
                     */

                    Text(
                        text = "Code PIN",
                        color = AccentGold,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(
                        modifier = Modifier.height(14.dp)
                    )

                    /*
                    =========================
                    PIN FIELD
                    =========================
                     */

                    OutlinedTextField(
                        value = pin,

                        onValueChange = {

                            if (
                                it.length <= 6 &&
                                it.all { c -> c.isDigit() }
                            ) {
                                pin = it
                            }
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        placeholder = {

                            Text(
                                text = "••••••",
                                color = TextSecondary
                            )
                        },

                        visualTransformation =

                            if (showPin)
                                VisualTransformation.None
                            else
                                PasswordVisualTransformation(),

                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType =
                                    KeyboardType.Number
                            ),

                        trailingIcon = {

                            IconButton(
                                onClick = {
                                    showPin = !showPin
                                }
                            ) {

                                Icon(
                                    imageVector =

                                        if (showPin)
                                            Icons.Default.VisibilityOff
                                        else
                                            Icons.Default.Visibility,

                                    contentDescription = null,

                                    tint = AccentGold
                                )
                            }
                        },

                        singleLine = true,

                        shape = RoundedCornerShape(20.dp),

                        textStyle =
                            LocalTextStyle.current.copy(
                                color = TextPrimary,
                                fontSize = 22.sp,
                                fontWeight =
                                    FontWeight.Bold
                            ),

                        colors =
                            OutlinedTextFieldDefaults.colors(

                                focusedBorderColor =
                                    AccentGold,

                                unfocusedBorderColor =
                                    Color.Transparent,

                                focusedContainerColor =
                                    BgPrimary,

                                unfocusedContainerColor =
                                    BgPrimary,

                                cursorColor =
                                    AccentGold
                            )
                    )

                    Spacer(
                        modifier = Modifier.height(28.dp)
                    )

                    /*
                    =========================
                    CONFIRM TITLE
                    =========================
                     */

                    Text(
                        text = "Confirmer le PIN",
                        color = AccentGold,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(
                        modifier = Modifier.height(14.dp)
                    )

                    /*
                    =========================
                    CONFIRM FIELD
                    =========================
                     */

                    OutlinedTextField(
                        value = confirmPin,

                        onValueChange = {

                            if (
                                it.length <= 6 &&
                                it.all { c -> c.isDigit() }
                            ) {
                                confirmPin = it
                            }
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        placeholder = {

                            Text(
                                text = "••••••",
                                color = TextSecondary
                            )
                        },

                        visualTransformation =

                            if (showConfirmPin)
                                VisualTransformation.None
                            else
                                PasswordVisualTransformation(),

                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType =
                                    KeyboardType.Number
                            ),

                        trailingIcon = {

                            IconButton(
                                onClick = {

                                    showConfirmPin =
                                        !showConfirmPin
                                }
                            ) {

                                Icon(
                                    imageVector =

                                        if (showConfirmPin)
                                            Icons.Default.VisibilityOff
                                        else
                                            Icons.Default.Visibility,

                                    contentDescription = null,

                                    tint = AccentGold
                                )
                            }
                        },

                        singleLine = true,

                        shape = RoundedCornerShape(20.dp),

                        textStyle =
                            LocalTextStyle.current.copy(
                                color = TextPrimary,
                                fontSize = 22.sp,
                                fontWeight =
                                    FontWeight.Bold
                            ),

                        colors =
                            OutlinedTextFieldDefaults.colors(

                                focusedBorderColor =
                                    AccentGold,

                                unfocusedBorderColor =
                                    Color.Transparent,

                                focusedContainerColor =
                                    BgPrimary,

                                unfocusedContainerColor =
                                    BgPrimary,

                                cursorColor =
                                    AccentGold
                            )
                    )

                    /*
                    =========================
                    PIN INDICATOR
                    =========================
                     */

                    Spacer(
                        modifier = Modifier.height(26.dp)
                    )

                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(12.dp),

                        modifier =
                            Modifier.align(
                                Alignment.CenterHorizontally
                            )
                    ) {

                        repeat(6) { index ->

                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(

                                        if (index < pin.length)
                                            AccentGold
                                        else
                                            BgPrimary
                                    )
                                    .border(
                                        width = 1.dp,
                                        color =
                                            AccentGold.copy(
                                                alpha = 0.5f
                                            ),
                                        shape = CircleShape
                                    )
                            )
                        }
                    }

                    /*
                    =========================
                    ERROR
                    =========================
                     */

                    if (error.isNotEmpty()) {

                        Spacer(
                            modifier = Modifier.height(20.dp)
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
                modifier = Modifier.height(40.dp)
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

                        pin.length != 6 -> {

                            error =
                                "Le PIN doit contenir 6 chiffres"
                        }

                        pin != confirmPin -> {

                            error =
                                "Les PIN ne correspondent pas"
                        }

                        else -> {

                            PinMemory.pinCode = pin

                            navController.navigate(
                                Routes.DASHBOARD
                            ) {

                                popUpTo(0)
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