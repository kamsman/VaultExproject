package com.vaultex.core.session

import android.content.Context
import android.os.Build

/**
 * Le point 2 signalé par un test réel : les notifications de réception, et le
 * comptage qui va avec, doivent fonctionner APP FERMÉE, pas seulement en
 * arrière-plan récent.
 *
 * `DepositCheckWorker` tourne déjà via WorkManager toutes les 15 min — Android
 * NE tue PAS ce travail quand l'utilisateur balaie l'app hors des tâches
 * récentes, contrairement à une simple activité ou un service classique. Le
 * vrai risque n'est donc pas dans notre code : c'est que certains
 * constructeurs vont plus loin que le système Android standard et ajoutent
 * leur PROPRE gestionnaire de batterie, qui tue les tâches d'arrière-plan même
 * après avoir accordé l'exemption Android officielle (déjà demandée via
 * `ensureBatteryExemption`). C'est un problème documenté et répandu sur les
 * marques les plus vendues en zone UEMOA : Tecno / Infinix / itel
 * (Transsion), et Xiaomi/Redmi/Poco.
 *
 * On ne peut pas forcer ce réglage depuis le code (aucune API publique ne le
 * permet, par conception — sinon ce serait une faille de sécurité Android).
 * La seule action fiable est d'expliquer le geste à faire une fois, et de
 * renvoyer vers les réglages de l'app pour que l'utilisateur l'active
 * lui-même.
 */
object BackgroundReliability {

    /** Marques connues pour tuer les tâches d'arrière-plan malgré l'exemption Android standard. */
    private val AGGRESSIVE_BRANDS = setOf(
        "transsion", "tecno", "infinix", "itel",           // dominants en UEMOA
        "xiaomi", "redmi", "poco",
        "huawei", "honor", "oppo", "vivo", "realme", "oneplus"
    )

    fun isKnownAggressiveOem(): Boolean {
        val signature = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
        return AGGRESSIVE_BRANDS.any { signature.contains(it) }
    }

    private const val PREFS = "vaultex_boot"
    private const val KEY_DISMISSED = "autostart_tip_dismissed"

    /**
     * Permanent, pas par session : contrairement au rappel de sauvegarde
     * (qui DOIT reparaître tant que l'état réel n'est pas confirmé), ceci est
     * une information ponctuelle — une fois lue et le réglage ouvert, la
     * répéter n'apporterait plus rien.
     */
    fun isDismissed(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DISMISSED, false)

    fun dismiss(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DISMISSED, true).apply()
    }
}
