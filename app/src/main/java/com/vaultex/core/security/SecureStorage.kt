package com.vaultex.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stockage sécurisé pour mnemonique chiffrée + métadonnées wallet.
 * Combine EncryptedSharedPreferences (Jetpack) + KeystoreManager personnalisé.
 *
 * Double couche de chiffrement :
 * 1. La mnémonique est chiffrée avec KeystoreManager (AES-256-GCM en TEE)
 * 2. Le ciphertext résultant est stocké dans EncryptedSharedPreferences (chiffrement supplémentaire)
 */
@Singleton
class SecureStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keystoreManager: KeystoreManager
) {

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "vaultex_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * Sauvegarde la mnémonique en double-chiffrement.
     */
    // ─────────────────────────────────────────────────────────────────────
    // MULTI-WALLET : chaque wallet a son propre seed chiffré, indexé par id.
    // L'API historique (saveMnemonic/getMnemonic/getPassphrase/hasMnemonic)
    // opère TOUJOURS sur le wallet ACTIF → tout le code aval (dérivation, envoi,
    // swap, soldes) suit le wallet actif sans modification.
    // ─────────────────────────────────────────────────────────────────────
    private fun enc(s: String): String =
        android.util.Base64.encodeToString(keystoreManager.encrypt(s.toByteArray(Charsets.UTF_8)), android.util.Base64.NO_WRAP)
    /**
     * Déchiffre une valeur du magasin.
     *
     * `null` signifie DEUX choses très différentes : « rien n'est enregistré »
     * (b64 nul, cas normal) et « quelque chose est enregistré mais n'a pas pu
     * être déchiffré » (cas anormal). Le second était traité en silence,
     * exactement comme le premier.
     *
     * Or l'appelant en tire une conclusion lourde : `getMnemonic()` renvoie
     * null, donc l'application conclut qu'aucun portefeuille n'existe et
     * affiche l'accueil de création. Pour l'utilisateur, son portefeuille a
     * disparu. Les fonds sont intacts — le seed est sur sa feuille de papier —
     * mais rien ne le lui dit, et rien ne nous permettait de savoir que c'était
     * arrivé.
     *
     * Les causes réelles existent : clé Keystore invalidée par l'ajout d'une
     * empreinte digitale, migration d'appareil, corruption du magasin. On ne
     * peut pas récupérer la donnée, mais on doit au moins SAVOIR. L'incident
     * part au diagnostic administrateur, sans jamais transporter la donnée
     * elle-même.
     */
    private fun dec(b64: String?): String? {
        if (b64 == null) return null
        return try {
            String(keystoreManager.decrypt(android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (e: Exception) {
            com.vaultex.core.monitoring.AdminBot.serviceFailed(
                "dechiffrement du magasin securise",
                e.javaClass.simpleName   // JAMAIS le message : il pourrait porter la donnée
            )
            null
        }
    }
    private fun mnKey(id: String) = "mnemonic_$id"
    private fun psKey(id: String) = "passphrase_$id"

    fun activeWalletId(): String? = prefs.getString(KEY_ACTIVE_WALLET, null)
    fun setActiveWalletId(id: String) { prefs.edit().putString(KEY_ACTIVE_WALLET, id).apply() }

    /** Enregistre le seed (+ passphrase) d'UN wallet donné, sans toucher aux autres. */
    fun saveWalletSecrets(walletId: String, mnemonic: String, passphrase: String) {
        val e = prefs.edit()
        e.putString(mnKey(walletId), enc(mnemonic))
        if (passphrase.isEmpty()) e.remove(psKey(walletId)) else e.putString(psKey(walletId), enc(passphrase))
        e.apply()
    }
    fun getMnemonicFor(walletId: String): String? = dec(prefs.getString(mnKey(walletId), null))
    fun getPassphraseFor(walletId: String): String = dec(prefs.getString(psKey(walletId), null)) ?: ""
    fun hasWalletSecrets(walletId: String): Boolean = prefs.contains(mnKey(walletId))

    /**
     * Identifiants de TOUS les wallets dont un seed est stocké ici.
     *
     * POURQUOI C'EST NÉCESSAIRE. Les seeds vivent ici, mais la LISTE des
     * wallets ne vivait que dans la table Room. Or la base est construite avec
     * `fallbackToDestructiveMigration()` : elle est effacée a chaque montée de
     * version du schéma.
     *
     * Sans cette fonction, le jour où une colonne est ajoutée, tout
     * utilisateur ayant plusieurs wallets voyait les autres DISPARAÎTRE. Leurs
     * phrases secrètes survivaient ici, intactes — mais l'application n'avait
     * plus aucun moyen de les retrouver, faute de savoir qu'elles existaient.
     *
     * Le stockage chiffré redevient ainsi la source de vérité, et la base une
     * simple copie de travail reconstructible.
     */
    fun storedWalletIds(): List<String> = try {
        prefs.all.keys
            .filter { it.startsWith("mnemonic_") }
            .map { it.removePrefix("mnemonic_") }
            .filter { it.isNotBlank() }
            .sorted()
    } catch (_: Exception) { emptyList() }
    fun deleteWalletSecrets(walletId: String) {
        val e = prefs.edit().remove(mnKey(walletId)).remove(psKey(walletId))
        // Le wallet migré garde une copie sous les anciennes clés (héritage
        // pré-multi-wallets) : on l'efface aussi, sinon son seed resterait sur
        // l'appareil après suppression — et pourrait être re-migré par erreur.
        if (walletId == LEGACY_WALLET_ID) e.remove(KEY_MNEMONIC).remove(KEY_PASSPHRASE)
        e.apply()
    }

    /**
     * Renvoie l'id du wallet actif, en migrant AU BESOIN l'ancien wallet unique
     * (KEY_MNEMONIC) vers le stockage multi-wallet. Idempotent.
     */
    @Synchronized
    fun ensureActiveWallet(): String? {
        activeWalletId()?.let { if (hasWalletSecrets(it)) return it }
        val legacy = prefs.getString(KEY_MNEMONIC, null)
        if (legacy != null) {
            val id = LEGACY_WALLET_ID
            val e = prefs.edit()
            e.putString(mnKey(id), legacy)                                  // déjà chiffré : déplacé tel quel
            prefs.getString(KEY_PASSPHRASE, null)?.let { e.putString(psKey(id), it) }
            e.putString(KEY_ACTIVE_WALLET, id)
            e.apply()
            return id
        }
        return activeWalletId()?.takeIf { hasWalletSecrets(it) }
    }

    /** Écrit dans le wallet ACTIF (crée un id par défaut s'il n'y en a pas encore). */
    fun saveMnemonic(mnemonic: String) {
        val id = activeWalletId() ?: LEGACY_WALLET_ID.also { setActiveWalletId(it) }
        prefs.edit().putString(mnKey(id), enc(mnemonic)).apply()
    }

    /** Mnémonique déchiffrée du wallet ACTIF. */
    fun getMnemonic(): String? {
        val id = ensureActiveWallet() ?: return null
        return getMnemonicFor(id)
    }

    fun hasMnemonic(): Boolean {
        val id = ensureActiveWallet() ?: return false
        return hasWalletSecrets(id)
    }

    /**
     * Passphrase BIP39 optionnelle (« 13e mot ») du wallet ACTIF.
     */
    fun savePassphrase(passphrase: String) {
        val id = activeWalletId() ?: LEGACY_WALLET_ID.also { setActiveWalletId(it) }
        if (passphrase.isEmpty()) { prefs.edit().remove(psKey(id)).apply(); return }
        prefs.edit().putString(psKey(id), enc(passphrase)).apply()
    }

    fun getPassphrase(): String {
        val id = ensureActiveWallet() ?: return ""
        return getPassphraseFor(id)
    }

    fun savePin(pinHash: String) {
        prefs.edit().putString(KEY_PIN_HASH, pinHash).apply()
    }

    fun getPinHash(): String? = prefs.getString(KEY_PIN_HASH, null)

    fun savePanicPin(pinHash: String) {
        prefs.edit().putString(KEY_PANIC_PIN_HASH, pinHash).apply()
    }

    fun getPanicPinHash(): String? = prefs.getString(KEY_PANIC_PIN_HASH, null)

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun isBiometricEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)

    fun setAutoLockMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_AUTOLOCK_MIN, minutes).apply()
    }

    fun getAutoLockMinutes(): Int = prefs.getInt(KEY_AUTOLOCK_MIN, 5)

    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
    }

    fun getThemeMode(): String = prefs.getString(KEY_THEME_MODE, "SYSTEM") ?: "SYSTEM"

    /** Nom d'affichage du wallet, modifiable par l'utilisateur (vide = défaut). */
    fun getWalletName(): String = prefs.getString(KEY_WALLET_NAME, "") ?: ""

    fun saveWalletName(name: String) {
        prefs.edit().putString(KEY_WALLET_NAME, name.trim()).apply()
    }

    /*
    ─── CODE ANTI-PHISHING ────────────────────────────────────────────────
    Mot personnel choisi par l'utilisateur, affiché par l'application aux
    moments les plus à risque (écran de déverrouillage, saisie de la phrase
    de récupération).

    À quoi ça sert ICI, concrètement : une contrefaçon de VaultEx installée
    depuis WhatsApp est forcément une INSTALLATION NEUVE — Android refuse
    d'installer par-dessus une application signée avec une autre clé. Elle
    part donc d'un stockage vide et ne peut PAS connaître ce mot. Si
    l'utilisateur ne voit pas son code, c'est un faux.

    C'est le complément exact du contrôle de signature : celui-ci peut être
    retiré par un attaquant qui modifie le code, alors qu'un mot qu'il ignore
    ne peut pas être deviné. Les deux se couvrent mutuellement.

    Stocké dans les préférences CHIFFRÉES, comme le reste.
    ───────────────────────────────────────────────────────────────────────
     */
    fun getAntiPhishingCode(): String = prefs.getString(KEY_ANTIPHISHING, "") ?: ""

    fun saveAntiPhishingCode(code: String) {
        prefs.edit().putString(KEY_ANTIPHISHING, code.trim()).apply()
    }

    fun clearAntiPhishingCode() {
        prefs.edit().remove(KEY_ANTIPHISHING).apply()
    }

    /** Devise d'affichage du wallet : USD, EUR ou XOF (défaut USD). */
    fun getCurrency(): String = prefs.getString(KEY_CURRENCY, "USD") ?: "USD"

    fun setCurrency(code: String) {
        prefs.edit().putString(KEY_CURRENCY, code).apply()
    }

    /** Monnaies activées (visibles) dans « Mes actifs ». Défaut : les principales. */
    fun getVisibleAssets(): Set<String> {
        val csv = prefs.getString(KEY_VISIBLE_ASSETS, null)
        return if (csv == null) DEFAULT_VISIBLE_ASSETS
        else csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun setVisibleAssets(assets: Set<String>) {
        prefs.edit().putString(KEY_VISIBLE_ASSETS, assets.joinToString(",")).apply()
    }

    /**
     * Clé de chiffrement de la base SQLCipher (C-03).
     * Générée aléatoirement une fois, stockée dans les prefs chiffrées
     * (protégées par le Keystore). Retourne une copie (SQLCipher efface
     * le tableau après usage).
     */
    fun getOrCreateDatabaseKey(): ByteArray {
        val existing = prefs.getString(KEY_DB_KEY, null)
        if (existing != null) {
            return android.util.Base64.decode(existing, android.util.Base64.NO_WRAP)
        }
        val key = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_DB_KEY, android.util.Base64.encodeToString(key, android.util.Base64.NO_WRAP))
            .apply()
        return key.copyOf()
    }

    fun setBalanceHidden(hidden: Boolean) {
        prefs.edit().putBoolean(KEY_BALANCE_HIDDEN, hidden).apply()
    }

    fun isBalanceHidden(): Boolean = prefs.getBoolean(KEY_BALANCE_HIDDEN, false)

    /** Cache du dernier portefeuille synchronisé (offline-first, #5). */
    fun savePortfolioSnapshot(json: String) {
        prefs.edit().putString(KEY_PORTFOLIO_SNAPSHOT, json).apply()
    }

    fun getPortfolioSnapshot(): String? = prefs.getString(KEY_PORTFOLIO_SNAPSHOT, null)

    /** Transactions sortantes en attente de confirmation (JSON). */
    fun savePendingTxs(json: String) {
        prefs.edit().putString(KEY_PENDING_TXS, json).apply()
    }

    fun getPendingTxs(): String? = prefs.getString(KEY_PENDING_TXS, null)

    /**
     * Vide les caches PROPRES à un wallet (solde, tx en attente, actifs visibles)
     * — appelé lors d'un changement de wallet actif pour que le nouveau wallet
     * n'affiche jamais les montants de l'ancien.
     */
    fun clearWalletCaches() {
        prefs.edit()
            .remove(KEY_PORTFOLIO_SNAPSHOT)
            .remove(KEY_PENDING_TXS)
            .remove(KEY_VISIBLE_ASSETS)
            .apply()
    }

    // ──────────────────────────────────────────────────────────
    // Persistance de l'état de lockout PIN (survit aux relances de process)
    // ──────────────────────────────────────────────────────────

    fun saveFailedPinAttempts(count: Int) {
        prefs.edit().putInt(KEY_PIN_FAILED_ATTEMPTS, count).apply()
    }

    fun getFailedPinAttempts(): Int = prefs.getInt(KEY_PIN_FAILED_ATTEMPTS, 0)

    fun savePinLockedUntil(timestamp: Long) {
        prefs.edit().putLong(KEY_PIN_LOCKED_UNTIL, timestamp).apply()
    }

    fun getPinLockedUntil(): Long = prefs.getLong(KEY_PIN_LOCKED_UNTIL, 0L)

    fun clearPinLockout() {
        prefs.edit()
            .putInt(KEY_PIN_FAILED_ATTEMPTS, 0)
            .putLong(KEY_PIN_LOCKED_UNTIL, 0L)
            .apply()
    }

    /**
     * RESET COMPLET — utilisé par le PIN de panique.
     * Efface mnémonique + PIN + détruit la master key.
     */
    fun nukeAllData() {
        prefs.edit().clear().apply()
        rpcPrefs.edit().clear().apply()
        keystoreManager.destroyMasterKey()
        /*
        EFFACEMENT DE **TOUTES** LES PRÉFÉRENCES DE L'APPLICATION.

        Seuls deux magasins étaient vidés ici. Une dizaine d'autres survivaient
        — dont le centre de notifications, qui conserve des lignes explicites du
        type « Vous avez reçu 0,5 BTC », ainsi que les soldes de référence du
        détecteur de dépôts.

        C'est acceptable pour un simple « Effacer le wallet ». Ça ne l'est pas
        du tout pour le PIN PANIQUE, dont c'est la RAISON D'ÊTRE : sous
        contrainte, l'appareil doit paraître vierge. Un agresseur qui ouvre la
        cloche et y lit l'historique des montants reçus comprend immédiatement
        que le portefeuille a été effacé devant lui — et que l'utilisateur ment.
        La fonction censée le protéger le mettait en danger.

        On énumère donc le dossier des préférences plutôt que de maintenir une
        liste en dur, qui aurait pris du retard au premier magasin ajouté.
         */
        try {
            val dir = java.io.File(context.applicationInfo.dataDir, "shared_prefs")
            dir.listFiles()?.forEach { file ->
                val name = file.name.removeSuffix(".xml")
                // clear() plutôt que la suppression du fichier : les instances
                // déjà chargées en mémoire restent cohérentes avec le disque.
                context.getSharedPreferences(name, Context.MODE_PRIVATE)
                    .edit().clear().commit()
            }
        } catch (_: Exception) { }
        /*
        JETON DE NOTIFICATION — le trou le plus grave du PIN panique.

        Le serveur associe le jeton FCM de l'appareil aux ADRESSES du
        portefeuille. Ce jeton ne change pas quand on efface les préférences :
        après un effacement sous contrainte, le serveur continuait donc
        d'envoyer les notifications de l'ANCIEN portefeuille. Concrètement,
        quelques minutes après avoir « tout effacé » devant un agresseur, une
        bannière « Vous avez reçu 0,5 BTC » pouvait s'afficher sur un téléphone
        censé être vierge.

        Supprimer le jeton coupe le lien : le serveur ne sait plus où écrire, et
        l'appareil s'en verra attribuer un neuf, sans passé.

        Sur un thread séparé : l'opération est réseau, et rien ne doit retarder
        un effacement d'urgence.
         */
        try {
            Thread {
                runCatching {
                    com.google.firebase.messaging.FirebaseMessaging.getInstance().deleteToken()
                }
            }.start()
        } catch (_: Exception) { }

        /*
        Travaux planifiés : sans annulation, les workers de détection de dépôt
        et d'alertes de prix restaient programmés après l'effacement.
         */
        try {
            androidx.work.WorkManager.getInstance(context).cancelAllWork()
        } catch (_: Exception) { }

        /*
        Fichiers et caches : logos de monnaies téléchargés, fichiers
        temporaires… Aucun secret, mais autant de traces d'un usage passé sur un
        appareil qui doit paraître neuf.
         */
        try {
            context.cacheDir?.deleteRecursively()
            context.filesDir?.listFiles()?.forEach { it.deleteRecursively() }
        } catch (_: Exception) { }

        // P2 : effacer aussi la base chiffrée (historique, contacts, alertes…)
        context.deleteDatabase("vaultex.db")
        // Flag de routage « wallet créé » : sans cet effacement, l'app
        // redemanderait au prochain démarrage un PIN… qui n'existe plus
        // (écran de déverrouillage infranchissable au lieu de l'accueil).
        try { com.vaultex.core.session.AppLaunchManager.setWalletCreated(context, false) } catch (_: Exception) { }
    }

    private val rpcPrefs: SharedPreferences by lazy { RpcPrefs.get(context) }

    /** @return false si l'URL est rejetée (non HTTPS). */
    fun setRpcUrl(chain: String, url: String): Boolean {
        if (!RpcPrefs.isValidRpcUrl(url)) return false
        rpcPrefs.edit().putString("rpc_$chain", url).apply()
        return true
    }

    fun getRpcUrl(chain: String, default: String): String =
        rpcPrefs.getString("rpc_$chain", default) ?: default

    companion object {
        private const val KEY_MNEMONIC = "encrypted_mnemonic"        // legacy (wallet unique)
        private const val KEY_PASSPHRASE = "encrypted_passphrase"    // legacy
        private const val KEY_ACTIVE_WALLET = "active_wallet_id"
        const val LEGACY_WALLET_ID = "w_1"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PANIC_PIN_HASH = "panic_pin_hash"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_AUTOLOCK_MIN = "autolock_minutes"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_BALANCE_HIDDEN = "balance_hidden"
        private const val KEY_DB_KEY = "db_encryption_key"
        private const val KEY_PORTFOLIO_SNAPSHOT = "portfolio_snapshot"
        private const val KEY_PENDING_TXS = "pending_txs"
        private const val KEY_PIN_FAILED_ATTEMPTS = "pin_failed_attempts"
        private const val KEY_PIN_LOCKED_UNTIL = "pin_locked_until"
        private const val KEY_WALLET_NAME = "wallet_display_name"
        private const val KEY_ANTIPHISHING = "anti_phishing_code"
        private const val KEY_CURRENCY = "display_currency"
        private const val KEY_VISIBLE_ASSETS = "visible_assets"
        val DEFAULT_VISIBLE_ASSETS = setOf("BTC", "ETH", "BNB", "SOL", "TRX", "USDT")
    }
}
