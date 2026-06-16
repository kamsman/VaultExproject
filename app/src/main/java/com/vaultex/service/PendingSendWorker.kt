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
    private val sendCryptoUseCase: SendCryptoUseCase
) : CoroutineWorker(appContext, params) {

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
                is SendCryptoUseCase.Result.Success ->
                    pendingSendDao.updateResult(item.id, STATUS_SENT, res.txHash, null, attempts)
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
