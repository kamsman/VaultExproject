package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.data.local.dao.PriceAlertDao
import com.vaultex.data.local.entity.PriceAlertEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PriceAlertViewModel @Inject constructor(
    private val priceAlertDao: PriceAlertDao
) : ViewModel() {

    val alerts: StateFlow<List<PriceAlertEntity>> = priceAlertDao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addAlert(symbol: String, condition: String, targetPrice: String) {
        if (targetPrice.toDoubleOrNull() == null) return
        viewModelScope.launch(Dispatchers.IO) {
            priceAlertDao.insert(PriceAlertEntity(
                id = UUID.randomUUID().toString(),
                tokenSymbol = symbol,
                condition = condition,
                targetPrice = targetPrice,
                isActive = true
            ))
        }
    }

    fun deleteAlert(id: String) {
        viewModelScope.launch(Dispatchers.IO) { priceAlertDao.delete(id) }
    }

    fun toggleAlert(id: String, active: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { priceAlertDao.setActive(id, active) }
    }
}
