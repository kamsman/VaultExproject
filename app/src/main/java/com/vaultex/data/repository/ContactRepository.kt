package com.vaultex.data.repository

import com.vaultex.data.local.dao.ContactDao
import com.vaultex.data.local.entity.ContactEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepository @Inject constructor(
    private val contactDao: ContactDao
) {
    fun observeAll(): Flow<List<ContactEntity>> = contactDao.observeAll()

    suspend fun save(contact: ContactEntity) = contactDao.insert(contact)

    suspend fun delete(id: String) = contactDao.delete(id)
}
