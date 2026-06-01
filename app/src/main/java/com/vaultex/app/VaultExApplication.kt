package com.vaultex.app

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.os.Bundle
import com.scottyab.rootbeer.RootBeer
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VaultExApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val rootBeer = RootBeer(this)
        if (rootBeer.isRooted) {
            // Bloquer l'app sur chaque Activity créée
            registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    AlertDialog.Builder(activity)
                        .setTitle("Appareil compromis")
                        .setMessage(
                            "Cet appareil est rooté ou modifié. " +
                            "VaultEx ne peut pas garantir la sécurité de vos fonds. " +
                            "L'application va se fermer."
                        )
                        .setCancelable(false)
                        .setPositiveButton("Fermer") { _, _ -> activity.finishAffinity() }
                        .show()
                }
                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            })
        }
    }
}
