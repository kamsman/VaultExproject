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

    // Défaut DÉSACTIVÉ : la notification « Nouvelle connexion » à chaque
    // déverrouillage n'est pas nécessaire. L'utilisateur peut l'activer dans
    // « Notifications sécurité » s'il le souhaite.
    private val _loginAlerts = MutableStateFlow(prefs.getBoolean("login", false))
    val loginAlerts: StateFlow<Boolean> = _loginAlerts

    private val _lowBalanceAlerts = MutableStateFlow(prefs.getBoolean("lowbal", false))
    val lowBalanceAlerts: StateFlow<Boolean> = _lowBalanceAlerts

    private val _pinChangeAlerts = MutableStateFlow(prefs.getBoolean("pin", true))
    val pinChangeAlerts: StateFlow<Boolean> = _pinChangeAlerts

    private val _thresholdXof = MutableStateFlow(prefs.getLong("threshold", 25_000L))
    val thresholdXof: StateFlow<Long> = _thresholdXof

    init {
        // Migration UNIQUE : l'ancienne version activait « Alertes connexion »
        // par défaut. On la désactive une seule fois pour les installs existants
        // (le tester ne verra plus « Nouvelle connexion » à chaque déverrouillage).
        // L'utilisateur peut toujours la réactiver dans « Notifications sécurité ».
        if (!prefs.getBoolean("mig_login_off_v1", false)) {
            _loginAlerts.value = false
            prefs.edit().putBoolean("login", false).putBoolean("mig_login_off_v1", true).apply()
        }
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
