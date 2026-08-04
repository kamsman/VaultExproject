package com.vaultex.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.data.local.dao.PendingSendDao
import com.vaultex.data.local.entity.PendingSendEntity
import com.vaultex.service.PendingSendWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PendingSendsViewModel @Inject constructor(
    private val pendingSendDao: PendingSendDao,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val items: StateFlow<List<PendingSendEntity>> =
        pendingSendDao.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Relance manuelle d'une intention échouée ou bloquée. On la remet en
     * PENDING et on reprogramme le worker. ATTENTION : action délibérée de
     * l'utilisateur — l'écran l'avertit de vérifier d'abord que l'envoi
     * précédent n'est pas déjà passé (risque de double-dépense sinon).
     */
    fun retry(item: PendingSendEntity) {
        viewModelScope.launch {
            pendingSendDao.updateResult(
                id = item.id,
                status = PendingSendWorker.STATUS_PENDING,
                txHash = null,
                error = null,
                attempts = item.attempts
            )
            PendingSendWorker.enqueue(appContext)
        }
    }

    /** Retire une intention de la file (abandon ou nettoyage). */
    fun dismiss(item: PendingSendEntity) {
        viewModelScope.launch { pendingSendDao.delete(item.id) }
    }

    /** Nettoie toutes les intentions déjà envoyées. */
    fun clearSent() {
        viewModelScope.launch { pendingSendDao.clearSent() }
    }
}
