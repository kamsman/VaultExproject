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
                /*
                ═══════════════════════════════════════════════════════════
                24 H D'HORLOGE N'ÉTAIT PAS 24 H D'ESSAIS
                ═══════════════════════════════════════════════════════════

                On cessait d'interroger 24 h après la CRÉATION de l'échange,
                quoi qu'il se soit passé entre-temps. Un téléphone éteint le
                week-end, un forfait épuisé, un voyage sans réseau — et le
                délai s'écoulait sans qu'une seule question ait été posée au
                fournisseur. L'échange restait « pending » à vie : pas de
                notification de fin, historique faux. Les fonds, eux,
                arrivaient — mais l'application l'ignorait pour toujours.

                Le budget porte désormais sur les ESSAIS, pas sur le temps.
                Trois régimes :

                  · jusqu'à 24 h        — à chaque passage, comme avant ;
                  · de 24 h à 7 jours   — une fois par heure, pas plus ;
                  · au-delà de 7 jours  — on renonce, et on le signale.

                Le plafond existe parce qu'un échange ouvert depuis une
                semaine ne se débloquera pas, et qu'interroger indéfiniment
                consommerait le quota du fournisseur pour rien.

                ON NE MARQUE TOUJOURS PAS EN ÉCHEC. Les fonds sont partis ;
                ils peuvent encore arriver. Déclarer un échec à tort serait
                pire que se taire — c'est la différence avec un dépôt refusé,
                où l'on SAIT que rien n'a quitté le portefeuille.

                L'accueil, lui, garde sa fenêtre de 24 h : il montre ce qui
                est vivant, pendant que le worker tient les comptes à jour.
                */
                val age = now - swap.timestamp
                val cle = CLE_DERNIER_ESSAI + swap.hash

                if (age > PLAFOND_SUIVI_MS) {
                    // Une seule alerte par échange : ce travail repasse toutes
                    // les 15 min, et le diagnostic n'a pas à être répété.
                    if (!suivi.getBoolean(CLE_ABANDON + swap.hash, false)) {
                        suivi.edit().putBoolean(CLE_ABANDON + swap.hash, true).apply()
                        com.vaultex.core.monitoring.AdminBot.serviceFailed(
                            "swap abandonne",
                            "id=${swap.hash} ouvert depuis plus de 7 jours (${swap.tokenSymbol})"
                        )
                    }
                    continue
                }

                if (age > MAX_TRACK_MS && now - suivi.getLong(cle, 0L) < RALENTI_MS) continue
                suivi.edit().putLong(cle, now).apply()

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
                status.hashSortie?.takeIf { it.isNotBlank() }?.let { payHash ->
                    toAsset?.let { runCatching { pendingTxManager.track(it.base, it.chain, payHash) } }
                }

                // Normalisé avant comparaison, comme dans refreshSwapStatus :
                // « Finished » avec une majuscule ne doit pas passer pour un
                // échange encore en cours.
                val etat = status.statut.trim().lowercase()
                if (etat !in TERMINAL) continue

                // Échange conclu : il sort de getPendingSwaps(), sa mémoire de
                // suivi n'a plus d'objet. Sans ce ménage, les préférences
                // grossiraient d'une entrée par échange, définitivement.
                suivi.edit().remove(CLE_DERNIER_ESSAI + swap.hash)
                    .remove(CLE_ABANDON + swap.hash).apply()

                if (etat == "finished") {
                    com.vaultex.core.monitoring.AdminBot.swapFinished(swap.amount, from, to, 0.0)
                } else {
                    com.vaultex.core.monitoring.AdminBot.swapFailed(from, to, status.statut)
                }

                if (!notifPrefs.txAlerts.value) continue
                val ctx = applicationContext
                if (etat == "finished") {
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
            com.vaultex.core.monitoring.reportUnlessCancelled("suivi des swaps", e)
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

    /**
     * Mémoire du suivi : date du dernier essai par échange, et marque
     * d'abandon. Des préférences plutôt qu'une colonne : la base n'a pas à
     * migrer pour un état qui ne concerne que ce worker, et qu'on peut perdre
     * sans dommage — au pire, un échange est réinterrogé une fois de trop.
     */
    private val suivi by lazy {
        applicationContext.getSharedPreferences("vaultex_suivi_swaps", Context.MODE_PRIVATE)
    }

    companion object {
        const val WORK_NAME = "vaultex_swap_tracking"

        private const val CLE_DERNIER_ESSAI = "dernier:"
        private const val CLE_ABANDON = "abandon:"

        /** Entre 24 h et 7 jours, on n'interroge plus qu'une fois par heure. */
        private const val RALENTI_MS = 60L * 60 * 1000

        /** Au-delà, on renonce : un échange ouvert depuis une semaine est mort. */
        private const val PLAFOND_SUIVI_MS = 7L * 24 * 60 * 60 * 1000

        /** Terminaux côté ChangeNOW : plus rien ne bougera après. */
        private val TERMINAL = setOf("finished", "failed", "refunded", "expired")

        /**
         * Fin du suivi RAPPROCHÉ, et fenêtre de l'accueil.
         *
         * Deux usages pour une seule valeur, et c'est délibéré.
         *
         * Ici, elle marque le passage au régime ralenti : au-delà, le worker
         * continue d'interroger, mais une fois par heure seulement (voir
         * doWork).
         *
         * L'accueil s'en sert pour décider ce qu'il appelle « en cours ».
         * Passé ce délai, un échange n'est plus une opération en cours mais
         * une anomalie : l'annoncer en tournant sur l'écran principal
         * inquiéterait sans rien apporter — le worker, lui, continue en
         * silence jusqu'à conclure ou renoncer.
         */
        const val MAX_TRACK_MS = 24L * 60 * 60 * 1000
    }
}
