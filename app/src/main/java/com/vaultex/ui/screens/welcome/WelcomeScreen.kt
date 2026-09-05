package com.vaultex.ui.screens.welcome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

import com.vaultex.R
import com.vaultex.ui.components.CryptoIcon
import com.vaultex.ui.navigation.Routes

/* ─── Palette de l'écran de marque (toujours sombre, quel que soit le thème :
   c'est une vitrine, elle doit être identique pour tous). ─────────────── */
private val BrandBgTop = Color(0xFF070B1C)
private val BrandBgBottom = Color(0xFF0C1330)
private val BrandCard = Color(0xFF101A38)
private val BrandBorder = Color(0xFF1E2A4E)
private val BrandBlue = Color(0xFF3B82F6)
private val BrandBlueLight = Color(0xFF60A5FA)
private val BrandTextDim = Color(0xFF8A95B4)

/** Cryptos mises en avant (logos réels chargés via Coil). */
private val SHOWCASE = listOf("BTC", "ETH", "BNB", "TRX", "SOL")

@Composable
fun WelcomeScreen(
    navController: NavHostController
) {
    var termsAccepted by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BrandBgTop, BrandBgBottom)))
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))

        // ─── Diamant dégradé (logo emblème) ───
        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(54.dp)
                    .rotate(45f)
                    .background(
                        Brush.linearGradient(listOf(BrandBlueLight, BrandBlue)),
                        RoundedCornerShape(8.dp)
                    )
            )
        }

        Spacer(Modifier.height(18.dp))

        com.vaultex.ui.components.VaultexWordmark(height = 36.dp)

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.welcome_tagline),
            color = BrandTextDim,
            fontSize = 14.sp
        )

        Spacer(Modifier.height(28.dp))

        // ─── Carte des 5 blockchains supportées (logos réels) ───
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(BrandCard)
                .border(1.dp, BrandBorder, RoundedCornerShape(18.dp))
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SHOWCASE.forEach { symbol ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    coil.compose.AsyncImage(
                        model = CryptoIcon.url(symbol),
                        contentDescription = symbol,
                        modifier = Modifier.size(34.dp).clip(CircleShape)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(symbol, color = BrandTextDim, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(Modifier.height(30.dp))

        // ─── Accroche : mot clé en bleu, comme la maquette ───
        Text(
            text = stringResource(R.string.welcome_headline_pre),
            color = Color.White,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Row(horizontalArrangement = Arrangement.Center) {
            Text(
                text = stringResource(R.string.welcome_headline_accent) + " ",
                color = BrandBlueLight,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.welcome_headline_post),
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.welcome_subtitle_pitch),
            color = BrandTextDim,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        // ─── Acceptation des CGU : obligatoire avant tout accès au wallet.
        // Les deux boutons restent inertes tant que la case n'est pas cochée. ───
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = termsAccepted,
                onCheckedChange = { termsAccepted = it },
                colors = CheckboxDefaults.colors(
                    checkedColor = BrandBlue,
                    uncheckedColor = BrandTextDim,
                    checkmarkColor = Color.White
                )
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.welcome_terms_accept),
                    color = BrandTextDim,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.legal_terms_title),
                        color = BrandBlueLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { navController.navigate(Routes.legal("terms")) }
                    )
                    Text(
                        text = stringResource(R.string.legal_privacy_title),
                        color = BrandBlueLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { navController.navigate(Routes.legal("privacy")) }
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        BrandGradientButton(
            text = stringResource(R.string.welcome_create),
            enabled = termsAccepted
        ) { navController.navigate(Routes.MNEMONIC_DISPLAY) }

        Spacer(Modifier.height(12.dp))

        BrandOutlinedButton(
            text = stringResource(R.string.welcome_import),
            enabled = termsAccepted
        ) { navController.navigate(Routes.IMPORT_WALLET) }

        Spacer(Modifier.height(22.dp))

        // ─── Pied de page : réassurance ───
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.VerifiedUser, null,
                tint = BrandTextDim, modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.welcome_trust_footer),
                color = BrandTextDim,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(28.dp))
    }
}

/** Bouton principal à dégradé bleu (maquette). Grisé tant que désactivé. */
@Composable
private fun BrandGradientButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val brush = if (enabled) Brush.horizontalGradient(listOf(BrandBlue, Color(0xFF2563EB)))
    else Brush.horizontalGradient(listOf(Color(0xFF1E2A4E), Color(0xFF1E2A4E)))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(brush)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (enabled) Color.White else BrandTextDim,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Bouton secondaire bordé (maquette). */
@Composable
private fun BrandOutlinedButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val stroke = if (enabled) BrandBlue else Color(0xFF1E2A4E)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(BorderStroke(1.dp, stroke), RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (enabled) Color.White else BrandTextDim,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
