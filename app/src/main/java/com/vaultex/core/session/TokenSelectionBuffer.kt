package com.vaultex.core.session

/**
 * Tampon de pré-sélection de monnaie. Quand l'utilisateur ouvre Envoyer /
 * Recevoir / Échanger DEPUIS la page d'une crypto (ex. BNB), on mémorise le
 * symbole ici ; l'écran cible le consomme une seule fois pour se positionner
 * automatiquement sur la bonne chaîne.
 */
object TokenSelectionBuffer {

    @Volatile
    private var pending: String? = null

    fun set(symbol: String?) {
        pending = symbol?.uppercase()
    }

    /** Récupère et efface le symbole en attente. */
    fun consume(): String? {
        val s = pending
        pending = null
        return s
    }
}
