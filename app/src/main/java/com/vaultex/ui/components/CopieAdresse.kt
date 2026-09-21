package com.vaultex.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
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

POURQUOI LA PREMIÈRE VERSION NE VIBRAIT PAS
───────────────────────────────────────────
Elle appelait uniquement `performHapticFeedback` de Compose. Ce chemin passe
par `View.performHapticFeedback`, que le système IGNORE EN SILENCE quand
« Vibration au toucher » est désactivé dans les réglages — cas courant sur
Samsung, où beaucoup la coupent pour économiser la batterie. L'appel
réussissait, ne renvoyait aucune erreur, et rien ne se produisait.

Ce réglage gouverne le retour du clavier et des boutons, c'est-à-dire des
dizaines de secousses par minute. Copier une adresse est une action
délibérée, rare, et dont l'utilisateur ATTEND une confirmation : la ranger
dans la même catégorie était l'erreur.

On passe donc par le vibreur, que la permission VIBRATE du manifeste
autorise déjà — voir plus bas pour la forme exacte de la tape.

Ce qui reste respecté, et doit l'être : le mode silencieux, l'intensité de
vibration réglée par l'utilisateur, et l'absence pure et simple de vibreur.
Dans ces cas le système ne fait rien, et c'est sa décision, pas la nôtre.

POURQUOI UN SEUL POINT DE PASSAGE
─────────────────────────────────
La copie existe sur sept écrans — réception, carnet d'adresses, détail d'un
envoi, suivi d'échange. Écrire sept fois les mêmes lignes garantissait
qu'elles finiraient par diverger : une vibration ici, rien là, une intensité
différente ailleurs. Un écran qui réagit autrement que son voisin donne
l'impression que l'un des deux n'a pas fonctionné.
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
    val secours = LocalHapticFeedback.current
    val contexte = LocalContext.current
    return remember(presse, secours, contexte) {
        { valeur: String ->
            if (valeur.isNotBlank()) {
                presse.setText(AnnotatedString(valeur))
                vibrerCourt(contexte, secours)
            }
        }
    }
}

/*
DEUXIÈME CORRECTION : LA TAPE ÉTAIT TROP COURTE POUR SE SENTIR
──────────────────────────────────────────────────────────────
Passer par le vibreur ne suffisait pas. `createOneShot(28 ms)` à
l'amplitude par défaut est imperceptible sur beaucoup d'appareils, Samsung
en tête : le moteur n'a pas le temps de monter en régime, et la commande
réussit sans que rien ne se sente.

Deux changements, et il fallait les deux.

EFFECT_CLICK PLUTÔT QU'UNE DURÉE CHOISIE PAR NOUS. Depuis Android 10, le
système expose des effets tactiles calibrés PAR LE CONSTRUCTEUR pour son
propre moteur. Un « clic » y est net sur un appareil dont la vibration est
molle comme sur un autre où elle est sèche. Deviner une durée revenait à
régler à l'aveugle un matériel qu'on ne connaît pas ; en dessous
d'Android 10, faute de mieux, on allonge à 50 ms.

DES ATTRIBUTS QUI DISENT CE QUE C'EST. Une vibration émise sans attribut
est rangée dans « usage inconnu », et le système la supprime dans plusieurs
situations — certains modes de concentration, certaines surcouches.
Déclarer une sonification la range avec les retours d'interface, qui
survivent à ces filtres.

Ce qui continue d'être respecté : le mode silencieux, l'intensité de
vibration réglée par l'utilisateur, et l'absence de vibreur. Dans ces cas
le système ne fait rien, et c'est sa décision.
*/

/** Une tape nette, calibrée par le système quand il sait le faire. */
private fun vibrerCourt(contexte: Context, secours: HapticFeedback) {
    val vibreur: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (contexte.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            contexte.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (_: Exception) {
        null
    }

    if (vibreur != null && vibreur.hasVibrator()) {
        val effet =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            else
                VibrationEffect.createOneShot(50L, VibrationEffect.DEFAULT_AMPLITUDE)

        val attributs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val ok = runCatching { vibreur.vibrate(effet, attributs) }.isSuccess
        if (ok) return
    }
    // Aucun vibreur, ou appel refusé : on tente au moins le retour de la vue.
    runCatching { secours.performHapticFeedback(HapticFeedbackType.LongPress) }
}
