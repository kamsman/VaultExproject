package com.vaultex.data.repository

import com.vaultex.data.local.dao.TokenDao
import com.vaultex.data.local.entity.TokenEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenRepository @Inject constructor(
    private val tokenDao: TokenDao
) {
    fun observeAll(): Flow<List<TokenEntity>> = tokenDao.observeAll()

    suspend fun getByBlockchain(blockchain: String): List<TokenEntity> =
        tokenDao.getByBlockchain(blockchain)

    suspend fun addToken(token: TokenEntity) = tokenDao.insert(token)

    suspend fun setHidden(address: String, blockchain: String, hidden: Boolean) =
        tokenDao.setHidden(address, blockchain, hidden)
}
