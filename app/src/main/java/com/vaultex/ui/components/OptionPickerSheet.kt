package com.vaultex.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultex.R
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.Surface as SurfaceColor
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary

/** Accent violet des sélecteurs (identique au Swap). */
private val PickerPurple = Color(0xFF7C5CFC)

/** Une option d'un sélecteur : valeur + libellé + sous-libellé/icône facultatifs. */
data class PickerOption<T>(
    val value: T,
    val label: String,
    val sublabel: String? = null,
    val icon: ImageVector? = null,
    val emoji: String? = null
)

/**
 * Sélecteur en bottom sheet (style prototype) : titre + sous-titre, options en
 * cartes (icône ronde violette ou emoji, sélection = bordure violette + coche),
 * boutons Annuler / Confirmer. Suit le thème actuel (clair ou sombre) et les
 * tailles de texte de l'app. Le choix n'est appliqué qu'au « Confirmer ».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> OptionPickerSheet(
    title: String,
    subtitle: String?,
    options: List<PickerOption<T>>,
    selected: T,
    onConfirm: (T) -> Unit,
    onDismiss: () -> Unit
) {
    var pending by remember { mutableStateOf(selected) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BgPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            subtitle?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, fontSize = 13.sp, color = TextSecondary)
            }
            Spacer(Modifier.height(14.dp))

            options.forEach { opt ->
                val isSel = opt.value == pending
                Surface(
                    onClick = { pending = opt.value },
                    shape = RoundedCornerShape(14.dp),
                    color = SurfaceColor,
                    border = BorderStroke(
                        if (isSel) 1.5.dp else 0.dp,
                        if (isSel) PickerPurple else Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Icône ronde (violette) ou emoji (drapeau…)
                        Box(
                            Modifier.size(34.dp).clip(CircleShape)
                                .background(PickerPurple.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                opt.emoji != null -> Text(opt.emoji, fontSize = 18.sp)
                                opt.icon != null -> Icon(opt.icon, null, tint = PickerPurple, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(opt.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            opt.sublabel?.let { Text(it, fontSize = 12.sp, color = TextSecondary) }
                        }
                        // Sélection : coche violette ; sinon cercle vide
                        if (isSel) {
                            Box(
                                Modifier.size(22.dp).clip(CircleShape).background(PickerPurple),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        } else {
                            Surface(
                                shape = CircleShape, color = Color.Transparent,
                                border = BorderStroke(1.5.dp, TextSecondary.copy(alpha = 0.5f)),
                                modifier = Modifier.size(20.dp)
                            ) {}
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, PickerPurple),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PickerPurple)
                ) { Text(stringResource(R.string.cancel), fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
                Button(
                    onClick = { onConfirm(pending) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PickerPurple, contentColor = Color.White)
                ) { Text(stringResource(R.string.confirm), fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            }
        }
    }
}
