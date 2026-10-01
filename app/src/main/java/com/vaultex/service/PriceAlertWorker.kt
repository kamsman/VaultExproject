package com.vaultex.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vaultex.R
import com.vaultex.core.market.CoinIds
import com.vaultex.core.session.PriceMoveSettings
import com.vaultex.data.local.dao.PriceAlertDao
import com.vaultex.data.local.entity.PriceAlertEntity
import com.vaultex.data.remote.api.CoinGeckoApi
import com.vaultex.data.remote.dto.CoinGeckoPriceDto
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.text.NumberFormat
import java.util.Locale

/**
 * Worker de prix, exécuté toutes les heures. Il fait DEUX choses en un seul
 * appel réseau :
 *
 * 1. **Alertes de variation** (automatiques, actives par défaut) : prévient
 *    quand une monnaie monte ou chute fortement sur 24 h. L'anti-spam est géré
 *    par [PriceMoveSettings] ; l'utilisateur peut les désactiver ou changer le
 *    seuil depuis l'écran Alertes.
 * 2. **Alertes de cible** (créées manuellement) : prévient quand un prix passe
 *    au-dessus/en dessous d'une valeur choisie. L'alerte est désactivée après
 *    déclenchement pour ne pas notifier en boucle.
 */
@HiltWorker
class PriceAlertWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val priceAlertDao: PriceAlertDao,
    private val coinGeckoApi: CoinGeckoApi,
    private val priceFallback: com.vaultex.data.repository.PriceFallbackSource,
    private val moveSettings: PriceMoveSettings,
    private val hub: com.vaultex.core.session.NotificationHub
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val alerts = priceAlertDao.getActiveOnce()
            val moveEnabled = moveSettings.enabled

            // Un seul appel pour les deux usages : les monnaies suivies pour
            // les variations + celles visées par une alerte de cible.
            val ids = buildSet {
                if (moveEnabled) addAll(CoinIds.ALERT_IDS.values)
                alerts.forEach { CoinIds.ALERT_IDS[it.tokenSymbol]?.let(::add) }
            }
            if (ids.isEmpty()) return Result.success()

            /*
            Source principale, puis SECOURS pour ce qu'elle n'a pas rendu.

            C'est ce worker qui remontait « ⚠️ Service indisponible : alertes
            de prix — HTTP 429 » en boucle sur le canal d'administration : le
            quota mensuel de CoinGecko épuisé, l'appel échouait à chaque
            réveil. Deux conséquences, l'alerte n'étant que la plus visible :
            plus aucune alerte de prix ne pouvait se déclencher, puisqu'il n'y
            avait plus de prix à comparer aux seuils.

            La source de secours n'a pas de quota mensuel. Un échec des DEUX
            reste signalé — c'est alors une vraie panne, pas un plafond.
             */
            val coursPrincipaux: Map<String, CoinGeckoPriceDto> = try {
                coinGeckoApi.getPrices(
                    ids = ids.joinToString(","),
                    // "usd" est indispensable : CoinGecko ne renvoie la variation
                    // 24 h (usd_24h_change) que si le dollar est demandé.
                    vsCurrencies = "usd,xof",
                    include24hChange = true,
                    includeMarketCap = false
                )
            } catch (_: Exception) { emptyMap() }
            val nonCotees = ids.filter { (coursPrincipaux[it]?.usd ?: 0.0) <= 0.0 }
            val prices = if (nonCotees.isEmpty()) coursPrincipaux
                else coursPrincipaux + priceFallback.pricesByCoinGeckoId(nonCotees)
            if (prices.isEmpty()) return Result.success()

            if (moveEnabled) checkMoves(prices)
            checkTargets(alerts, prices)
            Result.success()
        } catch (e: Exception) {
            /*
            `success` et non `retry`, comme pour DepositCheckWorker.

            CoinGecko limite le débit : un 429 est le cas d'échec le plus
            courant ici. Avec `retry`, WorkManager reprogramme avec un délai qui
            DOUBLE à chaque échec — et tant qu'il attend, ce travail périodique
            ne tourne plus. Quelques limitations d'affilée suffisent donc à
            éteindre les alertes de prix pour des heures, sans que rien ne
            l'indique.

            Le travail repasse de toute façon à l'heure suivante : renoncer à
            ce cycle-ci est la bonne réponse, réessayer en boucle ne l'est pas.
            La mesure surveillée est une variation sur 24 h — sauter un cycle
            n'y change quasiment rien.
             */
            com.vaultex.core.monitoring.reportUnlessCancelled("alertes de prix", e)
            Result.success()
        }
    }

    /** Alertes automatiques : forte hausse / forte baisse sur 24 h. */
    private fun checkMoves(prices: Map<String, CoinGeckoPriceDto>) {
        val now = System.currentTimeMillis()
        CoinIds.ALERT_IDS.forEach { (symbol, id) ->
            val dto = prices[id] ?: return@forEach
            val change = dto.change24h
            // 0.0 = valeur par défaut du DTO, donc donnée absente et non pas
            // « le cours n'a pas bougé d'un centième ». On ne notifie jamais
            // sur une donnée qu'on n'a pas reçue.
            if (change == 0.0) return@forEach
            val direction = moveSettings.evaluate(symbol, change, now) ?: return@forEach
            notifyMove(symbol, change, dto.xof, isUp = direction == "up")
        }
    }

    /** Alertes créées par l'utilisateur (cible de prix atteinte).
     *  `suspend` : la désactivation après déclenchement passe par le DAO. */
    private suspend fun checkTargets(alerts: List<PriceAlertEntity>, prices: Map<String, CoinGeckoPriceDto>) {
        alerts.forEach { alert ->
            val id = CoinIds.ALERT_IDS[alert.tokenSymbol] ?: return@forEach
            val current = prices[id]?.xof?.takeIf { it > 0 } ?: return@forEach
            val target = alert.targetPrice.toDoubleOrNull() ?: return@forEach
            val isAbove = alert.condition.contains("dessus", ignoreCase = true)
            val triggered = (isAbove && current >= target) || (!isAbove && current <= target)
            if (triggered) {
                /*
                CE QUE L'UTILISATEUR A DEMANDÉ PRIME SUR CE QU'ON DEVINE.

                `intention` est son choix à la création : vendre, acheter, ou
                seulement être prévenu. Vide, c'est une alerte créée avant que
                ce choix existe — on retombe alors sur la déduction d'avant :
                au-dessus de la cible, on suppose vendre ; en dessous, acheter.

                « RIEN » n'est pas un oubli à combler : quelqu'un qui surveille
                un cours sans intention d'agir ne doit pas se retrouver devant
                un formulaire d'échange parce qu'il a touché une notification.
                */
                val choix = alert.intention.uppercase()
                val vendre: Boolean? = when {
                    choix == com.vaultex.core.session.AlerteSwapBuffer.INTENTION_VENTE -> true
                    choix == com.vaultex.core.session.AlerteSwapBuffer.INTENTION_ACHAT -> false
                    choix == com.vaultex.core.session.AlerteSwapBuffer.INTENTION_RIEN -> null
                    else -> isAbove
                }
                notify(alert.tokenSymbol, alert.condition, target, current, vendre)
                priceAlertDao.setActive(alert.id, false)
            }
        }
    }

    private fun notifyMove(symbol: String, changePercent: Double, priceXof: Double, isUp: Boolean) {
        val ctx = com.vaultex.core.session.LocaleManager.wrap(applicationContext)
        /*
        Canal SÉPARÉ, mais en importance HAUTE.

        Séparé, pour que l'utilisateur puisse couper les alertes de marché
        depuis les réglages Android SANS perdre les alertes de fonds reçus —
        deux besoins très différents ne doivent pas partager un interrupteur.

        Haute, parce qu'une alerte de prix qui dort au fond du volet ne
        ramène personne dans l'application : elle doit s'afficher en haut de
        l'écran comme les autres.

        Le suffixe « _v2 » est nécessaire : Android FIGE l'importance d'un
        canal à sa création et ignore toute modification ultérieure. Sans
        nouvel identifiant, les appareils ayant déjà lancé l'app resteraient
        bloqués sur l'ancienne importance, discrète.
         */
        val manager = ctx.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MOVES,
                ctx.getString(R.string.price_moves_channel),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        val percent = String.format(com.vaultex.core.session.LocaleManager.appLocale(), "%+.1f %%", changePercent)

        /*
        ═══════════════════════════════════════════════════════════════════
        TROIS PALIERS D'INTENSITÉ
        ═══════════════════════════════════════════════════════════════════

        Toutes les variations disaient la même phrase. Une monnaie qui prend
        5 % et une qui prend 40 % arrivaient donc avec le même titre, la même
        icône, le même ton — et le pourcentage, seul à les distinguer, se
        trouve dans le corps du message, que l'on ne lit qu'après avoir décidé
        que la notification méritait un regard.

        Or c'est exactement l'inverse qu'il faut : l'ampleur doit être visible
        AVANT la lecture, dans le titre qui s'affiche seul en bandeau. Une
        alerte qui ne hiérarchise pas finit ignorée en bloc, y compris pour
        les mouvements qui comptaient vraiment.

        Les paliers viennent de PriceMoveSettings, qui s'en sert aussi pour
        laisser passer une aggravation malgré le délai de garde. Une seule
        définition pour les deux : deux tables séparées finiraient par
        diverger, et l'on annoncerait « EXPLOSE » sur un palier que l'anti-spam
        croit inchangé.
        */
        val titreRes = when (PriceMoveSettings.palier(changePercent)) {
            2 -> if (isUp) R.string.price_move_up_major else R.string.price_move_down_major
            1 -> if (isUp) R.string.price_move_up_strong else R.string.price_move_down_strong
            else -> if (isUp) R.string.price_move_up_title else R.string.price_move_down_title
        }
        val title = ctx.getString(titreRes, symbol)
        val body = if (priceXof > 0) {
            fmt.maximumFractionDigits = if (priceXof < 100) 2 else 0
            ctx.getString(R.string.price_move_body, percent, prixFcfa(priceXof))
        } else {
            ctx.getString(R.string.price_move_body_no_price, percent)
        }
        // Par le hub : bannière, pastille de l'icône et cloche d'un seul geste.
        // L'anti-spam propre aux variations est déjà assuré en amont par
        // PriceMoveSettings (réarmement + délai de garde de 12 h).
        hub.post(
            // Le palier entre dans la clé : sans lui, l'alerte d'aggravation
            // que PriceMoveSettings vient d'autoriser serait écartée ici comme
            // un doublon de la précédente.
            key = "move:$symbol:${if (isUp) "up" else "down"}:${PriceMoveSettings.palier(changePercent)}:${changePercent.toInt()}",
            title = title,
            body = body,
            symbol = symbol,
            channelId = CHANNEL_MOVES
        )
    }

    /** [vendre] : vrai = vendre, faux = acheter, null = ne proposer aucun échange. */
    /*
    ═══════════════════════════════════════════════════════════════════════
    UN PRIX EN FCFA NE S'ÉCRIT PAS AVEC TROIS DÉCIMALES
    ═══════════════════════════════════════════════════════════════════════

    Constaté sur appareil : « SOL est au-dessus de 100 FCFA (prix actuel :
    69 046,569 FCFA) ». Le franc CFA n'a pas de subdivision en circulation —
    ces trois chiffres après la virgule n'informent de rien, et ils arrivent
    là où l'on a le moins de place et le moins de temps : une bannière de
    notification, lue d'un coup d'œil.

    NumberFormat par défaut écrit jusqu'à trois décimales. C'est juste pour
    une quantité, faux pour une monnaie sans centimes.

    MAIS ON NE PEUT PAS ARRONDIR À L'ENTIER PARTOUT. SHIB et PEPE valent une
    fraction de franc : « 0 FCFA » serait pire que trois décimales de trop.
    La précision suit donc l'ordre de grandeur — c'est ce que fait n'importe
    quel tableau de cours, et ce que l'œil attend.
    ═══════════════════════════════════════════════════════════════════════
    */
    private fun prixFcfa(valeur: Double): String {
        val decimales = when {
            valeur >= 100.0 -> 0      // 1 576 381 FCFA
            valeur >= 1.0 -> 2        // 583,96 FCFA
            valeur >= 0.01 -> 4
            else -> 8                 // les jetons à fraction de franc
        }
        val f = NumberFormat.getNumberInstance(com.vaultex.core.session.LocaleManager.appLocale())
        f.maximumFractionDigits = decimales
        f.minimumFractionDigits = 0   // « 600 », jamais « 600,00 »
        return f.format(valeur)
    }

    private fun notify(
        symbol: String, condition: String, target: Double, current: Double, vendre: Boolean?
    ) {
        val ctx = com.vaultex.core.session.LocaleManager.wrap(applicationContext)
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                ctx.getString(R.string.alerts_title),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        val title = ctx.getString(R.string.alert_triggered_title, symbol)
        val body = ctx.getString(
            R.string.alert_triggered_body, symbol, condition, prixFcfa(target), prixFcfa(current)
        )
        // Une cible ne se déclenche qu'une fois (l'alerte est désactivée juste
        // après) : la clé porte la cible elle-même.
        /*
        ─── LA TOUCHER OUVRE L'ÉCHANGE ───────────────────────────────────

        Elle ouvrait l'accueil. Il fallait ensuite trouver le Swap, rechoisir
        la monnaie et le sens — et le temps de tout refaire, le cours a bougé.
        Une alerte qu'on ne peut pas suivre d'un doigt ne sert à rien.

        Le sens est celui de la condition qui vient d'être remplie : au-dessus
        de la cible, on suppose qu'on voulait vendre ; en dessous, acheter.
        C'est une supposition, elle se retourne d'un doigt sur l'écran, et
        aucun montant n'est saisi à la place de l'utilisateur.

        SEULES LES ALERTES À CIBLE mènent à l'échange. Les alertes
        automatiques de forte hausse ou de forte baisse (voir notifyMove)
        n'ont pas été demandées par l'utilisateur : les faire déboucher sur
        un formulaire d'échange serait pousser à réagir à une secousse, ce
        qui est exactement le contraire d'un outil qui garde de l'argent.
        */
        hub.post(
            key = "target:$symbol:$condition:$target",
            title = title,
            body = body,
            symbol = symbol,
            channelId = CHANNEL_ID,
            // null → aucun extra posé, la notification ouvre l'application
            // comme avant. C'est le sens de « juste me prévenir ».
            swapSymbole = if (vendre != null) symbol else null,
            swapVente = vendre == true
        )
    }

    companion object {
        const val CHANNEL_ID = "vaultex_price_alerts"
        const val CHANNEL_MOVES = "vaultex_price_moves_v2"
        const val WORK_NAME = "price_alert_check"

        // La liste des monnaies surveillées vit dans CoinIds : l'écran de
        // création d'alerte lit la MÊME table. Elle était recopiée ici, et
        // une divergence produisait une alerte que l'utilisateur voyait
        // active mais que ce worker ne vérifiait jamais.
    }
}
