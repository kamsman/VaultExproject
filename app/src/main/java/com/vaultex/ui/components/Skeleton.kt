package com.vaultex.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.vaultex.ui.theme.BgTertiary
import com.vaultex.ui.theme.Surface
import com.vaultex.ui.theme.SurfaceLight

/**
 * Skeleton / shimmer placeholders (#1 « écrans blancs »).
 *
 * Affichés pendant le premier chargement, à la place d'un écran vide ou
 * d'un simple spinner, pour donner une perception de rapidité et la forme
 * du contenu à venir. Les couleurs suivent le thème actif (clair/sombre).
 */
@Composable
private fun shimmerBrush(): Brush {
    val base = SurfaceLight
    val highlight = BgTertiary
    val transition = rememberInfiniteTransition(label = "skeleton")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton_phase"
    )
    // Bande lumineuse qui glisse horizontalement sur la surface grisée.
    val x = 700f * phase
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(x - 300f, 0f),
        end = Offset(x + 300f, 0f)
    )
}

@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    Box(modifier = modifier.clip(shape).background(shimmerBrush()))
}

/** Ligne générique : pastille ronde + deux traits + valeur à droite. */
@Composable
fun ListItemSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SkeletonBox(Modifier.size(40.dp), shape = CircleShape)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SkeletonBox(Modifier.fillMaxWidth(0.45f).height(14.dp))
            SkeletonBox(Modifier.fillMaxWidth(0.30f).height(11.dp))
        }
        Spacer(Modifier.width(12.dp))
        SkeletonBox(Modifier.width(60.dp).height(14.dp))
    }
}

/** Skeleton de la liste « Répartition » du Portfolio. */
@Composable
fun PortfolioListSkeleton(rows: Int = 4, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        repeat(rows) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ListItemSkeleton()
                SkeletonBox(Modifier.fillMaxWidth().height(6.dp), shape = RoundedCornerShape(3.dp))
            }
        }
    }
}

/** Skeleton d'une liste de transactions (Historique). */
@Composable
fun HistoryListSkeleton(rows: Int = 6, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(rows) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Surface)
                    .padding(14.dp)
            ) {
                ListItemSkeleton()
            }
        }
    }
}

/** Skeleton de l'écran Détail d'un token (en-tête, graphique, solde). */
@Composable
fun TokenDetailSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // En-tête prix
        SkeletonCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SkeletonBox(Modifier.size(44.dp), shape = CircleShape)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonBox(Modifier.width(80.dp).height(16.dp))
                    SkeletonBox(Modifier.width(120.dp).height(12.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            SkeletonBox(Modifier.width(160.dp).height(28.dp))
            Spacer(Modifier.height(8.dp))
            SkeletonBox(Modifier.width(90.dp).height(14.dp))
        }
        // Graphique
        SkeletonCard {
            SkeletonBox(Modifier.width(120.dp).height(14.dp))
            Spacer(Modifier.height(12.dp))
            SkeletonBox(Modifier.fillMaxWidth().height(100.dp), shape = RoundedCornerShape(12.dp))
        }
        // Solde
        SkeletonCard {
            SkeletonBox(Modifier.width(140.dp).height(14.dp))
            Spacer(Modifier.height(10.dp))
            SkeletonBox(Modifier.fillMaxWidth(0.7f).height(12.dp))
        }
    }
}

@Composable
private fun SkeletonCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .padding(20.dp),
        content = content
    )
}
