package com.vaultex.core.session

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Applique la langue choisie par l'utilisateur (français / anglais / arabe)
 * au niveau du Context de l'Activity, via attachBaseContext.
 *
 * Stocké dans des SharedPreferences simples (la langue n'est pas sensible)
 * pour pouvoir être lu avant l'injection Hilt, dès attachBaseContext.
 * setLayoutDirection assure le RTL automatique pour l'arabe.
 */
object LocaleManager {

    private const val PREFS = "vaultex_locale"
    private const val KEY_LANG = "app_language"

    /** Langues supportées (code ISO 639-1). La 1re est la valeur par défaut. */
    val SUPPORTED = listOf("fr", "en", "ar")

    fun getLanguage(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANG, "fr") ?: "fr"

    fun setLanguage(context: Context, lang: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANG, lang).apply()
        cached = Locale(lang)
    }

    /**
     * Langue de l'application, lisible SANS Context.
     *
     * Sert au formatage des nombres. `Locale.getDefault()` ne suffit pas : il
     * n'est aligné sur le choix de l'utilisateur qu'une fois [wrap] appelé,
     * c'est-à-dire à la création d'une Activity. Or les workers (dépôt, prix,
     * suivi de swap) tournent aussi APPLICATION FERMÉE, parfois juste après un
     * redémarrage du téléphone : à ce moment-là aucune Activity n'a existé, et
     * une notification serait formatée dans la langue du système au lieu de
     * celle choisie dans l'app.
     *
     * [prime] est appelé au démarrage de l'Application, avant tout worker.
     */
    @Volatile
    private var cached: Locale? = null

    fun prime(context: Context) {
        cached = Locale(getLanguage(context))
    }

    /** Repli sur le français : c'est la langue par défaut de l'application. */
    fun appLocale(): Locale = cached ?: Locale.FRENCH

    /** Renvoie un Context dont la configuration applique la langue choisie. */
    fun wrap(context: Context): Context {
        val locale = Locale(getLanguage(context))
        cached = locale
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale) // RTL pour l'arabe
        return context.createConfigurationContext(config)
    }
}
