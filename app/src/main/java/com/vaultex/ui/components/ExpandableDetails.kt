package com.vaultex.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultex.R

/**
 * Bloc « Voir détails » repliable.
 *
 * Sur un écran de confirmation, tout n'a pas la même valeur. L'utilisateur doit
 * décider en un coup d'œil sur trois choses : COMBIEN part, VERS QUI, et
 * COMBIEN AU TOTAL. Le reste — réseau, prix du gas, délai estimé — sert à
 * vérifier, pas à décider.
 *
 * Tout afficher force à faire défiler l'écran, et le défilement est
 * précisément l'ennemi ici : le bouton de confirmation sort du champ, et
 * l'information essentielle se noie dans le secondaire. On garde donc
 * l'essentiel visible sans geste, et on range le reste derrière ce bloc.
 *
 * Volontairement calqué sur le repliage éprouvé de l'écran d'aide : mêmes
 * primitives (animateContentSize + AnimatedVisibility + rotate), aucune API
 * exotique.
 *
 * @param summary résumé affiché sur la ligne repliée, quand il reste une
 *   information secondaire trop importante pour être cachée (les frais).
 */
@Composable
fun ExpandableDetails(
    accent: Color,
    labelColor: Color,
    summary: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth().animateContentSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = summary.orEmpty(),
                fontSize = 12.sp,
                color = labelColor,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(if (expanded) R.string.details_hide else R.string.details_show),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp).rotate(if (expanded) 180f else 0f)
            )
        }
        AnimatedVisibility(expanded) {
            Column(content = content)
        }
    }
}
