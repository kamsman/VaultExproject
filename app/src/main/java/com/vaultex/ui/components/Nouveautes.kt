package com.vaultex.ui.components

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultex.BuildConfig
import com.vaultex.R
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.BgSecondary
import com.vaultex.ui.theme.TextPrimary
import com.vaultex.ui.theme.TextSecondary

/*
═══════════════════════════════════════════════════════════════════════════
« NOUVEAUTÉS » — ce qui a changé, dit une seule fois
═══════════════════════════════════════════════════════════════════════════

Une mise à jour changeait l'application sans un mot. La barre de recherche
devenue un bouton rond, la liste passée de 100 à 1000 monnaies : autant de
choses qui, sans explication, ressemblent d'abord à un dérangement. Et comme
l'application se distribue aussi en APK hors magasin, il n'y a même pas de
notes de version pour rattraper.

La version installée est désormais mémorisée. Quand elle change, une carte
s'affiche une fois sur l'accueil, puis plus jamais pour cette version-là.

DEUX RÈGLES, sans lesquelles l'écran se retourne contre son but :

· RIEN À LA PREMIÈRE INSTALLATION. « La recherche est maintenant un bouton
  rond » ne veut rien dire pour qui n'a jamais connu l'ancienne. On
  reconnaît un nouvel arrivant à l'absence de portefeuille : on enregistre
  alors la version en silence, et l'écran n'apparaîtra qu'aux mises à jour
  suivantes.

· QUE CE QUI SE VOIT. Les corrections internes n'intéressent personne et
  allongent un écran qui doit se lire en cinq secondes.

À RÉÉCRIRE À CHAQUE VERSION — whats_new_1 à whats_new_4, dans les trois
langues. C'est la seule charge d'entretien de cet écran, et la seule façon
de le rendre nuisible est de l'oublier : une carte qui annonce les
nouveautés de l'avant-dernière version est pire que pas de carte du tout.

La liste est FIXE, elle ne s'adapte pas à la version d'où l'on vient. Celui
qui saute trois versions verra les nouveautés de la dernière seulement.
C'est assumé : un journal complet demanderait de conserver l'historique de
chaque version, pour un écran que l'on referme en cinq secondes.
*/

private const val PREFS = "vaultex_nouveautes"
private const val CLE_VERSION_VUE = "version_vue"

/**
 * À appeler AU DÉMARRAGE, depuis le splash — le seul instant où l'on peut
 * encore distinguer un nouvel arrivant d'un utilisateur qui met à jour.
 *
 * Passé l'accueil, la distinction est perdue : celui qui vient de créer son
 * portefeuille pendant l'intégration en a un, exactement comme un ancien.
 *
 * Ce premier passage tranche donc une fois pour toutes :
 *  · sans portefeuille — nouvel arrivant. On note la version courante : il
 *    ne verra pas des nouveautés qu'il n'a jamais manquées.
 *  · avec portefeuille — il vient de mettre à jour depuis une version qui
 *    ignorait ce mécanisme. On note 1, inférieur à toute version réelle,
 *    pour qu'il voie bien la feuille.
 */
fun marquerDemarrage(contexte: Context, aUnPortefeuille: Boolean) {
    val prefs = contexte.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    if (prefs.contains(CLE_VERSION_VUE)) return
    prefs.edit()
        .putInt(CLE_VERSION_VUE, if (aUnPortefeuille) 1 else BuildConfig.VERSION_CODE)
        .apply()
}

/**
 * Carte des nouveautés, au premier lancement suivant une mise à jour.
 *
 * NON MODALE, et c'est le point. La première version était une feuille qui
 * assombrissait tout l'écran et bloquait l'interaction — pour quatre lignes
 * n'exigeant aucune décision. Une note d'information ne mérite pas de
 * s'imposer : elle se pose PAR-DESSUS la page sans la bloquer, se lit d'un
 * coup d'œil, et se referme d'une croix.
 *
 * FLOTTANTE : à poser en superposition, au-dessus de la barre de navigation,
 * et non dans le flux d'une liste. Placée dans le flux, elle défile avec le
 * contenu et disparaît dès qu'on descend — une note qu'il faut chercher
 * n'informe personne.
 *
 * Le fond translucide prend ici tout son sens : le contenu défile DERRIÈRE
 * elle, et on le voit passer.
 */
@Composable
fun CarteNouveautes(modifier: Modifier = Modifier) {
    val contexte = LocalContext.current
    val prefs = remember { contexte.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    /*
    Décidé UNE SEULE FOIS, à la première composition.

    Lire la préférence à chaque recomposition ferait disparaître la carte à
    l'instant où on l'enregistre : la valeur changerait sous elle et la
    condition deviendrait fausse avant même que l'utilisateur ait lu.
    */
    var visible by remember {
        val vue = prefs.getInt(CLE_VERSION_VUE, 0)
        val courante = BuildConfig.VERSION_CODE

        // vue == 0 : marquerDemarrage() n'est jamais passé. On ne sait pas à
        // qui l'on parle, donc on se tait — se taire à tort est sans
        // conséquence, parler à tort déroute.
        val doitAfficher = vue in 1 until courante

        // Enregistré tout de suite, y compris quand on n'affiche rien, pour
        // qu'un lecteur ne revoie pas la carte au prochain lancement.
        if (vue != courante) prefs.edit().putInt(CLE_VERSION_VUE, courante).apply()

        mutableStateOf(doitAfficher)
    }

    if (!visible) return

    Surface(
        shape = RoundedCornerShape(18.dp),
        // Translucide : l'accueil transparaît légèrement dessous, ce qui pose
        // la carte SUR la page au lieu de l'y découper.
        color = BgSecondary.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🚀", fontSize = 17.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.whats_new_title),
                    fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                // Fermer sans lire doit rester possible d'un seul geste : la
                // croix est là pour ceux que la nouveauté n'intéresse pas.
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { visible = false },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close,
                        stringResource(R.string.close),
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            listOf(
                R.string.whats_new_1,
                R.string.whats_new_2,
                R.string.whats_new_3,
                R.string.whats_new_4
            ).forEach { ligne ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text("•", fontSize = 14.sp, color = AccentBlue, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(ligne), fontSize = 13.sp, color = TextPrimary)
                }
            }

            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { visible = false },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.whats_new_cta), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
