package com.vaultex.core.session

import com.vaultex.core.security.SecureStorage
import com.vaultex.data.local.dao.PendingSendDao
import com.vaultex.data.local.dao.TransactionDao
import com.vaultex.data.local.dao.WalletDao
import com.vaultex.data.local.entity.WalletEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinateur MULTI-WALLETS — la seule porte d'entrée pour créer, activer et
 * supprimer un wallet. Il garantit les invariants fund-critiques :
 *
 *  1. Chaque wallet garde SON seed chiffré (mnemonic_<id>) : créer un nouveau
 *     wallet n'écrase JAMAIS le seed d'un autre (l'ancien bug faisait
 *     exactement ça via l'emplacement unique KEY_MNEMONIC).
 *  2. Tout changement de wallet actif PURGE les caches financiers (instantané
 *     de solde, transactions locales, envois en attente) : le nouveau wallet
 *     n'affiche jamais les montants de l'ancien, et un envoi en file ne peut
 *     pas être signé avec la mauvaise clé.
 *  3. L'ancien wallet unique (avant multi-wallets) est migré et INSCRIT dans
 *     la liste — il reste sélectionnable, son seed n'est pas perdu.
 *
 * L'API historique SecureStorage.getMnemonic()/getPassphrase() lit toujours le
 * wallet ACTIF : la dérivation d'adresses, l'envoi, le swap et les soldes
 * suivent automatiquement, sans modification du code aval.
 */
@Singleton
class WalletStore @Inject constructor(
    private val secureStorage: SecureStorage,
    private val walletDao: WalletDao,
    private val transactionDao: TransactionDao,
    private val pendingSendDao: PendingSendDao,
    private val notificationCenter: NotificationCenter,
    private val notifPrefs: NotifPrefs,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
) {

    /**
     * Migration : si un wallet actif existe (ancien wallet unique inclus) mais
     * n'apparaît pas encore dans la table `wallets`, on l'y inscrit. Idempotent.
     */
    /**
     * Réaligne la table des wallets sur le stockage chiffré, qui fait foi.
     *
     * La base est construite avec `fallbackToDestructiveMigration()` : elle est
     * EFFACÉE a chaque montée de version du schéma. Avant cette reconstruction,
     * un utilisateur ayant plusieurs wallets les voyait alors disparaître —
     * leurs seeds restaient pourtant intacts dans le stockage chiffré, mais
     * plus rien ne signalait leur existence.
     *
     * On repart donc de `storedWalletIds()` : tout seed présent redevient un
     * wallet visible. Les noms d'origine, eux, vivaient dans la base et sont
     * perdus — on renomme « Wallet N » plutôt que de laisser un portefeuille
     * inaccessible.
     */
    suspend fun ensureRegistered() {
        val actif = secureStorage.ensureActiveWallet()
        val connus = secureStorage.storedWalletIds()
            .ifEmpty { listOfNotNull(actif) }
        if (connus.isEmpty()) return

        var n = walletDao.count()
        for (id in connus) {
            if (walletDao.getById(id) != null) continue
            n += 1
            walletDao.insert(
                WalletEntity(
                    id = id,
                    name = if (id == actif) secureStorage.getWalletName().ifBlank { "Wallet $n" }
                           else "Wallet $n",
                    isActive = false,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
        // Un seul wallet actif, et il doit correspondre au seed reellement charge.
        val cible = actif ?: connus.first()
        if (walletDao.getById(cible) != null) {
            walletDao.deactivateAll()
            walletDao.activate(cible)
        }
    }

    /**
     * Ajoute un wallet (création ou import) et l'ACTIVE. Le seed est stocké sous
     * son propre id — les seeds des autres wallets restent intacts.
     */
    suspend fun addWallet(mnemonic: String, passphrase: String, imported: Boolean): WalletEntity {
        ensureRegistered()   // l'ancien wallet doit être listé AVANT d'ajouter le nouveau
        // Id garanti UNIQUE : une collision écraserait le seed d'un autre wallet.
        var id: String
        do {
            id = "w_" + java.util.UUID.randomUUID().toString().take(8)
        } while (secureStorage.hasWalletSecrets(id) || walletDao.getById(id) != null)
        secureStorage.saveWalletSecrets(id, mnemonic, passphrase)
        val number = nextWalletNumber()
        val entity = WalletEntity(
            id = id,
            name = if (imported) "Wallet importé $number" else "Wallet $number",
            isActive = true,
            createdAt = System.currentTimeMillis()
        )
        walletDao.deactivateAll()
        walletDao.insert(entity)
        activateSecrets(entity)
        return entity
    }

    /** Nombre de wallets actuellement présents sur cet appareil. */
    suspend fun walletCount(): Int = try { walletDao.count() } catch (_: Exception) { 0 }

    /**
     * Prochain NUMÉRO de wallet — compteur MONOTONE qui ne se répète jamais,
     * même après suppression d'un wallet du milieu. L'ancien calcul (count + 1)
     * pouvait produire deux « Wallet 3 » (supprimer le 2 puis en créer un).
     * Baseline = max(compteur mémorisé, nombre actuel) → couvre les installs
     * existants qui n'avaient pas ce compteur.
     */
    private suspend fun nextWalletNumber(): Int {
        val prefs = appContext.getSharedPreferences("vaultex_wallet_seq", android.content.Context.MODE_PRIVATE)
        val current = try { walletDao.count() } catch (_: Exception) { 0 }
        val next = maxOf(prefs.getInt("last_number", 0), current) + 1
        prefs.edit().putInt("last_number", next).apply()
        return next
    }

    /** Bascule vers un autre wallet (son seed doit exister). */
    suspend fun switchWallet(id: String): Boolean {
        val entity = walletDao.getById(id) ?: return false
        if (!secureStorage.hasWalletSecrets(id)) return false   // jamais activer un wallet sans seed
        walletDao.deactivateAll()
        walletDao.activate(id)
        activateSecrets(entity)
        return true
    }

    /**
     * Supprime un wallet INACTIF : entité + seed chiffré. Refuse le wallet
     * actif (il faut d'abord basculer sur un autre).
     */
    suspend fun deleteWallet(id: String): Boolean {
        if (id == secureStorage.activeWalletId()) return false
        walletDao.delete(id)
        secureStorage.deleteWalletSecrets(id)
        // Sa liste de tokens personnalisés disparaît avec lui.
        try {
            appContext.getSharedPreferences(
                com.vaultex.data.repository.TokenRepository.OWNERSHIP_PREFS,
                android.content.Context.MODE_PRIVATE
            ).edit().remove(id).apply()
        } catch (_: Exception) { }
        return true
    }

    /** Rend [entity] actif côté secrets + purge les caches financiers. */
    private suspend fun activateSecrets(entity: WalletEntity) {
        secureStorage.setActiveWalletId(entity.id)
        secureStorage.saveWalletName(entity.name)
        // PURGE : aucun montant/tx de l'ancien wallet ne doit survivre.
        secureStorage.clearWalletCaches()
        try { transactionDao.deleteAll() } catch (_: Exception) { }
        try { pendingSendDao.deleteAll() } catch (_: Exception) { }
        // Soldes de référence du détecteur de dépôts : remis à zéro, sinon il
        // comparerait le solde du NOUVEAU wallet à celui de l'ancien et
        // enverrait de fausses notifications « dépôt reçu ». Après cette purge,
        // son premier passage re-mémorise sans notifier.
        try {
            appContext.getSharedPreferences("deposit_check_prefs", android.content.Context.MODE_PRIVATE)
                .edit().clear().apply()
        } catch (_: Exception) { }
        /*
        Marqueurs « adresse déjà balayée » : à remettre à zéro EN MÊME TEMPS que
        la table des transactions, sous peine d'incohérence.

        `transactionDao.deleteAll()` vient d'effacer tout l'historique local. Le
        prochain balayage va donc TOUT réinsérer, et chaque ligne réapparaîtra
        comme une insertion neuve. Si l'adresse restait marquée « déjà balayée »,
        chacune de ces réinsertions serait prise pour une nouvelle réception :
        l'utilisateur recevrait une rafale de fausses notifications « fonds
        reçus » simplement pour avoir changé de wallet.

        En remettant les marqueurs à zéro, cette réimportation redevient ce
        qu'elle est — un import silencieux — et seuls les dépôts SUIVANTS
        notifient. Purge globale, comme la table qu'elle accompagne.
         */
        try {
            appContext.getSharedPreferences("vaultex_sync_state", android.content.Context.MODE_PRIVATE)
                .edit().clear().apply()
        } catch (_: Exception) { }
        /*
        CLOCHE ET ÉTAT DES ALERTES : ils appartiennent AU WALLET, pas à
        l'appareil.

        Sans cette purge, on bascule sur le wallet 2 et la cloche continue
        d'annoncer « Vous avez reçu 0,5 BTC » — une réception du wallet 1, pour
        des fonds absents de celui qu'on regarde. L'utilisateur cherche un
        montant qui n'a jamais été là. C'est exactement le genre d'incohérence
        qui fait douter de l'application entière.

        Même raison pour le drapeau « alerte solde bas déjà envoyée » : gardé
        d'un wallet à l'autre, il empêchait le nouveau de recevoir la sienne, ou
        la déclenchait à tort.

        Et les clés d'anti-doublon des notifications : un dépôt du même montant
        sur le nouveau wallet, dans la demi-heure, aurait été pris pour une
        répétition et passé sous silence.
         */
        try { notificationCenter.clear() } catch (_: Exception) { }
        try { notifPrefs.lowBalanceNotified = false } catch (_: Exception) { }
        try {
            appContext.getSharedPreferences("vaultex_notif_dedup", android.content.Context.MODE_PRIVATE)
                .edit().clear().apply()
        } catch (_: Exception) { }
        // Force l'accueil à recharger les soldes du nouveau wallet dès son retour.
        BalanceRefreshSignal.signalTxSent()
    }
}
