package com.vaultex.service

import com.vaultex.core.crypto.WalletManager
import com.vaultex.core.security.SecureStorage
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enregistre le jeton FCM de l'appareil + les adresses de réception auprès de la
 * Cloud Function `registerDevice`. Le serveur s'en sert pour envoyer un push
 * « Fonds reçus » quand un solde augmente.
 *
 * L'enregistrement se rejoue dès que le COUPLE (jeton, adresses) change — et
 * non plus une seule fois par lancement. C'était un angle mort sérieux : après
 * l'ajout ou le changement de portefeuille, le serveur continuait de surveiller
 * les ANCIENNES adresses, et les dépôts sur le nouveau portefeuille
 * n'engendraient plus aucune notification.
 *
 * Sans Cloud Function déployée, l'appel échoue silencieusement (aucun impact).
 */
@Singleton
class PushRegistrar @Inject constructor(
    private val secureStorage: SecureStorage,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {
    private val prefs =
        context.getSharedPreferences("vaultex_push", android.content.Context.MODE_PRIVATE)

    private val client = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    /** À appeler depuis un contexte IO (bloquant, réseau + dérivation d'adresses). */
    fun registerBlocking() = register(force = false)

    /** Réenregistrement imposé — jeton renouvelé par Firebase (onNewToken). */
    fun forceRegisterBlocking() = register(force = true)

    private fun register(force: Boolean) {
        try {
            val mnemonic = secureStorage.getMnemonic() ?: return
            val addr = WalletManager.deriveAddresses(mnemonic, secureStorage.getPassphrase())
            val token = com.google.android.gms.tasks.Tasks.await(
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            ) ?: return
            if (token.isBlank()) return

            val json = """
                {"token":"$token","addresses":{"btc":"${addr.btc}","eth":"${addr.eth}","bnb":"${addr.bnb}","sol":"${addr.sol}","trx":"${addr.trx}"}}
            """.trimIndent()

            // Empreinte de ce qui a été envoyé : tant qu'elle ne bouge pas,
            // inutile de rappeler le serveur à chaque ouverture de l'app.
            val fingerprint = (token + "|" + addr.btc + addr.eth + addr.bnb + addr.sol + addr.trx)
                .hashCode().toString()
            if (!force && prefs.getString(KEY_SENT, null) == fingerprint) return

            val req = Request.Builder()
                .url(REGISTER_URL)
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { response ->
                // On ne mémorise QUE sur succès : un échec réseau doit être
                // rejoué au prochain lancement, sinon l'appareil resterait
                // inconnu du serveur pour toujours.
                if (response.isSuccessful) {
                    prefs.edit().putString(KEY_SENT, fingerprint).apply()
                }
            }
        } catch (_: Exception) {
            // Serveur non déployé / hors-ligne : sans effet sur l'app.
        }
    }

    companion object {
        private const val KEY_SENT = "registered_fingerprint"

        // Région par défaut des Cloud Functions : us-central1.
        private const val REGISTER_URL =
            "https://us-central1-vaultex-1fe79.cloudfunctions.net/registerDevice"
    }
}
