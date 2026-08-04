package com.vaultex.core.monitoring

import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Enveloppe défensive autour de Firebase Crashlytics.
 *
 * Toutes les méthodes sont protégées : si Firebase n'est pas initialisé
 * (tests, variantes sans google-services.json), elles deviennent des
 * no-op silencieux au lieu de planter. Objectif : enrichir les rapports
 * de crash avec du contexte (logs de parcours, clés personnalisées,
 * exceptions non fatales) SANS introduire de point de défaillance.
 */
object CrashReporter {

    private val crashlytics: FirebaseCrashlytics?
        get() = try {
            FirebaseCrashlytics.getInstance()
        } catch (_: Throwable) {
            null
        }

    /** Fil d'Ariane attaché au prochain rapport de crash. */
    fun log(message: String) {
        try {
            crashlytics?.log(message)
        } catch (_: Throwable) { /* no-op */ }
    }

    /** Clé/valeur de contexte (ex: chaîne en cours, écran, état réseau). */
    fun setKey(key: String, value: String) {
        try {
            crashlytics?.setCustomKey(key, value)
        } catch (_: Throwable) { /* no-op */ }
    }

    fun setKey(key: String, value: Boolean) {
        try {
            crashlytics?.setCustomKey(key, value)
        } catch (_: Throwable) { /* no-op */ }
    }

    fun setKey(key: String, value: Long) {
        try {
            crashlytics?.setCustomKey(key, value)
        } catch (_: Throwable) { /* no-op */ }
    }

    /** Remonte une exception non fatale (catch volontaire) avec contexte. */
    fun recordException(throwable: Throwable, context: String? = null) {
        try {
            if (context != null) crashlytics?.log(context)
            crashlytics?.recordException(throwable)
        } catch (_: Throwable) { /* no-op */ }
    }
}
