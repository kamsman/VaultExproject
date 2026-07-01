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
 * Sans configuration serveur déployée, l'appel échoue silencieusement (aucun
 * impact sur l'app). L'enregistrement se fait une fois par lancement.
 */
@Singleton
class PushRegistrar @Inject constructor(
    private val secureStorage: SecureStorage
) {
    @Volatile private var done = false

    private val client = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    /** À appeler depuis un contexte IO (bloquant, réseau + dérivation d'adresses). */
    fun registerBlocking() {
        if (done) return
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

            val req = Request.Builder()
                .url(REGISTER_URL)
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { /* réponse ignorée */ }
            done = true
        } catch (_: Exception) {
            // Serveur non déployé / hors-ligne : sans effet sur l'app.
        }
    }

    companion object {
        // Région par défaut des Cloud Functions : us-central1.
        private const val REGISTER_URL =
            "https://us-central1-vaultex-1fe79.cloudfunctions.net/registerDevice"
    }
}
