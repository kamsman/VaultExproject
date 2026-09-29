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

    /*
    ═══════════════════════════════════════════════════════════════════════
    CERTAINS ÉCRANS NE SE NÉGOCIENT PAS
    ═══════════════════════════════════════════════════════════════════════

    Le réglage ci-dessus est un arbitrage légitime, et le commentaire de
    SecureStorage dit pourquoi : ici tout passe par WhatsApp, et refuser
    toute capture empêche d'envoyer la preuve d'une transaction ou de
    montrer un problème au support.

    Mais ce réglage est UNE PROPRIÉTÉ DE FENÊTRE, donc un interrupteur
    unique pour toute l'application. Quelqu'un qui l'active pour envoyer
    un reçu l'active aussi, sans le savoir et sans limite de temps, sur
    l'écran qui affiche sa phrase de récupération et sur celui qui exporte
    une clé privée.

    Le geste qui suit est celui qu'on redoute : capturer ses vingt-quatre
    mots « pour les garder ». L'image part dans la galerie, lisible par
    toute application ayant l'accès aux photos, et synchronisée vers le
    nuage par défaut. Il n'existe aucune récupération après ça.

    Ces écrans-là reposent donc le drapeau pour eux-mêmes, quel que soit le
    réglage, et RENDENT L'ÉTAT PRÉCÉDENT en sortant. On lit l'état réel de
    la fenêtre plutôt que la préférence : la fonction n'a alors rien à
    savoir du stockage, et ne peut pas se désynchroniser de lui.
    */
    @androidx.compose.runtime.Composable
    fun EcranSecret() {
        val contexte = androidx.compose.ui.platform.LocalContext.current
        val activite = genererActivite(contexte) ?: return
        androidx.compose.runtime.DisposableEffect(Unit) {
            val fenetre = activite.window
            val etaitDejaProtege =
                (fenetre.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
            fenetre.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
            onDispose {
                if (!etaitDejaProtege) {
                    fenetre.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
    }

    /**
     * L'Activity derrière un Context de composition.
     *
     * `LocalContext.current` n'est pas toujours l'Activity : Compose
     * l'enveloppe (ContextWrapper) selon le thème et le conteneur. Un
     * transtypage direct renverrait null sur certains appareils — et la
     * protection ne serait pas posée, en silence.
     */
    private fun genererActivite(depart: android.content.Context): Activity? {
        var c = depart
        while (c is android.content.ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }
}
