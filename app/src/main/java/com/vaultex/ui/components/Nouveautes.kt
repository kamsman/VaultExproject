package com.vaultex.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

La version installée est désormais mémorisée. Quand elle change, cette
feuille s'ouvre une fois, puis plus jamais pour cette version-là.

DEUX RÈGLES, sans lesquelles l'écran se retourne contre son but :

· RIEN À LA PREMIÈRE INSTALLATION. « La recherche est maintenant un bouton
  rond » ne veut rien dire pour qui n'a jamais connu l'ancienne. On
  reconnaît un nouvel arrivant à l'absence de portefeuille : on enregistre
  alors la version en silence, et l'écran n'apparaîtra qu'aux mises à jour
  suivantes.

· QUE CE QUI SE VOIT. Les corrections internes n'intéressent personne et
  allongent un écran qui doit se lire en cinq secondes.
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

/** Feuille des nouveautés, au premier lancement suivant une mise à jour. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleNouveautes() {
    val contexte = LocalContext.current
    val prefs = remember { contexte.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    /*
    Décidé UNE SEULE FOIS, à la première composition.

    Lire la préférence à chaque recomposition ferait disparaître la feuille
    à l'instant où on l'enregistre : la valeur changerait sous elle et la
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
        // qu'un lecteur ne revoie pas la feuille au prochain lancement.
        if (vue != courante) prefs.edit().putInt(CLE_VERSION_VUE, courante).apply()

        mutableStateOf(doitAfficher)
    }

    if (!visible) return

    /*
    VOLONTAIREMENT COURT.

    La première version donnait à chaque nouveauté un titre ET une phrase
    d'explication, dans une feuille pleine largeur. Elle occupait presque tout
    l'écran pour dire quatre choses — un mur de texte devant l'accueil, que
    personne ne lit et que tout le monde referme.

    Une ligne par nouveauté, un bouton discret. Ce qui doit passer, c'est
    « voici ce qui a changé », pas le détail : celui qui veut en savoir plus
    ira voir l'écran concerné.
    */
    /*
    Fond LÉGÈREMENT TRANSLUCIDE.

    Deux réglages qui se compensent, et qu'il faut lire ensemble :

    · BgSecondary plutôt que BgPrimary. La feuille reprenait la couleur
      EXACTE du tableau de bord posé derrière : sans limite visible, elle s'y
      confondait. C'est ce contraste qui la fait exister en tant que panneau.
    · alpha à 0,92. Le tableau de bord transparaît juste assez pour qu'on
      sente ce qu'il y a dessous, sans que le texte perde en lisibilité —
      c'est la seule limite qui compte ici, l'écran est fait pour être lu.

    Sans le premier, le second donnerait une feuille à la fois translucide et
    de la même teinte que la page : plus rien ne la délimiterait.

    C'est de la translucidité, pas du flou. Un vrai verre dépoli demande un
    RenderEffect sur la fenêtre, indisponible avant Android 12 — donc chez la
    majorité des appareils visés.
    */
    ModalBottomSheet(
        onDismissRequest = { visible = false },
        containerColor = BgSecondary.copy(alpha = 0.92f),
        tonalElevation = 0.dp
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.whats_new_title),
                    fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary
                )
                Spacer(Modifier.width(8.dp))
                // La version tient sur la même ligne : elle sert au support,
                // elle ne mérite pas une ligne à elle.
                Text(BuildConfig.VERSION_NAME, fontSize = 12.sp, color = TextSecondary)
            }

            listOf(
                R.string.whats_new_1,
                R.string.whats_new_2,
                R.string.whats_new_3,
                R.string.whats_new_4
            ).forEach { ligne ->
                Row(Modifier.fillMaxWidth()) {
                    Text("•", fontSize = 14.sp, color = AccentBlue, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(ligne), fontSize = 14.sp, color = TextSecondary)
                }
            }

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
