package com.vaultex.core.session

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Préférences de notifications de sécurité, PERSISTÉES (SharedPreferences) et
 * réellement consommées par les workers/écrans :
 *  - txAlerts        → notifications « Fonds reçus » (worker + sync Historique)
 *  - loginAlerts     → notification à chaque déverrouillage du wallet
 *  - lowBalanceAlerts + thresholdXof → alerte quand le solde total passe sous le seuil
 *  - pinChangeAlerts → notification quand le code PIN est modifié
 * Journal des connexions (déverrouillages) conservé pour l'écran Historique.
 */
@Singleton
class NotifPrefs @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("vaultex_notif_prefs", Context.MODE_PRIVATE)

    private val _txAlerts = MutableStateFlow(prefs.getBoolean("tx", true))
    val txAlerts: StateFlow<Boolean> = _txAlerts

    // « Alertes connexion » : désormais LIMITÉE à une notification par 24 h
    // (voir shouldNotifyLogin). Plus de « Nouvelle connexion » à CHAQUE
    // déverrouillage — au plus une fois par jour.
    private val _loginAlerts = MutableStateFlow(prefs.getBoolean("login", true))
    val loginAlerts: StateFlow<Boolean> = _loginAlerts

    private val _lowBalanceAlerts = MutableStateFlow(prefs.getBoolean("lowbal", false))
    val lowBalanceAlerts: StateFlow<Boolean> = _lowBalanceAlerts

    private val _pinChangeAlerts = MutableStateFlow(prefs.getBoolean("pin", true))
    val pinChangeAlerts: StateFlow<Boolean> = _pinChangeAlerts

    private val _thresholdXof = MutableStateFlow(prefs.getLong("threshold", 25_000L))
    val thresholdXof: StateFlow<Long> = _thresholdXof

    init {
        // Migration v2 : rétablit « Alertes connexion » (l'ancienne v1 l'avait
        // coupée). Elle n'est plus gênante car désormais PLAFONNÉE à 1×/24 h.
        // Ne s'exécute qu'une seule fois.
        if (!prefs.getBoolean("mig_login_v2", false)) {
            _loginAlerts.value = true
            prefs.edit().putBoolean("login", true).putBoolean("mig_login_v2", true).apply()
        }
    }

    // ─── Anti-spam « connexion » : au plus UNE notification toutes les 24 h ───
    private val loginThrottleMs = 24L * 60 * 60 * 1000   // 24 heures

    /** true si l'alerte connexion est active ET si aucune n'a été envoyée
     *  depuis 24 h → évite une notification à chaque déverrouillage. */
    fun shouldNotifyLogin(now: Long = System.currentTimeMillis()): Boolean =
        _loginAlerts.value && (now - prefs.getLong("login_notified_at", 0L) >= loginThrottleMs)

    /** Mémorise l'instant de la dernière alerte connexion envoyée. */
    fun markLoginNotified(now: Long = System.currentTimeMillis()) {
        prefs.edit().putLong("login_notified_at", now).apply()
    }

    fun setTxAlerts(v: Boolean) { _txAlerts.value = v; prefs.edit().putBoolean("tx", v).apply() }
    fun setLoginAlerts(v: Boolean) { _loginAlerts.value = v; prefs.edit().putBoolean("login", v).apply() }
    fun setLowBalanceAlerts(v: Boolean) { _lowBalanceAlerts.value = v; prefs.edit().putBoolean("lowbal", v).apply() }
    fun setPinChangeAlerts(v: Boolean) { _pinChangeAlerts.value = v; prefs.edit().putBoolean("pin", v).apply() }
    fun setThresholdXof(v: Long) { _thresholdXof.value = v; prefs.edit().putLong("threshold", v).apply() }

    // ─── Anti-spam « solde bas » : notifier UNE fois au passage sous le seuil ───
    var lowBalanceNotified: Boolean
        get() = prefs.getBoolean("lowbal_notified", false)
        set(v) { prefs.edit().putBoolean("lowbal_notified", v).apply() }

    // ─── Journal des déverrouillages (max 50, plus récent en tête) ───
    fun recordLogin(timestamp: Long = System.currentTimeMillis()) {
        val list = loginHistory().toMutableList()
        list.add(0, timestamp)
        prefs.edit().putString("logins", list.take(50).joinToString(",")).apply()
    }

    fun loginHistory(): List<Long> =
        prefs.getString("logins", null)?.split(",")?.mapNotNull { it.toLongOrNull() } ?: emptyList()
}
