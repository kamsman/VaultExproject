package com.vaultex.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val id: String,
    val name: String,
    val addressesJson: String,
    val notes: String?,
    val avatarColor: Int
)
