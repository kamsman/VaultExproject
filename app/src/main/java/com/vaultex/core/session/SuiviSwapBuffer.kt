package com.vaultex.core.session

/*
═══════════════════════════════════════════════════════════════════════════
« SWAP » VEUT DIRE « JE VEUX ÉCHANGER », PAS « MONTRE-MOI L'ÉCHANGE D'AVANT »
═══════════════════════════════════════════════════════════════════════════

L'écran Swap choisit ce qu'il affiche d'après un seul fait : y a-t-il un
échange en cours ? Si oui, il montre le suivi. Toujours, quelle que soit la
façon dont on est arrivé.

Or un échange dure deux à cinq minutes, et pendant tout ce temps le bouton
central de la barre du bas — le plus gros de l'écran — ramène sur le suivi de
l'échange précédent au lieu d'ouvrir le formulaire. Le bouton de la barre
restaure en plus l'écran tel qu'il était, coche verte comprise.

Vu de l'utilisateur, c'est indistinguable d'un écran bloqué : il croit être
reparti, il touche Swap, et le même écran est là, figé sur sa conclusion.
C'est ce qui a été rapporté plusieurs fois comme « cet écran ne part jamais »
alors que le départ automatique, lui, fonctionnait.

DEUX INTENTIONS, DEUX DESTINATIONS. Toucher « Swap » veut dire échanger :
le formulaire. Toucher « Échange en cours » sur l'accueil veut dire suivre
CET échange-là : le suivi. Rien dans la destination ne distinguait les deux ;
ce tampon porte la différence.

POURQUOI UN TAMPON ET PAS UN ARGUMENT DE ROUTE. La route « swap » est aussi
celle de la barre du bas, qui restaure l'entrée sauvegardée plutôt que d'en
créer une neuve : un argument d'URL y serait ignoré au retour. Le tampon,
lui, est lu à la composition, restauration comprise.

À CONSOMMATION UNIQUE, délibérément. Revenir sur l'écran Swap plus tard, par
un autre chemin, ne doit pas rouvrir un suivi qu'on n'a pas demandé.
*/
object SuiviSwapBuffer {

    @Volatile
    private var demande: Boolean = false

    /** L'écran Swap devra s'ouvrir sur le SUIVI, pas sur le formulaire. */
    fun demanderSuivi() {
        demande = true
    }

    /** Récupère et efface la demande. */
    fun consume(): Boolean {
        val d = demande
        demande = false
        return d
    }
}
