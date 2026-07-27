package com.vaultex.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager

import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import androidx.navigation.compose.rememberNavController

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

import com.vaultex.BuildConfig
import com.vaultex.R
import com.vaultex.core.monitoring.CrashReporter
import com.vaultex.core.security.DeviceIntegrity
import com.vaultex.core.session.LocaleManager
import com.vaultex.core.session.SessionLockManager
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.navigation.VaultExNavGraph
import com.vaultex.ui.theme.BgPrimary
import com.vaultex.ui.theme.ThemeController
import com.vaultex.ui.theme.ThemeMode
import com.vaultex.ui.theme.VaultExTheme

import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var sessionLock: SessionLockManager

    @Inject
    lateinit var themeController: ThemeController

    @Inject
    lateinit var toastController: com.vaultex.core.session.ToastController

    // Demande de permission notifications (Android 13+). Sans elle, AUCUNE
    // notification (alertes prix, push) n'apparaît — elle est refusée par défaut.
    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* résultat ignoré */ }

    /** Demande POST_NOTIFICATIONS si nécessaire (uniquement sur Android 13+). */
    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    /**
     * Exemption d'optimisation batterie, demandée UNE seule fois. Les surcouches
     * agressives (Tecno/Infinix/itel = Transsion, Xiaomi…) — très répandues sur
     * notre marché — tuent les workers en arrière-plan : sans exemption, plus
     * AUCUNE notification de dépôt ni d'alerte prix n'arrive.
     */
    private fun ensureBatteryExemption() {
        try {
            val prefs = getSharedPreferences("vaultex_boot", MODE_PRIVATE)
            if (prefs.getBoolean("battery_asked", false)) return
            prefs.edit().putBoolean("battery_asked", true).apply()
            val pm = getSystemService(android.os.PowerManager::class.java)
            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        android.net.Uri.parse("package:$packageName")
                    )
                )
            }
        } catch (_: Exception) { /* refus/indispo : sans impact sur l'app */ }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        // P5 : deep link reçu alors que l'app tourne déjà
        com.vaultex.core.session.DeepLinkBuffer.offer(intent.dataString)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // P5 : adresse de paiement issue d'un deep link — validée par
        // AddressValidator avant toute mémorisation/préremplissage.
        com.vaultex.core.session.DeepLinkBuffer.offer(intent?.dataString)

        /*
        =========================
        BLOCK SCREENSHOTS — actif en toutes circonstances (M-02)
        =========================
         */
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        /*
        =========================
        ROOT DETECTION BLOQUANTE (C-01)
        En release, un appareil rooté ne peut pas accéder au wallet.
        =========================
         */
        if (!BuildConfig.DEBUG && DeviceIntegrity.isCompromised(this)) {
            val hooked = DeviceIntegrity.isHookingDetected(this)
            CrashReporter.setKey("device_rooted", DeviceIntegrity.isDeviceRooted(this))
            CrashReporter.setKey("device_hooked", hooked)
            CrashReporter.log("Accès bloqué : appareil rooté ou instrumenté (hooking=$hooked)")
            setContent {
                VaultExTheme {
                    RootBlockedScreen()
                }
            }
            return
        }

        // Android 13+ : demander l'autorisation d'afficher des notifications.
        ensureNotificationPermission()
        // Surcouches Transsion/Xiaomi : exemption batterie pour que les
        // notifications de dépôt continuent d'arriver en arrière-plan.
        ensureBatteryExemption()

        // ─── Détection RAPIDE des dépôts quand l'app est OUVERTE ───
        // Le worker périodique tourne au mieux toutes les 15 min (minimum
        // imposé par Android en arrière-plan). Au premier plan, on relance la
        // MÊME vérification toutes les 30 s : un dépôt reçu pendant que
        // l'utilisateur regarde l'app est signalé en secondes, plus en minutes.
        // (Le vrai temps réel en arrière-plan vient de la Cloud Function
        // checkDeposits + push FCM, une fois déployée.)
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    androidx.work.WorkManager.getInstance(this@MainActivity).enqueueUniqueWork(
                        com.vaultex.service.DepositCheckWorker.WORK_NAME + "_fg",
                        androidx.work.ExistingWorkPolicy.KEEP,
                        androidx.work.OneTimeWorkRequest.Builder(
                            com.vaultex.service.DepositCheckWorker::class.java
                        ).build()
                    )
                    kotlinx.coroutines.delay(30_000L)
                }
            }
        }

        setContent {

            val themeMode by themeController.mode.collectAsState()

            VaultExTheme(themeMode = themeMode) {

                // Barres système (icônes + couleurs) alignées sur le thème RÉSOLU.
                // Sans ça, en mode CLAIR les icônes de la barre d'état restaient
                // blanches (invisibles sur fond blanc) : réglage codé en dur dans
                // themes.xml. Ici, tout suit le thème effectif (système par défaut).
                val barsView = LocalView.current
                val darkTheme = when (themeMode) {
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                }
                val barColor = BgPrimary
                SideEffect {
                    window.statusBarColor = barColor.toArgb()
                    window.navigationBarColor = barColor.toArgb()
                    val controller = WindowCompat.getInsetsController(window, barsView)
                    controller.isAppearanceLightStatusBars = !darkTheme
                    controller.isAppearanceLightNavigationBars = !darkTheme
                }

                val navController = rememberNavController()

                /*
                =========================
                VERROUILLAGE AUTO AU RETOUR D'ARRIÈRE-PLAN
                =========================
                 */
                val lifecycleOwner = LocalLifecycleOwner.current
                // Écrans où il ne faut PAS forcer le PIN (auth / onboarding / le PIN lui-même).
                val authRoutes = remember {
                    setOf(
                        Routes.SPLASH, Routes.PIN_UNLOCK, Routes.WELCOME, Routes.FIRST_LAUNCH,
                        Routes.ONBOARDING, Routes.MNEMONIC_DISPLAY, Routes.MNEMONIC_VERIFY,
                        Routes.IMPORT_WALLET, Routes.PIN_SETUP, Routes.BIOMETRIC_SETUP, Routes.PANIC_PIN
                    )
                }
                /*
                BOUCLIER DE CONFIDENTIALITÉ
                Le verrouillage était décidé au retour au premier plan (ON_START) :
                l'image restaurée par le système était donc le DERNIER écran dessiné
                — le tableau de bord, soldes affichés — qui « flashait » avant le PIN.
                On couvre désormais l'app dès ON_PAUSE, tant qu'elle est encore
                visible : c'est le bouclier qui est mémorisé et restauré, et il ne
                tombe qu'une fois l'écran de PIN réellement dessiné.
                 */
                val shield = remember { mutableStateOf(false) }
                val reveal = remember { mutableStateOf(false) }

                DisposableEffect(lifecycleOwner) {
                    fun forceUnlock() {
                        sessionLock.lock()
                        navController.navigate(Routes.PIN_UNLOCK) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            // Encore au premier plan : on a le temps de dessiner
                            // le bouclier avant que l'app ne parte en arrière-plan.
                            Lifecycle.Event.ON_PAUSE -> {
                                val current = navController.currentDestination?.route
                                if (current != null && current !in authRoutes) shield.value = true
                            }
                            Lifecycle.Event.ON_STOP -> sessionLock.onEnterBackground()
                            // Le bouclier ne se retire pas ici mais après stabilisation
                            // de la navigation (LaunchedEffect ci-dessous).
                            Lifecycle.Event.ON_RESUME -> reveal.value = true
                            Lifecycle.Event.ON_START -> {
                                val current = navController.currentDestination?.route
                                when {
                                    // Auto-verrouillage après le délai passé en arrière-plan.
                                    sessionLock.shouldLockOnForeground() -> forceUnlock()
                                    // GARDE-FOU : session verrouillée mais l'app affiche un
                                    // écran protégé (retour arrière depuis le PIN, restauration
                                    // d'état de navigation, démarrage « chaud ») → on force le PIN.
                                    sessionLock.requiresUnlock() && current != null && current !in authRoutes -> forceUnlock()
                                    else -> {}
                                }
                            }
                            else -> {}
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                // Retrait du bouclier : on laisse passer deux images pour que la
                // navigation forcée vers le PIN (décidée en ON_START) soit non
                // seulement appliquée mais DESSINÉE. Si aucun verrouillage n'a eu
                // lieu, le délai (~30 ms) est imperceptible.
                LaunchedEffect(reveal.value) {
                    if (!reveal.value) return@LaunchedEffect
                    withFrameNanos { }
                    withFrameNanos { }
                    shield.value = false
                    reveal.value = false
                }

                Box(Modifier.fillMaxSize()) {
                    VaultExNavGraph(navController)
                    // Toasts maison (logo + texte) affichés par-dessus toute l'app.
                    com.vaultex.ui.components.ToastHost(toastController)
                    // Par-dessus TOUT, y compris les toasts.
                    if (shield.value) PrivacyShield()
                }
            }
        }
    }
}

/**
 * Écran opaque affiché pendant que l'app quitte / revient au premier plan.
 * Volontairement identique au démarrage (fond de l'app + logo) : l'utilisateur
 * voit une reprise nette, jamais ses montants.
 */
@Composable
private fun PrivacyShield() {
    Box(
        modifier = Modifier.fillMaxSize().background(BgPrimary),
        contentAlignment = Alignment.Center
    ) {
        com.vaultex.ui.components.VaultexWordmark(height = 40.dp)
    }
}

@Composable
private fun RootBlockedScreen() {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.GppBad,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                stringResource(R.string.error_root_detected),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
        }
    }
}
