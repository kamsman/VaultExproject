package com.vaultex.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.TextPrimary

/**
 * Le motif du scanner — damier clair, équerres dans l'accent.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * DEUX COULEURS DANS UNE ICÔNE, CE QU'UN `tint` NE SAIT PAS FAIRE
 * ═══════════════════════════════════════════════════════════════════════
 *
 * `Icons.Default.QrCodeScanner` porte déjà ses équerres, mais c'est UN
 * seul tracé : il prend une couleur et une seule. Le motif et les
 * équerres sortent donc du même bleu, et le viseur se confond avec le
 * code qu'il encadre.
 *
 * On sépare les deux : le motif reste une icône Material — net à toutes
 * les densités, aucun fichier ajouté — et les quatre équerres sont
 * TRACÉES par-dessus, dans l'accent. C'est le viseur d'un appareil photo,
 * et c'est ce que le geste veut dire.
 *
 * ─── POURQUOI DES TRAITS PLUTÔT QU'UNE SECONDE ICÔNE ──────────────────
 *
 * Aucune icône Material ne porte un cadre d'angles seul. En superposer
 * deux obligerait à les aligner au pixel près, et l'alignement dépendrait
 * de leurs marges internes respectives — invisibles, et différentes d'une
 * version de la bibliothèque à l'autre.
 *
 * Quatre traits se placent par rapport au carré qui les contient. Rien à
 * deviner, rien à réajuster quand la taille change.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * POURQUOI CE FICHIER EXISTE
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Ce dessin est né dans `DashboardScreen`, en privé. Six écrans ouvrent
 * le scanner — le tableau de bord, le gestionnaire de jetons, l'envoi Pi
 * et les trois écrans d'envoi — et les cinq autres montraient encore
 * l'icône d'origine, d'une seule couleur.
 *
 * Un même geste doit porter le même signe partout, sinon c'est le signe
 * qui perd son sens. Recopier le tracé cinq fois l'aurait fait diverger
 * au premier ajustement ; il vit donc ici, et les six écrans l'appellent.
 *
 * ─── LES PROPORTIONS SONT DES FRACTIONS, PAS DES PIXELS ───────────────
 *
 * Tout est exprimé par rapport au côté : l'épaisseur du trait, la
 * longueur des bras, et la taille du damier intérieur. Un appel à 40.dp
 * rend le même dessin qu'un appel à 21.dp, sans qu'aucune valeur ne soit
 * à reprendre.
 * ═══════════════════════════════════════════════════════════════════════
 *
 * @param taille côté du carré complet, équerres comprises.
 * @param accent couleur des quatre équerres.
 * @param motif couleur du damier central.
 */
@Composable
fun MotifScanner(
    modifier: Modifier = Modifier,
    taille: Dp = 24.dp,
    accent: Color = AccentBlue,
    motif: Color = TextPrimary,
    contentDescription: String? = null
) {
    val description = contentDescription
    Box(
        modifier
            .size(taille)
            /*
            LA DESCRIPTION EST POSÉE SUR LE CARRÉ, PAS SUR L'ICÔNE.
            Le damier n'est qu'une moitié du dessin ; un lecteur d'écran
            doit annoncer « scanner un QR », pas « code QR ».
            */
            /*
            `this.` N'EST PAS DÉCORATIF ICI, ET SON ABSENCE A CASSÉ UN BUILD.

            Le paramètre de cette fonction s'appelle `contentDescription`,
            et la propriété sémantique aussi. Dans le bloc `semantics`, le
            paramètre est le plus proche : il MASQUE la propriété. Écrire
            `contentDescription = texte` revient donc à réaffecter un
            paramètre — « Val cannot be reassigned ».

            `this.` désigne explicitement le récepteur sémantique. Et
            `texte` évite d'avoir à relire le paramètre, dont le type est
            nullable.
            */
            .then(
                description?.let { texte ->
                    Modifier.semantics { this.contentDescription = texte }
                } ?: Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cote = size.minDimension
            val trait = cote * 0.105f
            val bras = cote * 0.30f
            // Le tracé déborde de la moitié de son épaisseur : on rentre
            // d'autant pour que les équerres restent dans le carré au lieu
            // d'être rognées.
            val m = trait / 2f
            // Haut-gauche, haut-droite, bas-gauche, bas-droite.
            for ((dx, sx) in listOf(m to 1f, (cote - m) to -1f)) {
                for ((dy, sy) in listOf(m to 1f, (cote - m) to -1f)) {
                    drawLine(
                        accent,
                        Offset(dx, dy),
                        Offset(dx + sx * bras, dy),
                        strokeWidth = trait,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        accent,
                        Offset(dx, dy),
                        Offset(dx, dy + sy * bras),
                        strokeWidth = trait,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
        Icon(
            Icons.Default.QrCode,
            contentDescription = null,
            tint = motif,
            // 12/21 — la proportion retenue sur le tableau de bord, gardée
            // telle quelle pour que le dessin ne change pas en se déplaçant.
            modifier = Modifier.size(taille * 0.571f)
        )
    }
}
