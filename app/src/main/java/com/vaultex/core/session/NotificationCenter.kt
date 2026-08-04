package com.vaultex.core.session

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Une notification affichée dans le centre de notifications de l'app. */
data class NotifItem(
    val id: Long,
    val title: String,
    val body: String,
    val timestamp: Long,
    val symbol: String? = null,
    val read: Boolean = false
)

/**
 * Centre de notifications in-app : mémorise les notifications affichées (dépôts,
 * alertes prix, annonces…) pour les lister dans l'app et compter les non-lues.
 * Persisté en clair dans des SharedPreferences dédiées (aucune clé privée).
 */
@Singleton
class NotificationCenter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("vaultex_notif_center", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<NotifItem>> = _items

    /** Nombre de notifications non lues (pour la pastille). */
    private val _unreadCount = MutableStateFlow(_items.value.count { !it.read })
    val unreadCount: StateFlow<Int> = _unreadCount

    private fun apply(updated: List<NotifItem>) {
        _items.value = updated
        _unreadCount.value = updated.count { !it.read }
        save(updated)
    }

    /** Ajoute une notification en tête de liste (max 100 conservées). */
    @Synchronized
    fun push(title: String, body: String, symbol: String? = null) {
        val item = NotifItem(
            id = System.currentTimeMillis(),
            title = title,
            body = body,
            timestamp = System.currentTimeMillis(),
            symbol = symbol,
            read = false
        )
        apply((listOf(item) + _items.value).take(100))
    }

    /** Marque toutes les notifications comme lues. */
    fun markAllRead() = apply(_items.value.map { if (it.read) it else it.copy(read = true) })

    /** Vide le centre de notifications. */
    fun clear() = apply(emptyList())

    private fun load(): List<NotifItem> {
        return try {
            val json = prefs.getString(KEY, null) ?: return emptyList()
            gson.fromJson(json, object : TypeToken<List<NotifItem>>() {}.type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save(list: List<NotifItem>) {
        prefs.edit().putString(KEY, gson.toJson(list)).apply()
    }

    companion object {
        private const val KEY = "items"
    }
}
