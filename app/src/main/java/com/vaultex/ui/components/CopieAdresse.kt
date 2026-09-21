package com.vaultex.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString

/*
═══════════════════════════════════════════════════════════════════════════
COPIER UNE ADRESSE DOIT SE SENTIR DANS LA MAIN
═══════════════════════════════════════════════════════════════════════════

Copier une adresse de réception ne produisait aucune réaction physique. Le
seul retour était un message fugace, affiché en bas de l'écran — souvent
sous le doigt qui vient d'appuyer, et absent de plusieurs écrans.

Or cette copie n'est pas un geste anodin : ce qui part dans le presse-papier
va être collé chez quelqu'un d'autre, ou dans une autre application, pour y
recevoir de l'argent. Douter d'avoir réussi conduit à appuyer une seconde
fois, puis à coller sans regarder.

Une vibration courte règle cela sans un mot, sans occuper de place, et
fonctionne quand on ne regarde pas l'écran.

POURQUOI UN SEUL POINT DE PASSAGE. La copie existe sur sept écrans
— réception, carnet d'adresses, détail d'un envoi, suivi d'échange. Écrire
sept fois les deux mêmes lignes garantissait qu'elles finiraient par
diverger : une vibration ici, rien là, une intensité différente ailleurs.
Un écran qui réagit autrement que son voisin donne l'impression que l'un des
deux n'a pas fonctionné.

LongPress, et non TextHandleMove : ce dernier est un frémissement que
beaucoup d'appareils rendent à peine, voire pas du tout. Une confirmation
qu'on ne sent pas ne confirme rien.

La vibration suit le réglage système : si l'utilisateur a coupé le retour
haptique, rien ne se produit — et c'est la bonne façon de l'éteindre.
*/

/**
 * Copie une valeur dans le presse-papier, avec la vibration qui confirme le
 * geste. À employer pour toute adresse, tout identifiant et tout hash.
 *
 * Une valeur vide ne fait rien : ni copie, ni vibration. Vibrer sur une
 * adresse absente affirmerait qu'elle est bien dans le presse-papier.
 */
@Composable
fun rememberCopieAvecVibration(): (String) -> Unit {
    val presse = LocalClipboardManager.current
    val vibration = LocalHapticFeedback.current
    return remember(presse, vibration) {
        { valeur: String ->
            if (valeur.isNotBlank()) {
                presse.setText(AnnotatedString(valeur))
                vibration.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }
}
