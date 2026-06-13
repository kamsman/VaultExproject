package com.vaultex.app

import android.content.Context
import android.os.Bundle
import android.view.WindowManager

import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.rememberNavController

import com.vaultex.BuildConfig
import com.vaultex.core.session.LocaleManager
import com.vaultex.core.session.SessionLockManager
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.navigation.VaultExNavGraph
import com.vaultex.ui.theme.ThemeController
import com.vaultex.ui.theme.VaultExTheme

import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var sessionLock: SessionLockManager

    @Inject
    lateinit var themeController: ThemeController

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /*
        =========================
        BLOCK SCREENSHOTS
        =========================
         */

        if (!BuildConfig.DEBUG) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        setContent {

            val themeMode by themeController.mode.collectAsState()

            VaultExTheme(themeMode = themeMode) {

                val navController = rememberNavController()

                /*
                =========================
                VERROUILLAGE AUTO AU RETOUR D'ARRIÈRE-PLAN
                =========================
                 */
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_STOP -> sessionLock.onEnterBackground()
                            Lifecycle.Event.ON_START -> {
                                if (sessionLock.shouldLockOnForeground()) {
                                    sessionLock.lock()
                                    navController.navigate(Routes.PIN_UNLOCK) {
                                        popUpTo(0) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                VaultExNavGraph(navController)
            }
        }
    }
}
