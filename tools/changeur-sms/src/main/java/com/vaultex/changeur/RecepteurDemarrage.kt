package com.vaultex.changeur

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/*
═══════════════════════════════════════════════════════════════════════════
LE REDÉMARRAGE DU TÉLÉPHONE
═══════════════════════════════════════════════════════════════════════════

Sans ce receveur, la veille s'arrête au premier redémarrage et ne repart
que le jour où quelqu'un rouvre l'application. Or un téléphone de
commerçant redémarre : batterie vide, mise à jour système, plantage.

La diffusion `SMS_RECEIVED`, elle, repart toute seule après un
redémarrage — c'est le receveur du manifeste qui s'en charge, et c'est
pour ça que le robot a pu fonctionner des semaines sans ce fichier. Ce
qui ne repart pas, c'est le RATTRAPAGE et la NOTIFICATION, donc
précisément ce qui permet de savoir qu'il fonctionne.

UN SERVICE DÉMARRÉ AU BOOT PEUT ÊTRE REFUSÉ. Android restreint le
démarrage de services en avant-plan depuis l'arrière-plan ; `demarrer`
attrape le refus et se tait. Le robot retombe alors sur la diffusion
seule, comme avant — on perd le filet, pas l'écoute.
═══════════════════════════════════════════════════════════════════════════
*/
class RecepteurDemarrage : BroadcastReceiver() {
    override fun onReceive(contexte: Context, intention: Intent) {
        if (intention.action != Intent.ACTION_BOOT_COMPLETED) return
        val reglages = Reglages(contexte)
        // Rien à veiller tant que le changeur n'a pas réglé le relais.
        if (!reglages.configure) return
        ServiceVeille.demarrer(contexte)
    }
}
