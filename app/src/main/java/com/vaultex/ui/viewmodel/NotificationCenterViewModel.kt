package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.vaultex.core.session.NotifItem
import com.vaultex.core.session.NotificationCenter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class NotificationCenterViewModel @Inject constructor(
    private val notificationCenter: NotificationCenter,
    private val hub: com.vaultex.core.session.NotificationHub
) : ViewModel() {

    val items: StateFlow<List<NotifItem>> = notificationCenter.items

    /**
     * L'utilisateur a VU la liste : on remet à zéro la cloche ET la barre
     * système. Les deux comptes doivent rester cohérents — sinon la pastille
     * de l'icône continuerait d'annoncer des messages déjà lus.
     */
    fun markAllRead() {
        notificationCenter.markAllRead()
        hub.clearSystemNotifications()
    }

    fun clear() {
        notificationCenter.clear()
        hub.clearSystemNotifications()
    }
}
