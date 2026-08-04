package com.vaultex.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vaultex.R
import com.vaultex.core.session.NotificationHub
import com.vaultex.core.session.NotifPrefs
import com.vaultex.data.local.dao.TransactionDao
import com.vaultex.domain.usecase.SwapUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Suit les échanges en cours EN DEHORS de l'écran Swap.
 *
 * ── LE PROBLÈME QU'IL RÉSOUT ───────────────────────────────────────────────
 * Le suivi d'un swap vivait uniquement dans le `viewModelScope` de
 * `SwapViewModel`, lui-même lié à l'entrée de navigation de l'écran Swap.
 * Quitter cet écran détruisait le ViewModel, donc annulait la boucle de suivi.
 * Conséquences, toutes silencieuses :
 *
 *   - la notification « Échange effectué » ne partait jamais ;
 *   - le `payoutHash` n'était jamais transmis à `PendingTxManager`, donc la
 *     monnaie reçue n'affichait aucun badge « en attente » sur l'accueil ;
 *   - la transaction restait « pending » à vie dans l'historique.
 *
 * Autrement dit, l'utilisateur était CONTRAINT de rester sur l'écran de suivi
 * — sans que rien ne le lui dise — pendant 2 minutes à plusieurs heures (un
 * échange depuis Bitcoin attend ses confirmations). Exactement ce qu'on a
 * refusé de faire sur l'envoi.
 *
 * ── POURQUOI UN WORKER, ET PAS UN SCOPE PLUS LARGE ─────────────────────────
 * Élargir le scope (application au lieu de l'écran) survivrait à la navigation
 * mais pas à la fermeture de l'application, ni au redémarrage du téléphone.
 * Or un swap dure plus longtemps que la session moyenne. WorkManager persiste
 * le travail sur disque et le reprend après un redémarrage : c'est la seule
 * option qui tient la durée réelle d'un échange.
 *
 * ── PAS DE DOUBLE NOTIFICATION ─────────────────────────────────────────────
 * L'écran, quand il est ouvert, continue son propre suivi rapide (20 s) pour
 * un affichage vivant. Les deux chemins passent par [NotificationHub] avec la
 * MÊME clé (`swap:done:<id>`), qui déduplique : le premier arrivé notifie, le
 * second est ignoré.
 */
@HiltWorker
class SwapTrackingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val transactionDao: TransactionDao,
    private val swapUseCase: SwapUseCase,
    private val hub: NotificationHub,
    private val notifPrefs: NotifPrefs,
    private val pendingTxManager: com.vaultex.core.tx.PendingTxManager
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val pending = withContext(Dispatchers.IO) { transactionDao.getPendingSwaps() }
            if (pending.isEmpty()) return Result.success()

            val now = System.currentTimeMillis()
            for (swap in pending) {
                // Au-delà de MAX_TRACK_MS, on cesse d'interroger : un échange
                // encore ouvert après ce délai ne se débloquera pas tout seul,
                // et continuer à l'interroger toutes les 15 min pendant des
                // semaines consommerait le quota ChangeNOW pour rien. On ne le
                // marque PAS en échec pour autant — les fonds peuvent encore
                // arriver, et déclarer un échec à tort serait pire que se
                // taire. L'incident part au diagnostic administrateur.
                if (now - swap.timestamp > MAX_TRACK_MS) {
                    com.vaultex.core.monitoring.AdminBot.serviceFailed(
                        "swap bloque",
                        "id=${swap.hash} ouvert depuis plus de 24 h (${swap.tokenSymbol})"
                    )
                    continue
                }

                // refreshSwapStatus met déjà à jour le statut en base : une fois
                // « confirmed » ou « failed », le swap sort de getPendingSwaps()
                // et n'est plus interrogé.
                val status = withContext(Dispatchers.IO) {
                    runCatching { swapUseCase.refreshSwapStatus(swap.hash) }.getOrNull()
                } ?: continue

                // « CLÉ→CLÉ » tel qu'écrit par recordSwap (clés du registre :
                // BTC, USDT-ETH, SHIB…). On repasse par le registre plutôt que
                // de redéfinir la correspondance ici : une table dupliquée
                // finirait par diverger de l'originale sans que rien ne le
                // signale.
                val fromAsset = assetOrNull(swap.tokenSymbol.substringBefore("→"))
                val toAsset = assetOrNull(swap.tokenSymbol.substringAfter("→", ""))
                val from = fromAsset?.base ?: swap.blockchain
                val to = toAsset?.base ?: ""

                // Dès « sending », ChangeNOW a diffusé le versement : le badge
                // « en attente » doit apparaître sur la monnaie reçue sans
                // attendre la confirmation finale.
                status.payoutHash?.takeIf { it.isNotBlank() }?.let { payHash ->
                    toAsset?.let { runCatching { pendingTxManager.track(it.base, it.chain, payHash) } }
                }

                if (status.status !in TERMINAL) continue

                if (status.status == "finished") {
                    com.vaultex.core.monitoring.AdminBot.swapFinished(swap.amount, from, to, 0.0)
                } else {
                    com.vaultex.core.monitoring.AdminBot.swapFailed(from, to, status.status)
                }

                if (!notifPrefs.txAlerts.value) continue
                val ctx = applicationContext
                if (status.status == "finished") {
                    hub.post(
                        // Clé identique à celle de SwapViewModel : si l'écran a
                        // déjà notifié, le hub ignore ce doublon.
                        key = "swap:done:${swap.hash}",
                        title = ctx.getString(R.string.notif_swap_done_title),
                        body = ctx.getString(R.string.notif_swap_done_body, swap.amount, from, to),
                        symbol = to
                    )
                } else {
                    hub.post(
                        key = "swap:failed:${swap.hash}",
                        title = ctx.getString(R.string.notif_swap_failed_title),
                        body = ctx.getString(R.string.notif_swap_failed_body, from, to),
                        symbol = from
                    )
                }
            }
            Result.success()
        } catch (e: Exception) {
            /*
            `success` et non `retry` — même raison que DepositCheckWorker et
            PriceAlertWorker : le délai de WorkManager double à chaque échec, et
            tant qu'il attend, ce travail périodique ne tourne plus. Quelques
            échecs réseau d'affilée suffiraient à éteindre le suivi des swaps
            pour des heures. Le prochain cycle arrive dans 15 minutes de toute
            façon ; renoncer à celui-ci est la bonne réponse.
             */
            com.vaultex.core.monitoring.AdminBot.serviceFailed("suivi des swaps", e.message)
            Result.success()
        }
    }

    /**
     * Actif du registre, ou null si la clé est inconnue.
     *
     * `SwapViewModel.assetOf` retombe silencieusement sur Bitcoin quand la clé
     * ne correspond à rien — acceptable pour peupler un menu, dangereux ici :
     * on poserait un badge « en attente » sur BTC pour un swap qui n'a rien à
     * voir. Mieux vaut ne rien faire que se tromper de monnaie.
     */
    private fun assetOrNull(key: String): com.vaultex.ui.viewmodel.SwapViewModel.SwapAsset? =
        com.vaultex.ui.viewmodel.SwapViewModel.SWAP_ASSETS
            .firstOrNull { it.key.equals(key, ignoreCase = true) }

    companion object {
        const val WORK_NAME = "vaultex_swap_tracking"

        /** Terminaux côté ChangeNOW : plus rien ne bougera après. */
        private val TERMINAL = setOf("finished", "failed", "refunded", "expired")

        /** Au-delà, on cesse d'interroger (voir doWork). */
        private const val MAX_TRACK_MS = 24L * 60 * 60 * 1000
    }
}
