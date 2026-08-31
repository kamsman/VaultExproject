package com.vaultex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultex.R
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.BorderColor
import com.vaultex.ui.theme.Surface as SurfaceColor
import com.vaultex.ui.theme.TextMuted
import com.vaultex.ui.theme.TextPrimary

/*
═══════════════════════════════════════════════════════════════════════════
RECHERCHE REPLIABLE — un seul modèle pour toute l'application
═══════════════════════════════════════════════════════════════════════════

Sept écrans proposaient une recherche, et chacun l'avait redessinée à sa
façon : hauteurs, rayons d'angle, couleurs de fond et tailles d'icône
différentes d'un écran à l'autre. Toutes occupaient en outre une barre
pleine largeur en permanence, pour une fonction dont on ne se sert que par
moments — de la place prise à la liste qu'on est venu consulter.

Le modèle retenu est celui du Marché : un bouton rond dans l'en-tête, qui
se déploie en champ de saisie à la place du titre quand on le touche. La
place n'est prise qu'au moment où elle sert, et le geste est le même
partout.

Regrouper ici évite surtout que le prochain ajustement doive être refait
sept fois — c'est exactement cette dispersion qui avait produit sept
apparences différentes.
*/

/**
 * État d'une recherche repliable : ouverte ou non, et son texte.
 *
 * Réunis dans un même objet parce qu'ils vont toujours ensemble : fermer
 * doit vider, sans quoi une recherche oubliée continuerait de filtrer une
 * liste alors que le champ n'est plus à l'écran — un défaut sans cause
 * visible pour qui le subit.
 */
@Stable
class EtatRecherche {
    var ouverte by mutableStateOf(false)
        private set

    var texte by mutableStateOf("")
        private set

    internal val focus = FocusRequester()

    fun ouvrir() { ouverte = true }

    fun saisir(valeur: String) { texte = valeur }

    /** Referme ET vide : voir la remarque ci-dessus. */
    fun fermer() {
        texte = ""
        ouverte = false
    }
}

@Composable
fun rememberEtatRecherche(): EtatRecherche = remember { EtatRecherche() }

/**
 * Bouton rond de l'en-tête.
 *
 * Une icône nue se perd sur un fond sombre : rien ne dit qu'elle se touche.
 * Le disque lui donne une cible visible et une surface de frappe confortable.
 */
@Composable
fun BoutonRecherche(
    etat: EtatRecherche,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(SurfaceColor)
            .clickable { etat.ouvrir() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Search,
            stringResource(R.string.search),
            tint = TextPrimary,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Champ déployé, à placer là où était le titre.
 *
 * [onTexteChange] est appelé en plus de la mise à jour de l'état : les
 * écrans qui filtrent en local n'en ont pas besoin, celui du Marché s'en
 * sert pour lancer la recherche réseau.
 *
 * [enChargement] remplace la croix par une roue le temps d'un appel — sans
 * quoi une recherche réseau lente passerait pour une recherche sans
 * résultat.
 */
@Composable
fun ChampRecherche(
    etat: EtatRecherche,
    placeholder: String,
    modifier: Modifier = Modifier,
    enChargement: Boolean = false,
    onTexteChange: (String) -> Unit = {}
) {
    /*
    Ouvrir sans donner le focus obligerait à toucher le champ une seconde
    fois avant de pouvoir taper.

    Mais requestFocus() lève une exception tant que le nœud de focus n'est
    pas rattaché, et l'effet peut partir avant que le champ ne soit posé.
    D'où l'attente d'une image, puis le garde-fou : le confort de la saisie
    ne vaut pas le risque de faire planter l'écran au premier appui sur la
    loupe. Au pire, l'utilisateur touche le champ lui-même.
    */
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { etat.focus.requestFocus() }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, null, tint = TextMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (etat.texte.isEmpty()) {
                    Text(placeholder, color = TextMuted, fontSize = 14.sp)
                }
                BasicTextField(
                    value = etat.texte,
                    onValueChange = {
                        etat.saisir(it)
                        onTexteChange(it)
                    },
                    singleLine = true,
                    textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(AccentBlue),
                    modifier = Modifier.fillMaxWidth().focusRequester(etat.focus)
                )
            }
            if (enChargement) {
                CircularProgressIndicator(
                    Modifier.padding(horizontal = 10.dp).size(16.dp),
                    color = AccentBlue,
                    strokeWidth = 2.dp
                )
            } else {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable {
                            etat.fermer()
                            onTexteChange("")
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close,
                        stringResource(R.string.close),
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
