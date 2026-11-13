package com.vaultex.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vaultex.R

/**
 * Logo de marque « VAULTEX » — image réelle (drawable/logo_tex.png), utilisée
 * partout où le texte « VaultEx » servait auparavant de logo (splash, accueil,
 * déverrouillage…). Dimensionné par HAUTEUR (ratio conservé automatiquement).
 *
 * Chargé via Coil (et non painterResource) : le décodage se fait HORS du
 * thread principal et l'image est réduite si besoin → pas de blocage du
 * démarrage même avec un gros PNG, et pas de crash si le fichier est illisible.
 */
@Composable
fun VaultexWordmark(
    height: Dp,
    modifier: Modifier = Modifier
) {
    coil.compose.AsyncImage(
        model = R.drawable.logo_tex,
        contentDescription = "VaultEx",
        contentScale = ContentScale.Fit,
        modifier = modifier.height(height)
    )
}

/*
═══════════════════════════════════════════════════════════════════════════
LE NOM QUI SE RÉVÈLE, LETTRE APRÈS LETTRE
═══════════════════════════════════════════════════════════════════════════

Un volet passe de gauche à droite et découvre le logo, pendant qu'il monte
de quelques points. À la vitesse où ça se joue, l'œil lit « les lettres
arrivent l'une après l'autre ».

─── POURQUOI PAS DE VRAIES LETTRES ANIMÉES ─────────────────────────────

La maquette animait chaque lettre séparément, ce qui demande des tracés
vectoriels — V, A, U, L, T, les trois barres du E, X — au lieu de l'image.

Ces tracés ont été comparés au vrai `logo_tex.png` : ils en sont très
proches, mais pas identiques. Les adopter mettrait DEUX versions du logo
dans l'application — celle du splash et celle de l'accueil, du
déverrouillage, de l'assistance — et personne ne saurait plus laquelle
fait foi.

Un volet sur la vraie image donne le même effet sans toucher à la marque.

─── SAUF LE E, QUI SE SÉPARE ───────────────────────────────────────────

Lui seul est dessiné à la main, parce que son mouvement ne se raconte pas
avec un volet : ses trois barres partent empilées au centre, écrasées, et
s'écartent vers leurs places.

Pour qu'il reste LE E DU LOGO et non une approximation, ses dimensions ont
été relevées dans le PNG lui-même, au pixel : voir [BarreE]. Et le relevé a
appris quelque chose que la maquette ignorait — la barre du milieu est plus
courte que les deux autres. La maquette leur donnait la même longueur.

─── LE VOLET NE FAIT PAS DE FONDU ──────────────────────────────────────

Un fondu ferait apparaître le mot entier en pâle, puis le durcirait : on
verrait « VAULTEX » dès le premier instant. Le volet le fait apparaître
pour de bon, de gauche à droite, ce qui est le mouvement voulu.
═══════════════════════════════════════════════════════════════════════════
*/

/*
LE E, RELEVÉ DANS `logo_tex.png` AU PIXEL PRÈS.

Tout est exprimé en fractions de l'image (1024 × 261) et non en pixels :
le composant est dimensionné par sa hauteur, et l'image est redessinée à
l'échelle. Des pixels en dur seraient faux à toute autre taille que celle
du relevé.

  barre haute   x 692 → 767   y 100 → 121
  barre milieu  x 692 → 751   y 143 → 164     ← plus courte, c'est un vrai E
  barre basse   x 692 → 767   y 186 → 207

Les lettres voisines ne sont pas collées au E : le T finit à x 676 et le X
commence à 784. Les bornes ci-dessous tombent donc dans du vide, et
découper l'image ici ne rogne aucune lettre.
*/
/**
 * Où commence le E dans le mot, en fraction de sa largeur.
 *
 * Exposé parce que le splash en a besoin pour déclencher la séparation à
 * l'instant exact où le volet arrive sur le E. Le calcul vit là-bas, la
 * mesure ici — là où elle a été relevée.
 */
const val FRACTION_HORIZONTALE_E = 692f / 1024f

private const val E_GAUCHE = 692f / 1024f
private const val E_DROITE = 767f / 1024f
private const val E_MILIEU_DROITE = 751f / 1024f

private const val E_EPAISSEUR = 21f / 261f
private const val E_ARRONDI = 4f / 261f

/** Centres verticaux finaux des trois barres, en fraction de la hauteur. */
private const val E_CENTRE_HAUT = 110.5f / 261f
private const val E_CENTRE_MILIEU = 153.5f / 261f
private const val E_CENTRE_BAS = 196.5f / 261f

/** Bleu relevé au centre d'une barre — celui du logo, pas celui du thème. */
private val E_BLEU = Color(0xFF1A7CF0)

/**
 * Une barre du E : ses bornes finales, et d'où elle part.
 *
 * [decalage] est la distance qui la sépare du centre du E au départ, en
 * fraction de la hauteur. Les trois barres commencent empilées là ; celle
 * du milieu a donc un décalage nul et ne fait que s'étirer.
 */
private data class BarreE(
    val gauche: Float,
    val droite: Float,
    val centre: Float,
    val decalage: Float
)

private val BARRES_E = listOf(
    BarreE(E_GAUCHE, E_DROITE, E_CENTRE_HAUT, E_CENTRE_MILIEU - E_CENTRE_HAUT),
    BarreE(E_GAUCHE, E_MILIEU_DROITE, E_CENTRE_MILIEU, 0f),
    BarreE(E_GAUCHE, E_DROITE, E_CENTRE_BAS, E_CENTRE_MILIEU - E_CENTRE_BAS)
)

/*
LES QUATRE TEMPS DE LA SÉPARATION, repris de la maquette.

  0 → 38 %   les trois barres, empilées au centre, s'étirent jusqu'à
             dépasser leur largeur de douze pour cent
  38 → 52 %  la largeur revient à sa valeur juste
  52 → 80 %  les barres s'écartent, et dépassent leur place de quinze
             pour cent de l'écart
  80 → 100 % elles reviennent se poser

Les deux dépassements sont ce qui fait la différence entre des barres qui
se placent et des barres qu'on place. Les retirer donnerait le même
mouvement, mort.
*/
private val E_ETIRE = CubicBezierEasing(0.2f, 0.9f, 0.25f, 1f)
private val E_POSE_LARGEUR = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
private val E_ECART = CubicBezierEasing(0.5f, 0f, 0.3f, 1f)
private val E_RETOUR = CubicBezierEasing(0f, 0f, 0.58f, 1f)

/** Largeur relative d'une barre à l'instant [p] de la séparation. */
private fun largeurBarreE(p: Float): Float = when {
    p < 0.38f -> E_ETIRE.transform(p / 0.38f) * 1.12f
    p < 0.52f -> 1.12f - 0.12f * E_POSE_LARGEUR.transform((p - 0.38f) / 0.14f)
    else -> 1f
}

/**
 * Part du décalage de départ encore appliquée à l'instant [p].
 *
 * Vaut 1 tant que les barres sont empilées, passe par -0,15 — le
 * dépassement — puis revient à 0, leur place.
 */
private fun ecartBarreE(p: Float): Float = when {
    p < 0.52f -> 1f
    p < 0.80f -> 1f - 1.15f * E_ECART.transform((p - 0.52f) / 0.28f)
    else -> -0.15f + 0.15f * E_RETOUR.transform((p - 0.80f) / 0.20f)
}

@Composable
fun VaultexWordmarkAnime(
    height: Dp,
    avancement: Float,
    avancementE: Float,
    modifier: Modifier = Modifier
) {
    val p = avancement.coerceIn(0f, 1f)
    val pE = avancementE.coerceIn(0f, 1f)

    if (p <= 0f) {
        // Place tenue dès le premier instant : sans ça, le nom pousserait le
        // reste de la colonne vers le bas en apparaissant.
        androidx.compose.foundation.layout.Spacer(modifier.height(height))
        return
    }

    VaultexWordmark(
        height = height,
        modifier = modifier
            .graphicsLayer {
                // Le mot finit de monter avant que le volet ne l'ait
                // entierement decouvert : il est pose quand on le lit.
                translationY = (1f - p) * 14.dp.toPx() * 1.4f
            }
            .drawWithContent {
                val l = size.width
                val h = size.height
                val volet = l * p

                /*
                L'IMAGE EST DESSINÉE EN DEUX MORCEAUX, le E retiré du
                milieu. Une seule découpe ne peut pas faire un trou : il
                faut la gauche, puis la droite.

                Chacune est en plus bornée par le volet, sinon le X
                apparaîtrait avant les lettres qui le précèdent.
                */
                clipRect(right = minOf(l * E_GAUCHE, volet)) {
                    this@drawWithContent.drawContent()
                }
                if (volet > l * E_DROITE) {
                    clipRect(left = l * E_DROITE, right = volet) {
                        this@drawWithContent.drawContent()
                    }
                }

                if (pE <= 0f) return@drawWithContent

                val largeur = largeurBarreE(pE)
                val ecart = ecartBarreE(pE)

                BARRES_E.forEach { barre ->
                    val demiL = (barre.droite - barre.gauche) * l / 2f
                    val centreX = (barre.gauche + barre.droite) / 2f * l
                    val centreY = (barre.centre + barre.decalage * ecart) * h
                    val demiH = E_EPAISSEUR * h / 2f

                    drawRoundRect(
                        color = E_BLEU,
                        topLeft = Offset(centreX - demiL * largeur, centreY - demiH),
                        size = Size(demiL * 2f * largeur, demiH * 2f),
                        cornerRadius = CornerRadius(E_ARRONDI * h)
                    )
                }
            }
    )
}

/** Bleu de la marque VAULTEX (repris du logo) — pour les endroits sans image. */
val VaultexBrandBlue = Color(0xFF1E7FF5)
