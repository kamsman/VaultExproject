package com.vaultex.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/*
═══════════════════════════════════════════════════════════════════════════
LOGO ANIMÉ — l'anneau se trace, le losange se pose dedans
═══════════════════════════════════════════════════════════════════════════

Repris d'une animation vidéo de dix secondes : des particules convergent en
losange, un arc fait le tour, puis le nom apparaît.

TROIS RAISONS DE LA REDESSINER PLUTÔT QUE DE JOUER LA VIDÉO :

· Dix secondes avant d'atteindre son portefeuille, c'est le genre de détail
  qui fait désinstaller.
· La vidéo est en 1280×720 paysage et se termine en blanc. Sur un téléphone
  en portrait, dans une application sombre, il aurait fallu des bandes noires
  puis un passage blanc → noir en pleine figure.
· Un fichier vidéo pèse 2,2 Mo, demande un décodeur et porte une piste audio.
  Un portefeuille qui fait du bruit au lancement, en public, est un problème.

─── CE QUI A CHANGÉ, ET POURQUOI L'ORDRE EST INVERSÉ ────────────────────

La version précédente posait le losange, PUIS fermait l'anneau autour. Le
commentaire d'alors défendait cet ordre : un cercle qui se referme sur une
forme dit « coffre » sans un mot.

C'est maintenant l'inverse — l'anneau se trace d'abord, et le losange vient
se poser dedans. L'histoire est aussi bonne et elle se lit mieux : un cadre
se dessine, puis la chose précieuse y est déposée. Surtout, les deux temps
SE CHEVAUCHENT, ce que l'ancienne version ne faisait pas. Trois phases
strictement l'une après l'autre donnent trois petits films ; des phases qui
empiètent donnent un seul mouvement.

─── UNE SEULE HORLOGE, ET DES PHASES QUI S'Y DÉCOUPENT ──────────────────

L'ancienne version enchaînait trois `animateTo`, ce qui interdisait tout
chevauchement par construction : la phase suivante ne pouvait commencer
qu'une fois la précédente finie.

Ici une seule progression court de 0 à [LOGO_ANIME_DUREE_MS], et chaque
phase y découpe sa fenêtre. Les faire se recouvrir ne demande plus qu'à
changer deux nombres.
═══════════════════════════════════════════════════════════════════════════
*/

/*
LES FENÊTRES DE CHAQUE PHASE, en millisecondes depuis le début.

Elles se recouvrent volontairement. Le losange démarre alors que l'anneau
n'a fait que la moitié du tour ; la brillance part avant que la pulsation
ne soit finie.

TOTAL : 2 450 ms, contre 3 050 avant. Le mouvement paraît pourtant plus
plein, parce qu'il n'y a plus de temps mort entre les phases — c'est le
chevauchement qui raccourcit, pas une accélération.
*/
private const val T_ANNEAU_DEBUT = 0
private const val T_ANNEAU_FIN = 1000

private const val T_LOSANGE_DEBUT = 550
private const val T_LOSANGE_FIN = 1450

private const val T_POSE_DEBUT = 1250
private const val T_POSE_FIN = 1750

private const val T_PULSE_DEBUT = 1250
private const val T_PULSE_FIN = 2250

private const val T_BRILLANCE_DEBUT = 1450
private const val T_BRILLANCE_FIN = 2450

/** Total de l'animation, utile à l'appelant pour caler sa navigation. */
const val LOGO_ANIME_DUREE_MS = T_BRILLANCE_FIN

/**
 * Le moment où le nom commence à se révéler.
 *
 * Exposé pour que le splash n'ait pas à redéclarer ce chiffre : le logo et
 * le nom sont un seul mouvement, et leur recouvrement est un réglage de
 * l'animation, pas de l'écran qui l'affiche.
 */
const val LOGO_ANIME_MOT_DEBUT_MS = 1550

/*
LES COURBES, reprises une par une de la maquette.

`EaseOutBack` de Compose ne convient pas au losange : son dépassement est
plus sec que celui voulu ici. La courbe est donc écrite telle quelle.
*/
private val COURBE_ANNEAU = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
private val COURBE_LOSANGE = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
private val COURBE_DOUCE = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
private val COURBE_SORTIE = CubicBezierEasing(0f, 0f, 0.58f, 1f)

/*
LES TROIS BLEUS DU DÉGRADÉ, du clair au profond.

Pas pris dans le thème : ce sont les couleurs de la marque, pas celles de
l'interface. Un thème clair qui éclaircirait l'accent délaverait le logo.
*/
private val BLEU_CLAIR = Color(0xFF9CCAFF)
private val BLEU = Color(0xFF1F7FF0)
private val BLEU_PROFOND = Color(0xFF1259C9)

/** Avancement de 0 à 1 d'une phase, à l'instant [ms]. */
private fun phase(ms: Float, debut: Int, fin: Int): Float =
    ((ms - debut) / (fin - debut)).coerceIn(0f, 1f)

/**
 * Logo VaultEx qui se construit sous les yeux.
 *
 * [onTermine] est appelé une fois la dernière phase jouée — l'appelant peut
 * enchaîner sans dupliquer les durées écrites ici.
 */
@Composable
fun LogoAnime(
    modifier: Modifier = Modifier,
    taille: Dp = 124.dp,
    onTermine: () -> Unit = {}
) {
    val horloge = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        horloge.animateTo(
            LOGO_ANIME_DUREE_MS.toFloat(),
            tween(LOGO_ANIME_DUREE_MS, easing = LinearEasing)
        )
        onTermine()
    }

    Box(modifier.size(taille), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(taille)) {
            val ms = horloge.value
            val centre = Offset(size.width / 2f, size.height / 2f)

            /*
            LES PROPORTIONS VIENNENT DE LA MAQUETTE, exprimées en fractions
            de la taille demandée plutôt qu'en points fixes : le même
            composant sert à 124 points sur le splash et pourrait servir
            ailleurs plus petit, sans que l'épaisseur du trait ne devienne
            grossière ou invisible.
            */
            val rayon = size.minDimension * 0.457f
            val epaisseur = size.minDimension * 0.027f
            val coteLosange = size.minDimension * 0.488f

            /*
            LA POSE : un léger gonflement au moment où le losange touche le
            fond, appliqué à TOUT l'icône. C'est ce qui donne le poids —
            sans lui, la forme arrive sans rien peser.
            */
            val pose = phase(ms, T_POSE_DEBUT, T_POSE_FIN)
            val facteurPose = 1f + 0.05f * sin((pose * Math.PI).toFloat())

            scale(facteurPose, facteurPose, centre) {
                dessinerAnneau(ms, centre, rayon, epaisseur)
                dessinerLosange(ms, centre, coteLosange)
                dessinerPulsation(ms, centre, rayon, epaisseur)
            }
        }
    }
}

/*
L'ANNEAU, dessiné comme un arc qui progresse.

Le départ à -90° le fait partir du haut : c'est là que l'œil se pose, et le
point de fermeture y revient, ce qui rend le tour complet lisible.
*/
private fun DrawScope.dessinerAnneau(
    ms: Float,
    centre: Offset,
    rayon: Float,
    epaisseur: Float
) {
    val p = COURBE_ANNEAU.transform(phase(ms, T_ANNEAU_DEBUT, T_ANNEAU_FIN))
    if (p <= 0f) return

    drawArc(
        color = BLEU,
        startAngle = -90f,
        sweepAngle = 360f * p,
        useCenter = false,
        topLeft = Offset(centre.x - rayon, centre.y - rayon),
        size = Size(rayon * 2, rayon * 2),
        style = Stroke(width = epaisseur, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    )

    // Tête lumineuse en bout d'arc : sans elle, l'anneau semble apparaître
    // plutôt que se tracer.
    if (p < 1f) {
        val angle = Math.toRadians((-90f + 360f * p).toDouble())
        drawCircle(
            color = Color.White,
            radius = epaisseur * 0.75f,
            center = Offset(
                centre.x + rayon * cos(angle).toFloat(),
                centre.y + rayon * sin(angle).toFloat()
            ),
            alpha = 0.9f
        )
    }
}

/*
LE LOSANGE — un carré aux coins arrondis, posé à 40°.

Il arrive en tournant de cent quarante degrés et en grandissant depuis
rien, avec un dépassement en fin de course. La rotation est ce qui fait la
différence entre « une image grossit » et « un objet se met en place ».

QUARANTE DEGRÉS ET NON QUARANTE-CINQ : à 45° la forme est parfaitement
symétrique et devient un simple carré sur la pointe. Les cinq degrés
d'écart la rendent délibérée.

LA BRILLANCE EST DESSINÉE ICI, découpée à la forme du losange : c'est le
même objet, et la séparer aurait demandé de recalculer son contour.
*/
private fun DrawScope.dessinerLosange(ms: Float, centre: Offset, cote: Float) {
    val p = COURBE_LOSANGE.transform(phase(ms, T_LOSANGE_DEBUT, T_LOSANGE_FIN))
    if (p <= 0f) return

    // L'opacité monte plus vite que la forme : la maquette la veut pleine au
    // tiers du mouvement, pour qu'on voie tourner un objet et non un fantôme.
    val opacite = (p * 3f).coerceIn(0f, 1f)
    val angle = 40f - 140f * (1f - p)

    rotate(degrees = angle, pivot = centre) {
        scale(p, p, centre) {
            val demi = cote / 2f
            val cadre = Rect(
                left = centre.x - demi,
                top = centre.y - demi,
                right = centre.x + demi,
                bottom = centre.y + demi
            )
            val rayonCoin = cote * 0.12f

            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(BLEU_CLAIR, BLEU, BLEU_PROFOND),
                    start = Offset(cadre.right, cadre.top),
                    end = Offset(cadre.left, cadre.bottom)
                ),
                topLeft = Offset(cadre.left, cadre.top),
                size = Size(cote, cote),
                cornerRadius = CornerRadius(rayonCoin),
                alpha = opacite
            )

            dessinerBrillance(ms, cadre, rayonCoin)
        }
    }
}

/*
LA BRILLANCE — une bande claire qui traverse le losange.

C'est le seul moment où l'animation suggère une matière : une surface qui
accroche la lumière est une surface dure. Sur un portefeuille, ça ne dit pas
rien.

ELLE EST DÉCOUPÉE À LA FORME, sinon la bande déborderait en diagonale sur
le fond et le losange perdrait ses arêtes.

LA BANDE EST UN PARALLÉLOGRAMME construit point par point, et non un
rectangle incliné. Compose n'a pas de transformation de cisaillement ; la
dessiner penchée revient au même et évite une matrice écrite à la main.
*/
private fun DrawScope.dessinerBrillance(ms: Float, cadre: Rect, rayonCoin: Float) {
    val p = COURBE_DOUCE.transform(phase(ms, T_BRILLANCE_DEBUT, T_BRILLANCE_FIN))
    if (p <= 0f || p >= 1f) return

    val cote = cadre.width
    val largeurBande = cote * 0.24f
    val penche = cote * 0.36f

    // De bien avant le bord gauche à bien après le bord droit : la bande doit
    // être entièrement sortie aux deux extrémités du mouvement.
    val x = cadre.left - cote * 0.8f + p * (cote * 2.6f)

    val bande = Path().apply {
        moveTo(x, cadre.bottom)
        lineTo(x + penche, cadre.top)
        lineTo(x + penche + largeurBande, cadre.top)
        lineTo(x + largeurBande, cadre.bottom)
        close()
    }

    val forme = Path().apply {
        addRoundRect(RoundRect(cadre, CornerRadius(rayonCoin)))
    }

    clipPath(forme) {
        drawPath(
            path = bande,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0f),
                    Color.White.copy(alpha = 0.55f),
                    Color.White.copy(alpha = 0f)
                ),
                start = Offset(x, 0f),
                end = Offset(x + penche + largeurBande, 0f)
            )
        )
    }
}

/*
LA PULSATION — un anneau qui s'ouvre et s'efface.

Elle part au moment où le losange se pose, et elle seule dit que quelque
chose vient de se terminer. Sans elle l'animation s'arrête ; elle ne se
termine pas.
*/
private fun DrawScope.dessinerPulsation(
    ms: Float,
    centre: Offset,
    rayon: Float,
    epaisseur: Float
) {
    val p = COURBE_SORTIE.transform(phase(ms, T_PULSE_DEBUT, T_PULSE_FIN))
    if (p <= 0f || p >= 1f) return

    drawCircle(
        color = BLEU,
        radius = rayon * (1f + 0.28f * p),
        center = centre,
        alpha = 0.55f * (1f - p),
        style = Stroke(width = epaisseur * 0.43f)
    )
}
