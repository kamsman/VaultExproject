package com.vaultex.core.security

import android.content.Context
import java.io.File

/**
 * Détection d'instrumentation / hooking (Frida, Xposed, LSPosed, Substrate) —
 * 100 % côté client, sans dépendance native. Combine plusieurs heuristiques ;
 * chacune est protégée par try/catch pour ne jamais faire planter l'app.
 *
 * Aucune méthode n'est infaillible (un attaquant déterminé peut contourner),
 * mais l'ensemble élève nettement la barre face aux outils grand public.
 */
object HookDetector {

    // Classes injectées par Xposed/LSPosed dans le process.
    private val XPOSED_CLASSES = listOf(
        "de.robv.android.xposed.XposedBridge",
        "de.robv.android.xposed.XposedHelpers",
        "de.robv.android.xposed.IXposedHookLoadPackage",
        "de.robv.android.xposed.XC_MethodHook"
    )

    // Applications de hooking installées.
    private val HOOK_PACKAGES = listOf(
        "de.robv.android.xposed.installer",
        "org.lsposed.manager",
        "io.github.lsposed.manager",
        "io.va.exposed",
        "me.weishu.exp",
        "com.saurik.substrate"
    )

    // Signatures de bibliothèques visibles dans /proc/self/maps.
    private val MAPS_SIGNATURES = listOf(
        "frida", "gum-js-loop", "frida-agent", "frida-gadget",
        "libsubstrate", "lsposed", "libxposed"
    )

    /** Vrai si une instrumentation/hooking est détectée. */
    fun isHookingDetected(context: Context): Boolean =
        hasXposedClasses() ||
            isXposedInStackTrace() ||
            hasHookArtifactsInMaps() ||
            hasHookApps(context)

    /** 1) Présence des classes Xposed/LSPosed (chargées dans le process). */
    private fun hasXposedClasses(): Boolean = XPOSED_CLASSES.any { cls ->
        try {
            Class.forName(cls)
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** 2) Frames Xposed/Substrate dans une pile d'appel provoquée. */
    private fun isXposedInStackTrace(): Boolean = try {
        Throwable().stackTrace.any {
            it.className.startsWith("de.robv.android.xposed") ||
                it.className.startsWith("com.saurik.substrate")
        }
    } catch (_: Throwable) {
        false
    }

    /** 3) Bibliothèques frida/substrate/xposed mappées dans le process. */
    private fun hasHookArtifactsInMaps(): Boolean = try {
        File("/proc/self/maps").bufferedReader().useLines { lines ->
            lines.any { line ->
                val l = line.lowercase()
                MAPS_SIGNATURES.any { sig -> l.contains(sig) }
            }
        }
    } catch (_: Throwable) {
        false
    }

    /** 4) Applications de hooking installées sur l'appareil. */
    private fun hasHookApps(context: Context): Boolean = try {
        val pm = context.packageManager
        HOOK_PACKAGES.any { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (_: Throwable) {
                false
            }
        }
    } catch (_: Throwable) {
        false
    }
}
