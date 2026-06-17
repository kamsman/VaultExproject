package com.vaultex.core.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.vaultex.R

/**
 * Gestion de l'authentification biométrique avec BiometricPrompt.
 * Niveau requis : BIOMETRIC_STRONG (Class 3) pour usage avec Keystore.
 */
class BiometricHelper(private val activity: FragmentActivity) {

    enum class BiometricStatus {
        AVAILABLE,
        NO_HARDWARE,
        UNAVAILABLE,
        NONE_ENROLLED,
        SECURITY_UPDATE_REQUIRED
    }

    fun checkAvailability(): BiometricStatus {
        val biometricManager = BiometricManager.from(activity)
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus.UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NONE_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricStatus.SECURITY_UPDATE_REQUIRED
            else -> BiometricStatus.UNAVAILABLE
        }
    }

    /**
     * Vrai si l'appareil possède un verrouillage sécurisé (PIN/schéma/mot
     * de passe), utilisable comme repli quand la biométrie n'est pas
     * enrôlée. Permet de rendre la ré-authentification obligatoire (m-05).
     */
    fun canUseDeviceCredential(): Boolean =
        BiometricManager.from(activity)
            .canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Affiche le prompt biométrique.
     * @param title Titre du dialog
     * @param subtitle Description
     * @param onSuccess Callback succès
     * @param onError Callback erreur (incluant cancel utilisateur)
     */
    fun authenticate(
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (errorCode: Int, errorMessage: String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errorCode, errString.toString())
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(activity.getString(R.string.cancel))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setConfirmationRequired(true)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    /**
     * Ré-authentification OBLIGATOIRE avant une opération sensible (m-05).
     * Utilise la biométrie si elle est enrôlée, sinon le code de
     * verrouillage de l'appareil (PIN/schéma/mot de passe). À n'appeler que
     * si [checkAvailability] == AVAILABLE ou [canUseDeviceCredential] == true.
     */
    fun authenticateStrongOrCredential(
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (errorCode: Int, errorMessage: String) -> Unit
    ) {
        val biometricAvailable = checkAvailability() == BiometricStatus.AVAILABLE
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errorCode, errString.toString())
                }
            }
        )

        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setConfirmationRequired(true)

        if (biometricAvailable) {
            // BIOMETRIC_STRONG impose un bouton négatif explicite.
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText(activity.getString(R.string.cancel))
        } else {
            // DEVICE_CREDENTIAL : pas de bouton négatif autorisé par l'API.
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        }

        biometricPrompt.authenticate(builder.build())
    }
}
