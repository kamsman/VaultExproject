package com.vaultex.core.security

import android.app.Activity
import android.view.WindowManager

/**
 * Applique — ou lève — l'interdiction de capturer l'écran.
 *
 * FLAG_SECURE était posé une fois pour toutes dans MainActivity.onCreate.
 * C'était le bon réglage par défaut, mais un réglage qu'on ne pouvait pas
 * changer : ni pour envoyer la preuve d'une transaction à un correspondant,
 * ni pour montrer un problème au support — deux gestes quotidiens ici, où
 * tout passe par WhatsApp.
 *
 * Le drapeau se pose donc à partir du choix enregistré, et cette fonction
 * est le SEUL endroit qui le manipule. Elle est appelée à deux moments :
 *
 * · au démarrage de l'activité, pour rétablir le choix précédent ;
 * · au basculement de l'interrupteur dans les Réglages, pour que l'effet
 *   soit immédiat — sans cela, il aurait fallu redémarrer l'application
 *   pour voir quoi que ce soit, et l'utilisateur aurait cru l'option
 *   cassée.
 *
 * Ce que le drapeau couvre : la capture d'écran, l'enregistrement vidéo,
 * le partage d'écran, et l'aperçu de l'application dans la liste des
 * tâches récentes. Ce qu'il ne couvre PAS : une photo de l'écran prise
 * avec un autre téléphone. Aucun réglage logiciel ne peut l'empêcher.
 */
object ProtectionEcran {

    fun appliquer(activite: Activity, capturesAutorisees: Boolean) {
        if (capturesAutorisees) {
            activite.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activite.window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
    }
}
