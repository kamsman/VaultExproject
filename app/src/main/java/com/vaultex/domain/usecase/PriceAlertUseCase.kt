package com.vaultex.domain.usecase

import com.vaultex.data.local.dao.PriceAlertDao
import com.vaultex.data.local.entity.PriceAlertEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

class PriceAlertUseCase @Inject constructor(
    private val priceAlertDao: PriceAlertDao
) {
    fun observeAlerts(): Flow<List<PriceAlertEntity>> = priceAlertDao.observeAll()

    fun observeActiveAlerts(): Flow<List<PriceAlertEntity>> = priceAlertDao.observeActive()

    /**
     * @param intention « VENTE », « ACHAT » ou « RIEN ». Vide = déduire de la
     *   condition, comportement des alertes créées avant ce choix.
     */
    suspend fun createAlert(
        symbol: String, condition: String, targetPrice: String, intention: String = ""
    ) {
        priceAlertDao.insert(PriceAlertEntity(
            id = UUID.randomUUID().toString(),
            tokenSymbol = symbol,
            condition = condition,
            targetPrice = targetPrice,
            isActive = true,
            intention = intention
        ))
    }

    suspend fun deleteAlert(id: String) = priceAlertDao.delete(id)

    suspend fun toggleAlert(id: String, active: Boolean) = priceAlertDao.setActive(id, active)
}
