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
     * Relit le disque a l'ouverture de l'ecran.
     *
     * Le retour au premier plan declenche deja une relecture, mais il ne
     * couvre pas tout : si l'application est DEJA au premier plan quand une
     * notification arrive dans un autre processus, aucun evenement de cycle de
     * vie ne se produit. Relire ici garantit que l'utilisateur voit l'etat
     * reel au moment ou il regarde.
     */
    fun refresh() = notificationCenter.reload()

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
