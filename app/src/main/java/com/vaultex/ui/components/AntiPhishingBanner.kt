package com.vaultex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultex.R
import com.vaultex.ui.theme.AccentGreen
import com.vaultex.ui.theme.TextMuted
import com.vaultex.ui.theme.TextSecondary

/**
 * Affiche le code anti-phishing de l'utilisateur.
 *
 * Placé aux deux moments où une contrefaçon frappe : l'écran de
 * déverrouillage (premier écran vu) et la saisie de la phrase de récupération
 * (le moment où tout se joue). Une fausse application, forcément installée à
 * neuf, ne peut pas connaître ce mot — son absence est le signal d'alarme.
 *
 * Si aucun code n'est défini, on affiche une invitation discrète à en créer un
 * plutôt que rien : un dispositif de sécurité que personne n'active ne protège
 * personne. [onConfigure] ouvre l'écran de réglage ; s'il est nul, l'invitation
 * n'est pas affichée (écrans où l'on ne peut pas naviguer).
 */
@Composable
fun AntiPhishingBanner(
    code: String,
    modifier: Modifier = Modifier,
    onConfigure: (() -> Unit)? = null
) {
    if (code.isNotBlank()) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(AccentGreen.copy(alpha = 0.10f))
                .border(1.dp, AccentGreen.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.VerifiedUser, null, tint = AccentGreen, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.antiphishing_banner_label),
                    fontSize = 10.sp,
                    color = TextSecondary
                )
                Text(
                    code,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = AccentGreen
                )
            }
        }
    } else if (onConfigure != null) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onConfigure() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.VerifiedUser, null, tint = TextMuted, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.antiphishing_banner_setup),
                fontSize = 11.sp,
                color = TextMuted
            )
        }
    } else {
        Spacer(Modifier.height(0.dp))
    }
}

/** Couleur d'accent réutilisée par l'écran de réglage. */
internal val AntiPhishingAccent: Color
    @Composable get() = AccentGreen
