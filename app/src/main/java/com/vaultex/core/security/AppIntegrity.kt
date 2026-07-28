package com.vaultex.core.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * Vérifie que l'application EST BIEN celle qui a été publiée, en comparant
 * l'empreinte de son propre certificat de signature à celle attendue.
 *
 * Pourquoi c'est le contrôle le plus important sur notre marché : ici, les
 * APK circulent par WhatsApp et Telegram, pas par le Play Store. Le scénario
 * concret est celui-ci — un attaquant télécharge l'APK, le décompile, ajoute
 * quelques lignes qui envoient la phrase de récupération vers son serveur, le
 * resigne avec SA clé, et le rediffuse dans les mêmes groupes. Même icône,
 * même nom, tout fonctionne normalement : l'utilisateur ne voit rien, et perd
 * ses fonds au premier dépôt.
 *
 * Un attaquant capable de lire ce code peut évidemment retirer le contrôle.
 * Ce n'est pas le but : l'objectif est d'éliminer le repackaging AUTOMATISÉ
 * — décompiler, injecter, resigner — qui représente l'écrasante majorité des
 * cas réels, et de rendre l'attaque assez coûteuse pour ne plus être rentable
 * à grande échelle.
 *
 * INACTIF tant qu'aucune empreinte n'est configurée (`app.signature.sha256`
 * dans local.properties). Les builds de développement et les débuts de projet
 * ne sont donc jamais bloqués.
 */
object AppIntegrity {

    /**
     * true si l'app tourne avec une signature INATTENDUE.
     *
     * Renvoie false — donc « rien à signaler » — quand aucune empreinte n'est
     * configurée, ou en cas d'erreur de lecture : un contrôle d'intégrité ne
     * doit jamais transformer un incident technique en portefeuille inaccessible.
     */
    fun isRepackaged(context: Context): Boolean {
        val expected = normalize(com.vaultex.BuildConfig.APP_SIGNATURE_SHA256)
        if (expected.isEmpty()) return false          // contrôle non configuré
        val actual = currentSignatureSha256(context) ?: return false
        return actual != expected
    }

    /** Empreinte SHA-256 du certificat courant, au format hexadécimal continu. */
    fun currentSignatureSha256(context: Context): String? = try {
        val pm = context.packageManager
        val certificates: Array<android.content.pm.Signature> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                @Suppress("DEPRECATION")
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signing = info.signingInfo
                when {
                    signing == null -> emptyArray()
                    // Un APK peut avoir subi une rotation de clé : on prend la
                    // liste complète et on retient la première correspondance.
                    signing.hasMultipleSigners() -> signing.apkContentsSigners
                    else -> signing.signingCertificateHistory
                }
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
                    ?: emptyArray()
            }
        val expected = normalize(com.vaultex.BuildConfig.APP_SIGNATURE_SHA256)
        val digests = certificates.map { sha256Hex(it.toByteArray()) }
        // S'il y a plusieurs certificats (rotation de clé), l'app est authentique
        // dès que L'UN d'eux correspond.
        digests.firstOrNull { it == expected } ?: digests.firstOrNull()
    } catch (_: Exception) { null }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02X".format(it) }

    /** `keytool` affiche `AA:BB:CC…` — on tolère les deux écritures. */
    private fun normalize(fingerprint: String): String =
        fingerprint.replace(":", "").replace(" ", "").trim().uppercase()
}
