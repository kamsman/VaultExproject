package com.vaultex.core.session

import com.vaultex.core.security.SecureStorage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gère le verrouillage automatique de la session.
 *
 * Le routage au démarrage (SplashScreen) ne verrouille qu'au lancement à
 * froid : quand l'app passe en arrière-plan puis revient, l'Activity reste
 * vivante et aucun re-verrouillage n'avait lieu. Ce gestionnaire mémorise
 * l'instant de passage en arrière-plan et décide, au retour au premier plan,
 * s'il faut redemander le PIN selon le délai d'auto-verrouillage choisi.
 */
@Singleton
class SessionLockManager @Inject constructor(
    private val secureStorage: SecureStorage
) {
    private var backgroundedAt: Long = 0L

    /** La session a-t-elle été déverrouillée depuis le dernier verrouillage. */
    @Volatile
    var isUnlocked: Boolean = false
        private set

    /** Appelé après un déverrouillage réussi (PIN ou biométrie) ou la création du wallet. */
    fun markUnlocked() {
        isUnlocked = true
        backgroundedAt = 0L
    }

    /** Appelé quand l'app passe en arrière-plan. */
    fun onEnterBackground() {
        if (isUnlocked) backgroundedAt = System.currentTimeMillis()
    }

    /**
     * Vrai s'il faut reverrouiller au retour au premier plan :
     * un wallet existe, la session était déverrouillée, et le temps écoulé
     * en arrière-plan atteint le délai d'auto-verrouillage (0 = immédiat).
     */
    fun shouldLockOnForeground(): Boolean {
        if (!secureStorage.hasMnemonic()) return false
        if (!isUnlocked) return false          // cold start → le Splash gère le routage
        if (backgroundedAt == 0L) return false  // pas passé en arrière-plan
        val elapsedMs = System.currentTimeMillis() - backgroundedAt
        val thresholdMs = secureStorage.getAutoLockMinutes() * 60_000L
        return elapsedMs >= thresholdMs
    }

    /** Verrouille la session (force le PIN au prochain affichage). */
    fun lock() {
        isUnlocked = false
        backgroundedAt = 0L
    }
}
