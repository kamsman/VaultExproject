package com.vaultex.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

import com.vaultex.ui.components.GlassCard
import com.vaultex.ui.components.PrimaryButton
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.*
import com.vaultex.ui.utils.WalletMemory

import org.web3j.crypto.MnemonicUtils
import java.security.SecureRandom

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MnemonicDisplayScreen(
    navController: NavHostController
) {

    /*
    =========================
    GENERATE REAL MNEMONIC
    =========================
     */

    val mnemonic = remember {

        val entropy = ByteArray(16)

        SecureRandom().nextBytes(entropy)

        MnemonicUtils.generateMnemonic(entropy)
            .split(" ")
    }

    /*
    =========================
    SAVE FOR VERIFY SCREEN
    =========================
     */

    WalletMemory.mnemonicWords = mnemonic

    /*
    =========================
    UI STATE
    =========================
     */

    var revealed by remember {
        mutableStateOf(false)
    }

    Scaffold(
        topBar = {

            TopAppBar(
                title = {

                    Text(
                        "Phrase de récupération",
                        color = TextPrimary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BgPrimary
                )
            )
        },
        containerColor = BgPrimary
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {

            /*
            =========================
            WARNING
            =========================
             */

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = AccentRed.copy(alpha = 0.15f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {

                Text(
                    text =
                        "⚠️ Notez ces 12 mots dans l'ordre exact. " +
                                "Si vous les perdez, vous perdez votre wallet.",

                    color = AccentRed,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(14.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            /*
            =========================
            MNEMONIC GRID
            =========================
             */

            GlassCard {

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),

                    verticalArrangement = Arrangement.spacedBy(10.dp),

                    horizontalArrangement = Arrangement.spacedBy(10.dp),

                    modifier = Modifier.height(280.dp)
                ) {

                    itemsIndexed(mnemonic) { i, word ->

                        Row(
                            modifier = Modifier
                                .background(
                                    BgSecondary,
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(
                                    horizontal = 10.dp,
                                    vertical = 12.dp
                                ),

                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Text(
                                text = "${i + 1}.",
                                color = TextMuted,
                                fontSize = 11.sp
                            )

                            Spacer(
                                modifier = Modifier.width(6.dp)
                            )

                            Text(
                                text =
                                    if (revealed)
                                        word
                                    else
                                        "••••••",

                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                /*
                =========================
                SHOW / HIDE
                =========================
                 */

                TextButton(
                    onClick = {
                        revealed = !revealed
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Icon(
                        imageVector =
                            if (revealed)
                                Icons.Default.VisibilityOff
                            else
                                Icons.Default.Visibility,

                        contentDescription = null,
                        tint = AccentGold
                    )

                    Spacer(
                        modifier = Modifier.width(6.dp)
                    )

                    Text(
                        text =
                            if (revealed)
                                "Masquer"
                            else
                                "Afficher les mots",

                        color = AccentGold
                    )
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

                    navController.navigate(
                        Routes.MNEMONIC_VERIFY
                    )
                }
            )
        }
    }
}