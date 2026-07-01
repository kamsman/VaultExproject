package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.vaultex.core.session.NotifItem
import com.vaultex.core.session.NotificationCenter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class NotificationCenterViewModel @Inject constructor(
    private val notificationCenter: NotificationCenter
) : ViewModel() {

    val items: StateFlow<List<NotifItem>> = notificationCenter.items

    fun markAllRead() = notificationCenter.markAllRead()
    fun clear() = notificationCenter.clear()
}
