package com.vaultex.core.session

/**
 * Redémarrage PROPRE du process après un effacement complet (PIN de panique,
 * « Effacer le wallet »). Indispensable : après nukeAllData(), les singletons
 * encore en RAM (base Room dont le fichier a été supprimé, prefs chiffrées
 * liées à la master key détruite) sont dans un état mort — continuer dans le
 * même process ferait échouer silencieusement tout ré-import de seed.
 * Un process neuf repart sur des fondations saines → l'accueil s'affiche et
 * l'import fonctionne.
 */
object AppRestart {
    fun restart(context: android.content.Context) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                addFlags(
                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                        android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                )
            }
            if (intent != null) context.startActivity(intent)
        } catch (_: Exception) { }
        Runtime.getRuntime().exit(0)
    }
}
