package com.vaultex.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultex.BuildConfig
import com.vaultex.R
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.BgPrimary
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

    ModalBottomSheet(
        onDismissRequest = { visible = false },
        containerColor = BgPrimary
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                stringResource(R.string.whats_new_title),
                fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary
            )
            Text(
                stringResource(R.string.whats_new_version, BuildConfig.VERSION_NAME),
                fontSize = 13.sp, color = TextSecondary
            )
            Spacer(Modifier.height(16.dp))

            Nouveaute(R.string.whats_new_1_title, R.string.whats_new_1_body)
            Nouveaute(R.string.whats_new_2_title, R.string.whats_new_2_body)
            Nouveaute(R.string.whats_new_3_title, R.string.whats_new_3_body)
            Nouveaute(R.string.whats_new_4_title, R.string.whats_new_4_body)

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { visible = false },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(R.string.whats_new_cta),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun Nouveaute(titre: Int, corps: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("•", fontSize = 15.sp, color = AccentBlue, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                stringResource(titre),
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary
            )
            Text(stringResource(corps), fontSize = 13.sp, color = TextSecondary)
        }
    }
}
