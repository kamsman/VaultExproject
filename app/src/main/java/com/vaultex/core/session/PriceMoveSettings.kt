package com.vaultex.core.session

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Réglages et anti-spam des **alertes automatiques de variation de prix**.
 *
 * Contrairement aux alertes de prix classiques (l'utilisateur fixe une cible),
 * celles-ci sont ACTIVES PAR DÉFAUT : l'utilisateur est prévenu dès qu'une
 * monnaie bouge fortement sur 24 h, sans rien avoir à configurer. Il peut les
 * désactiver, ou choisir à partir de quel pourcentage il veut être dérangé.
 *
 * Le vrai piège de ce genre de notification, c'est le harcèlement : une
 * variation sur 24 h oscille autour du seuil et déclencherait une alerte à
 * chaque passage du worker. [evaluate] applique donc deux règles :
 *
 * 1. **Réarmement** : tant que la variation reste au-dessus du seuil, on ne
 *    renotifie pas. Il faut qu'elle soit redescendue en dessous pour qu'un
 *    nouveau franchissement compte comme un événement.
 * 2. **Délai de garde** : une même monnaie dans le même sens n'est jamais
 *    renotifiée avant [COOLDOWN_MS], même si le mouvement s'amplifie. Un
 *    changement de sens (hausse → baisse), lui, passe immédiatement : c'est
 *    une information réellement nouvelle.
 */
@Singleton
class PriceMoveSettings @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Alertes de variation actives. ACTIVÉES par défaut. */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) { prefs.edit().putBoolean(KEY_ENABLED, value).apply() }

    /** Seuil de déclenchement, en pourcentage de variation sur 24 h. */
    var thresholdPercent: Int
        get() = prefs.getInt(KEY_THRESHOLD, DEFAULT_THRESHOLD)
        set(value) { prefs.edit().putInt(KEY_THRESHOLD, value).apply() }

    /**
     * Faut-il notifier [symbol] pour une variation de [changePercent] % ?
     *
     * @return "up" ou "down" s'il faut notifier (l'état anti-spam est alors
     *   enregistré), `null` sinon.
     */
    fun evaluate(symbol: String, changePercent: Double, now: Long): String? {
        if (!enabled) return null

        if (abs(changePercent) < thresholdPercent) {
            // Retombé sous le seuil : on réarme, le prochain franchissement
            // sera de nouveau considéré comme un événement.
            if (prefs.contains(dirKey(symbol))) {
                prefs.edit().remove(dirKey(symbol)).apply()
            }
            return null
        }

        val direction = if (changePercent > 0) "up" else "down"
        val lastDirection = prefs.getString(dirKey(symbol), null)
        val lastNotifiedAt = prefs.getLong(timeKey(symbol), 0L)

        val directionChanged = direction != lastDirection
        val cooldownOver = now - lastNotifiedAt >= COOLDOWN_MS
        if (!directionChanged && !cooldownOver) return null

        prefs.edit()
            .putString(dirKey(symbol), direction)
            .putLong(timeKey(symbol), now)
            .apply()
        return direction
    }

    private fun dirKey(symbol: String) = "dir_$symbol"
    private fun timeKey(symbol: String) = "at_$symbol"

    companion object {
        private const val PREFS = "vaultex_price_moves"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_THRESHOLD = "threshold_percent"

        /** 5 % sur 24 h : assez rare pour ne pas noyer l'utilisateur, assez
         *  fréquent pour rester utile sur les cryptos. */
        const val DEFAULT_THRESHOLD = 5

        /** Seuils proposés dans l'écran Alertes. */
        val THRESHOLD_CHOICES = listOf(3, 5, 10, 20)

        /** Délai de garde entre deux alertes d'une même monnaie dans le même sens. */
        private const val COOLDOWN_MS = 12L * 60 * 60 * 1000
    }
}
