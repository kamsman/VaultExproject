package com.vaultex.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultex.data.local.dao.ContactDao
import com.vaultex.data.local.entity.ContactEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class AddressBookUiState(
    val showAddDialog: Boolean = false,
    val newName: String = "",
    val newAddress: String = "",
    val newChain: String = "ETH",
    val error: String? = null
)

@HiltViewModel
class AddressBookViewModel @Inject constructor(
    private val contactDao: ContactDao
) : ViewModel() {

    val contacts: StateFlow<List<ContactEntity>> =
        contactDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _ui = MutableStateFlow(AddressBookUiState())
    val ui: StateFlow<AddressBookUiState> = _ui.asStateFlow()

    fun openAddDialog() = _ui.update { it.copy(showAddDialog = true, newName = "", newAddress = "", error = null) }
    fun closeDialog()   = _ui.update { it.copy(showAddDialog = false) }

    fun setName(v: String)    = _ui.update { it.copy(newName = v) }
    fun setAddress(v: String) = _ui.update { it.copy(newAddress = v) }
    fun setChain(v: String)   = _ui.update { it.copy(newChain = v) }

    fun saveContact() {
        val s = _ui.value
        if (s.newName.isBlank() || s.newAddress.isBlank()) {
            _ui.update { it.copy(error = "Nom et adresse requis") }
            return
        }
        viewModelScope.launch {
            val contact = ContactEntity(
                id = UUID.randomUUID().toString(),
                name = s.newName.trim(),
                addressesJson = """{"${s.newChain}":"${s.newAddress.trim()}"}""",
                notes = null,
                avatarColor = (0xFF3B82F6).toInt()
            )
            contactDao.insert(contact)
            _ui.update { it.copy(showAddDialog = false) }
        }
    }

    fun deleteContact(id: String) {
        viewModelScope.launch { contactDao.delete(id) }
    }
}
