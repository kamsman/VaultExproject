package com.vaultex.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vaultex.data.local.dao.PendingSendDao
import com.vaultex.domain.usecase.SendCryptoUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Vide la file des envois mis en attente hors-ligne. Déclenché
 * automatiquement par WorkManager dès qu'une connexion réseau est
 * disponible (contrainte NetworkType.CONNECTED).
 *
 * SÉCURITÉ — anti double-dépense : chaque intention est tentée UNE SEULE
 * fois. Avant l'appel réseau elle passe en SENDING et ne sera donc plus
 * jamais resélectionnée automatiquement (getPending ne lit que PENDING).
 * En cas d'échec — y compris un accusé de réception perdu après une
 * diffusion réussie — l'élément finit en FAILED (ou reste en SENDING si le
 * worker est tué en plein vol) et c'est à l'utilisateur de relancer
 * manuellement. On ne re-signe JAMAIS avec un nouveau nonce en aveugle :
 * un envoi en moins est récupérable, un double-envoi ne l'est pas.
 */
@HiltWorker
class PendingSendWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val pendingSendDao: PendingSendDao,
    private val sendCryptoUseCase: SendCryptoUseCase,
    private val secureStorage: com.vaultex.core.security.SecureStorage,
    private val transactionDao: com.vaultex.data.local.dao.TransactionDao,
    private val tokenRepository: com.vaultex.data.repository.TokenRepository,
    private val pendingTxManager: com.vaultex.core.tx.PendingTxManager,
    private val hub: com.vaultex.core.session.NotificationHub,
    private val notifPrefs: com.vaultex.core.session.NotifPrefs
) : CoroutineWorker(appContext, params) {

    /** Chaîne native qui paie les frais (BTC/ETH/BNB/SOL/TRX), à partir du
     *  code stocké dans la file (natif, "USDT*", ou "ERC20:<ETH|BNB>:contrat:déc"). */
    private fun nativeUnit(chain: String): String = when {
        chain.startsWith("ERC20:") -> if (chain.split(":").getOrNull(1) == "BNB") "BNB" else "ETH"
        chain == "ETH" || chain == "USDT-ETH" -> "ETH"
        chain == "BNB" || chain == "USDT-BNB" -> "BNB"
        chain == "SOL" -> "SOL"
        chain == "TRX" || chain == "USDT" -> "TRX"
        else -> chain
    }

    /** Symbole affiché : ticker réel du token pour un contrat ERC-20/BEP-20. */
    private suspend fun displaySymbol(chain: String): String = when {
        chain.startsWith("ERC20:") -> {
            val contract = chain.split(":").getOrNull(2)
            try {
                tokenRepository.getCustom().firstOrNull { it.contractAddress.equals(contract, ignoreCase = true) }?.symbol
            } catch (_: Exception) { null } ?: "Token"
        }
        chain.startsWith("USDT") -> "USDT"
        else -> chain
    }

    private suspend fun myAddressFor(chain: String): String = try {
        val mnemonic = secureStorage.getMnemonic() ?: ""
        val a = com.vaultex.core.crypto.WalletManager.deriveAddresses(mnemonic, secureStorage.getPassphrase())
        when (chain) {
            "BTC" -> a.btc; "ETH" -> a.eth; "BNB" -> a.bnb; "SOL" -> a.sol; "TRX" -> a.trx
            else -> ""
        }
    } catch (_: Exception) { "" }

    override suspend fun doWork(): Result {
        val pending = try {
            pendingSendDao.getPending()
        } catch (_: Exception) {
            return Result.retry()
        }
        if (pending.isEmpty()) return Result.success()

        for (item in pending) {
            val attempts = item.attempts + 1
            // Réserve l'élément : il ne sera plus resélectionné automatiquement.
            pendingSendDao.updateResult(item.id, STATUS_SENDING, null, null, attempts)

            val res = try {
                sendCryptoUseCase.sendByChain(item.chain, item.toAddress, item.amount)
            } catch (e: Exception) {
                SendCryptoUseCase.Result.Error(e.message ?: "Erreur d'envoi")
            }

            when (res) {
                is SendCryptoUseCase.Result.Success -> {
                    pendingSendDao.updateResult(item.id, STATUS_SENT, res.txHash, null, attempts)
                    // Aligne cet envoi (parti hors-ligne, diffusé maintenant) sur le
                    // MÊME comportement qu'un envoi en ligne : badge de suivi,
                    // entrée immédiate dans « Récent », notification cloche —
                    // sans quoi un envoi mis en file restait invisible partout
                    // sauf dans l'écran « Transactions en attente ».
                    try {
                        val nativeChain = nativeUnit(item.chain)
                        pendingTxManager.track(item.chain, nativeChain, res.txHash)
                        val myAddr = myAddressFor(nativeChain)
                        val sym = displaySymbol(item.chain)
                        transactionDao.insert(
                            com.vaultex.data.local.entity.TransactionEntity(
                                hash = res.txHash,
                                type = "sent",
                                blockchain = nativeChain,
                                fromAddress = myAddr,
                                toAddress = item.toAddress,
                                amount = item.amount,
                                tokenSymbol = sym,
                                fee = "0",
                                status = "pending",
                                timestamp = System.currentTimeMillis(),
                                confirmations = 0,
                                blockNumber = null
                            )
                        )
                        // Barre système ET cloche : l'utilisateur ne regarde
                        // PAS l'app à cet instant — l'envoi partait justement
                        // d'une file hors-ligne, débloquée en arrière-plan.
                        // C'est le cas où une notification compte le plus.
                        if (notifPrefs.txAlerts.value) {
                            hub.post(
                                key = "sent:${res.txHash}",
                                title = "Envoi effectué",
                                body = "Envoi de ${item.amount} $sym confirmé (mis en file hors-ligne)",
                                symbol = sym
                            )
                        }
                    } catch (_: Exception) { }
                }
                is SendCryptoUseCase.Result.Error ->
                    pendingSendDao.updateResult(item.id, STATUS_FAILED, null, res.message, attempts)
            }
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "pending_send_flush"
        const val STATUS_PENDING = "PENDING"
        const val STATUS_SENDING = "SENDING"
        const val STATUS_SENT = "SENT"
        const val STATUS_FAILED = "FAILED"

        /**
         * Programme un vidage de la file dès qu'une connexion est
         * disponible. Travail unique (KEEP) : un seul vidage à la fois.
         */
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<PendingSendWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
