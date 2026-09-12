package com.vaultex.ui.screens.recovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.vaultex.R
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.AccentOrange
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.BorderColor
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary

/*
═══════════════════════════════════════════════════════════════════════════
« CE TÉLÉPHONE NE PEUT PLUS LIRE VOTRE CLÉ »
═══════════════════════════════════════════════════════════════════════════

Cet écran n'apparaît que dans un cas, rare et grave : le seed est bien
enregistré sur l'appareil, mais le Keystore Android ne peut plus le
déchiffrer. Vu sur un appareil réel, remonté par le bot d'administration.

Sans lui, l'application se comportait comme si tout allait bien : écran de
code, code accepté, puis des soldes à zéro, des adresses vides et des envois
refusés. La personne en concluait que ses fonds s'étaient volatilisés.

CE QUE CET ÉCRAN DOIT FAIRE, DANS CET ORDRE :

1. RASSURER D'ABORD, EXPLIQUER ENSUITE. La première ligne lue doit être
   « vos fonds sont intacts ». Tout le reste peut attendre la seconde.

2. NE PAS PROPOSER DE CRÉER UN PORTEFEUILLE. C'est le réflexe naturel quand
   une application semble vide, et c'est le geste qui condamne l'accès : un
   nouveau portefeuille n'a rien à voir avec l'ancien. Le seul chemin offert
   est la restauration par la phrase.

3. NE PAS ACCUSER L'UTILISATEUR. Il n'a rien fait de mal. La clé maîtresse
   vit hors de l'application, dans le coffre du système ; c'est lui qui l'a
   perdue.
*/
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeedIllisibleScreen(navController: NavHostController) {
    Scaffold(containerColor = BgPrimary) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgPrimary)
                .systemBarsPadding()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            Box(
                Modifier.size(72.dp).clip(CircleShape).background(AccentOrange.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Key, null, tint = AccentOrange, modifier = Modifier.size(34.dp))
            }

            Spacer(Modifier.height(24.dp))

            // Ce que la personne doit lire EN PREMIER.
            Text(
                stringResource(R.string.seed_unreadable_title),
                fontSize = 21.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 28.sp
            )

            Spacer(Modifier.height(14.dp))

            Text(
                stringResource(R.string.seed_unreadable_body),
                fontSize = 14.sp, color = TextSecondary, lineHeight = 21.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(Modifier.height(22.dp))

            // Le « pourquoi », en retrait : utile, mais pas urgent.
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.seed_unreadable_why_title),
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.seed_unreadable_why_body),
                        fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // Le SEUL chemin offert.
            Button(
                onClick = {
                    navController.navigate(Routes.IMPORT_WALLET) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = Color.White)
            ) {
                Text(
                    stringResource(R.string.seed_unreadable_restore),
                    fontWeight = FontWeight.Bold, fontSize = 16.sp
                )
            }

            Spacer(Modifier.height(14.dp))

            /*
            Pas de bouton « créer un portefeuille » ici, et c'est délibéré.

            Ce serait le geste le plus facile pour quelqu'un qui veut juste
            que l'application remarche — et le seul qui rende la situation
            irréversible. Qui n'a pas sa phrase doit d'abord la chercher, pas
            repartir de zéro ; l'avertissement ci-dessous le dit sans détour.
            */
            Text(
                stringResource(R.string.seed_unreadable_no_phrase),
                fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}
