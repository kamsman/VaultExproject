package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.vaultex.core.session.NotifPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SecurityNotifViewModel @Inject constructor(
    private val prefs: NotifPrefs
) : ViewModel() {
    val txAlerts: StateFlow<Boolean> = prefs.txAlerts
    val loginAlerts: StateFlow<Boolean> = prefs.loginAlerts
    val lowBalanceAlerts: StateFlow<Boolean> = prefs.lowBalanceAlerts
    val pinChangeAlerts: StateFlow<Boolean> = prefs.pinChangeAlerts
    val thresholdXof: StateFlow<Long> = prefs.thresholdXof

    fun setTx(v: Boolean) = prefs.setTxAlerts(v)
    fun setLogin(v: Boolean) = prefs.setLoginAlerts(v)
    fun setLowBalance(v: Boolean) = prefs.setLowBalanceAlerts(v)
    fun setPinChange(v: Boolean) = prefs.setPinChangeAlerts(v)
    fun setThreshold(v: Long) = prefs.setThresholdXof(v)

    fun loginHistory(): List<Long> = prefs.loginHistory()
}
