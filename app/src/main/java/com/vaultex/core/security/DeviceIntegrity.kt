package com.vaultex.core.security

import android.content.Context
import android.util.Base64
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.scottyab.rootbeer.RootBeer
import com.vaultex.BuildConfig
import com.vaultex.core.monitoring.CrashReporter
import java.security.SecureRandom

/**
 * Intégrité de l'appareil — deux couches complémentaires :
 *
 *  1. [isDeviceRooted] : détection root locale et instantanée (RootBeer),
 *     renforcée par le check BusyBox en plus du isRooted() de base. Sert de
 *     garde bloquant hors-ligne (utilisé par MainActivity).
 *
 *  2. [requestIntegrityToken] : Play Integrity API (successeur de SafetyNet).
 *     Best-effort, asynchrone. Demande un jeton signé par Google attestant de
 *     l'intégrité de l'app et de l'appareil. Le verdict NE DOIT PAS être
 *     évalué côté client : le jeton est destiné à une vérification serveur.
 *     Désactivé tant que PLAY_INTEGRITY_PROJECT vaut 0 (numéro de projet
 *     Google Cloud à renseigner dans local.properties).
 */
object DeviceIntegrity {

    /** Détection root renforcée (RootBeer : isRooted + BusyBox). */
    fun isDeviceRooted(context: Context): Boolean {
        val rootBeer = RootBeer(context)
        return rootBeer.isRooted || rootBeer.isRootedWithBusyBoxCheck()
    }

    /**
     * Demande un jeton Play Integrity et le remonte (à terme) à un backend
     * pour vérification. Ici on se contente de journaliser le résultat dans
     * Crashlytics ; aucune décision de sécurité n'est prise côté client.
     */
    fun requestIntegrityToken(context: Context) {
        val projectNumber = BuildConfig.PLAY_INTEGRITY_PROJECT
        if (projectNumber == 0L) {
            CrashReporter.log("Play Integrity désactivé (PLAY_INTEGRITY_PROJECT non renseigné)")
            return
        }
        try {
            val manager = IntegrityManagerFactory.create(context.applicationContext)
            val request = IntegrityTokenRequest.builder()
                .setNonce(generateNonce())
                .setCloudProjectNumber(projectNumber)
                .build()
            manager.requestIntegrityToken(request)
                .addOnSuccessListener { _ ->
                    // TODO(backend) : transmettre response.token() à un serveur
                    // de confiance qui appelle l'API de décodage Play Integrity.
                    CrashReporter.setKey("play_integrity", "token_received")
                }
                .addOnFailureListener { e ->
                    CrashReporter.recordException(e, "Play Integrity : échec de la requête")
                }
        } catch (e: Throwable) {
            CrashReporter.recordException(e, "Play Integrity : exception à l'initialisation")
        }
    }

    private fun generateNonce(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
