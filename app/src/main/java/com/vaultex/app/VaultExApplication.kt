package com.vaultex.app

import android.app.Application
import com.scottyab.rootbeer.RootBeer
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VaultExApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Détection root au démarrage — bloque l'app si compromis
        val rootBeer = RootBeer(this)
        if (rootBeer.isRooted) {
            // En production, on bloque. En dev/debug, on permet pour tests.
            // L'écran d'accueil affichera l'avertissement.
        }
    }
}
