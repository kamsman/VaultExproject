package com.vaultex.core.session

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Données d'un toast maison (logo crypto + texte). */
data class ToastData(val text: String, val symbol: String? = null)

/**
 * Émetteur global de toasts in-app (carte animée avec logo + texte), affiché
 * par [com.vaultex.ui.components.ToastHost] à la racine de l'app. Permet de
 * notifier depuis n'importe quel ViewModel (ex. envoi confirmé).
 */
@Singleton
class ToastController @Inject constructor() {
    private val _events = MutableSharedFlow<ToastData>(extraBufferCapacity = 8)
    val events: SharedFlow<ToastData> = _events

    fun show(text: String, symbol: String? = null) {
        _events.tryEmit(ToastData(text, symbol))
    }
}
