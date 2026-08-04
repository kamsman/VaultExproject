package com.vaultex.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultex.core.session.ToastController
import com.vaultex.core.session.ToastData
import com.vaultex.ui.theme.Surface as SurfaceColor
import com.vaultex.ui.theme.TextPrimary
import kotlinx.coroutines.delay

/**
 * Hôte des toasts maison : à placer une fois à la racine de l'app (au-dessus du
 * NavHost). Écoute [ToastController] et affiche une carte animée (logo + texte)
 * en haut de l'écran, qui disparaît toute seule après ~2,8 s.
 */
@Composable
fun ToastHost(controller: ToastController, modifier: Modifier = Modifier) {
    var current by remember { mutableStateOf<ToastData?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        controller.events.collect { data ->
            current = data
            visible = true
            delay(2800)
            visible = false
            delay(300) // laisse l'animation de sortie se terminer
        }
    }

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut()
        ) {
            val data = current
            if (data != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SurfaceColor,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .padding(top = 48.dp, start = 24.dp, end = 24.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(Color(0x1422C55E)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (data.symbol != null) {
                                coil.compose.AsyncImage(
                                    model = CryptoIcon.url(data.symbol),
                                    contentDescription = data.symbol,
                                    modifier = Modifier.size(34.dp).clip(CircleShape)
                                )
                            } else {
                                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF22C55E), modifier = Modifier.size(22.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(data.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }
                }
            }
        }
    }
}
