package com.vaultex.core.security

import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestion sécurisée des clés via Android Keystore (TEE / StrongBox si disponible).
 * Utilise AES-256-GCM pour le chiffrement authentifié de la mnémonique.
 *
 * Les clés ne quittent JAMAIS le hardware sécurisé.
 */
@Singleton
class KeystoreManager @Inject constructor() {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "VaultExMasterKey"
        private const val GCM_TAG_LENGTH = 128  // bits
        private const val GCM_IV_LENGTH = 12    // bytes
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /**
     * Génère ou récupère la master key dans le Keystore.
     * Si l'appareil supporte StrongBox (Pixel 3+, Samsung S20+), elle y est stockée.
     */
    private fun getOrCreateMasterKey(requireBiometric: Boolean = false): SecretKey {
        val existingKey = keyStore.getKey(MASTER_KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) return existingKey

        /*
        ═══════════════════════════════════════════════════════════════════
        STRONGBOX : TENTER, PUIS RETOMBER SUR LE TEE
        ═══════════════════════════════════════════════════════════════════

        StrongBox est une puce de sécurité dédiée. Elle n'existe que sur une
        minorité d'appareils — Pixel 3+, Samsung haut de gamme. La plupart des
        téléphones de milieu et d'entrée de gamme, Tecno, Infinix et itel en
        tête, n'en ont pas.

        LE BUG QUE CECI CORRIGE. La version précédente entourait
        `setIsStrongBoxBacked(true)` d'un try/catch. Or cet appel ne fait que
        poser un drapeau sur le constructeur : il ne lève JAMAIS d'exception.
        C'est `generateKey()` qui lève `StrongBoxUnavailableException`, et il
        était en dehors du try.

        Sur tout appareil sans StrongBox, la création de la clé maîtresse
        échouait donc, et l'utilisateur voyait une erreur au moment de valider
        son code PIN. Impossible de créer le moindre portefeuille. Le bug
        était invisible en développement sur un appareil haut de gamme, et
        systématique sur le marché visé.

        Le repli sur le TEE n'est pas un pis-aller : le TEE est l'enclave
        sécurisée présente sur tout Android moderne, et c'est ce qu'utilisent
        la quasi-totalité des applications bancaires.
        ═══════════════════════════════════════════════════════════════════
         */
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            try {
                return generateMasterKey(requireBiometric, strongBox = true)
            } catch (_: Exception) {
                // StrongBoxUnavailableException, ou un refus propre au
                // constructeur. Une tentative echouee peut laisser un alias
                // partiel : on le supprime avant de reessayer, sinon la
                // seconde tentative echouerait a son tour.
                runCatching { keyStore.deleteEntry(MASTER_KEY_ALIAS) }
            }
        }
        return generateMasterKey(requireBiometric, strongBox = false)
    }

    /** Crée la clé maîtresse, avec ou sans StrongBox. */
    private fun generateMasterKey(requireBiometric: Boolean, strongBox: Boolean): SecretKey {
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)

        // Auth biométrique pour l'usage de la clé (données sensibles).
        if (requireBiometric) {
            builder.setUserAuthenticationRequired(true)
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            // P4 : invalide la clé si une nouvelle empreinte est enrôlée
            // (empêche un attaquant ayant ajouté sa biométrie de l'utiliser).
            builder.setInvalidatedByBiometricEnrollment(true)
        }

        if (strongBox && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true)
        }

        keyGen.init(builder.build())
        return keyGen.generateKey()
    }

    /**
     * Chiffre des données avec AES-256-GCM.
     * Format de retour: [12 bytes IV][ciphertext + tag GCM]
     */
    fun encrypt(plaintext: ByteArray, requireBiometric: Boolean = false): ByteArray {
        val key = getOrCreateMasterKey(requireBiometric)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val iv = cipher.iv  // 12 bytes générés automatiquement
        val ciphertext = cipher.doFinal(plaintext)

        return iv + ciphertext
    }

    /**
     * Déchiffre des données AES-256-GCM.
     */
    fun decrypt(encrypted: ByteArray): ByteArray {
        require(encrypted.size > GCM_IV_LENGTH) { "Données chiffrées trop courtes" }
        val key = getOrCreateMasterKey()

        val iv = encrypted.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = encrypted.copyOfRange(GCM_IV_LENGTH, encrypted.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        return cipher.doFinal(ciphertext)
    }

    /**
     * Détruit la master key — utilisé pour le reset complet du wallet (ex: PIN de panique).
     */
    fun destroyMasterKey() {
        if (keyStore.containsAlias(MASTER_KEY_ALIAS)) {
            keyStore.deleteEntry(MASTER_KEY_ALIAS)
        }
        /*
        SECONDE CLÉ, tout aussi importante.

        Le double chiffrement du seed repose sur DEUX clés : celle-ci
        (VaultExMasterKey) et celle qui protège les préférences chiffrées,
        créée par MasterKey.Builder sous son alias PAR DÉFAUT. Seule la
        première était supprimée : après un effacement — y compris un PIN
        panique — la clé des préférences restait dans le Keystore Android.

        Ce n'est pas une fuite en soi, les fichiers ayant été vidés. Mais une
        clé qui subsiste est une trace qui subsiste, et l'objectif du PIN
        panique est justement qu'il n'en reste aucune. On supprime les deux.

        On supprime la clé PAR SON ALIAS PAR DÉFAUT plutôt que de définir un
        alias personnalisé : changer l'alias sur une application déjà
        installée rendrait les préférences existantes — donc les seeds —
        DÉFINITIVEMENT indéchiffrables. Ce serait une perte de fonds.
         */
        try {
            val prefsAlias = androidx.security.crypto.MasterKey.DEFAULT_MASTER_KEY_ALIAS
            if (keyStore.containsAlias(prefsAlias)) {
                keyStore.deleteEntry(prefsAlias)
            }
        } catch (_: Exception) { }
    }

    /** Détection réelle du module hardware StrongBox (m-03). */
    fun isStrongBoxAvailable(context: android.content.Context): Boolean {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
    }
}
