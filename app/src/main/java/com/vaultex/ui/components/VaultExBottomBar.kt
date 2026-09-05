package com.vaultex.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.vaultex.R
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.Surface as SurfaceColor
import com.vaultex.ui.theme.TextMuted

/* Dégradé bleu → violet (accent fixe, comme le prototype). */
private val NavBlue = Color(0xFF3B82F6)
private val NavPurple = Color(0xFF7C5CFC)
private val NavGradient = Brush.linearGradient(listOf(NavBlue, NavPurple))

/** Hauteur propre de la barre : sa pilule, son écart au bord, sa marge. */
private val HauteurBarre = 104.dp

/**
 * Espace à réserver EN BAS du contenu défilant des écrans qui affichent la
 * barre. La barre étant FLOTTANTE (posée par-dessus le contenu), sans cette
 * marge le dernier élément d'une liste passerait définitivement dessous.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * POURQUOI CETTE VALEUR EST CALCULÉE ET NON ÉCRITE EN DUR
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * C'était une constante : 104 dp. Elle a cessé d'être juste au passage à
 * Android 16.
 *
 * L'arithmétique le dit sans ambiguïté. La barre occupe, depuis le bas de
 * l'écran : la marge de barre système, puis FloatGap, puis ses 86 dp de
 * pilule. Avant l'edge-to-edge, la fenêtre EXCLUAIT les barres système : cette
 * première marge valait zéro, et 104 dp couvraient largement les 100 dp
 * réellement occupés.
 *
 * Depuis qu'Android impose le contenu SOUS les barres système, cette marge
 * vaut 24 à 48 dp selon l'appareil et le mode de navigation. La barre occupe
 * donc 124 à 148 dp, pour 104 réservés : le bas de chaque liste, et la carte
 * des nouveautés, passaient dessous. Constaté à l'écran.
 *
 * La valeur suit désormais l'appareil. Six écrans l'utilisent — accueil,
 * marché, réglages, carnet d'adresses, swap, tableau de bord — et se
 * corrigent tous par ce seul changement, sans qu'aucun n'ait à s'en occuper.
 */
val BottomBarSpace: Dp
    @Composable get() = HauteurBarre +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/** Marge entre le bas de l'écran (barres système) et la barre flottante. */
private val FloatGap = 14.dp

/**
 * Barre de navigation FLOTTANTE (façon Trust Wallet) : pilule arrondie posée
 * par-dessus le contenu, détachée des bords, avec ombre portée et bordure
 * dégradée. 5 onglets — Accueil · Marché · [Swap au centre, gros bouton rond]
 * · Historique · Paramètres. Le contenu de l'écran défile DERRIÈRE elle.
 * Suit le thème actuel (fond = surface du thème) ; le violet/bleu reste fixe.
 */
@Composable
fun VaultExBottomBar(navController: NavHostController, modifier: Modifier = Modifier) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    fun go(route: String) {
        navController.navigate(route) {
            popUpTo(Routes.DASHBOARD) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = FloatGap)
            .height(86.dp)
    ) {
        // ─── Pilule flottante (ombre + fond + bordure dégradée) ───
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(64.dp)
                // Ombre portée : c'est elle qui « décolle » la barre du contenu.
                .shadow(14.dp, RoundedCornerShape(28.dp), clip = false)
                .clip(RoundedCornerShape(28.dp))
                .background(SurfaceColor)
                .border(1.dp, NavGradient, RoundedCornerShape(28.dp))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavTabImage(R.drawable.logorond, stringResource(R.string.tab_home), currentRoute == Routes.DASHBOARD, Modifier.weight(1f)) { go(Routes.DASHBOARD) }
            NavTab(Icons.Default.TrendingUp, stringResource(R.string.tab_market), currentRoute == Routes.MARKET, Modifier.weight(1f)) { go(Routes.MARKET) }
            // Emplacement central : uniquement le libellé (le bouton flotte au-dessus).
            Box(Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) {
                Text(
                    stringResource(R.string.tab_swap),
                    fontSize = 11.sp,
                    fontWeight = if (currentRoute == Routes.SWAP) FontWeight.Bold else FontWeight.Normal,
                    color = if (currentRoute == Routes.SWAP) NavBlue else TextMuted,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            NavTab(Icons.Default.History, stringResource(R.string.tab_history), currentRoute == Routes.HISTORY, Modifier.weight(1f)) { go(Routes.HISTORY) }
            NavTab(Icons.Default.Settings, stringResource(R.string.tab_settings), currentRoute == Routes.SETTINGS, Modifier.weight(1f)) { go(Routes.SETTINGS) }
        }

        // ─── Bouton Swap central, surélevé + anneau pointillé ───
        Box(
            Modifier.align(Alignment.TopCenter).size(64.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(64.dp)) {
                drawCircle(
                    color = NavPurple.copy(alpha = 0.7f),
                    radius = size.minDimension / 2f - 1.dp.toPx(),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 9f), 0f))
                )
            }
            Box(
                Modifier
                    .size(52.dp)
                    .shadow(10.dp, CircleShape, clip = false)
                    .clip(CircleShape)
                    .background(NavGradient)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { go(Routes.SWAP) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Repeat, contentDescription = stringResource(R.string.tab_swap), tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
    }
}

@Composable
private fun NavTab(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = if (selected) NavBlue else TextMuted
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = color)
        // Indicateur de sélection (petit trait violet).
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier.width(16.dp).height(3.dp).clip(RoundedCornerShape(2.dp))
                .background(if (selected) NavPurple else Color.Transparent)
        )
    }
}

/** Onglet avec une IMAGE ronde (ex. logorond.png pour Accueil) au lieu d'une
 *  icône vectorielle. Le logo garde ses couleurs (pas de teinte) ; l'état
 *  sélectionné se lit sur le libellé + le trait violet, comme les autres. */
@Composable
private fun NavTabImage(imageRes: Int, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = if (selected) NavBlue else TextMuted
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Coil (décodage hors thread principal, downsampling, tolérant aux
        // fichiers illisibles) — évite tout blocage/crash au démarrage.
        coil.compose.AsyncImage(
            model = imageRes,
            contentDescription = label,
            modifier = Modifier.size(24.dp).clip(CircleShape)
        )
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = color)
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier.width(16.dp).height(3.dp).clip(RoundedCornerShape(2.dp))
                .background(if (selected) NavPurple else Color.Transparent)
        )
    }
}
