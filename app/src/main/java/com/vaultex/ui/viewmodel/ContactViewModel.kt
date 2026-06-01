package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.data.local.dao.ContactDao
import com.vaultex.data.local.entity.ContactEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ContactViewModel @Inject constructor(
    private val contactDao: ContactDao
) : ViewModel() {

    val contacts = contactDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addContact(name: String, addresses: Map<String, String>, notes: String? = null) {
        viewModelScope.launch {
            val json = JSONObject().apply {
                addresses.forEach { (chain, addr) -> put(chain, addr) }
            }.toString()

            // Derive a consistent avatar color from the name
            val color = (name.hashCode() and 0xFFFFFF) or (0xFF shl 24)

            contactDao.insert(
                ContactEntity(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    addressesJson = json,
                    notes = notes,
                    avatarColor = color
                )
            )
        }
    }

    fun deleteContact(id: String) {
        viewModelScope.launch { contactDao.delete(id) }
    }
}
