package com.vaultex.core.session

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Vérification simple de la connectivité réseau. Utilisé pour éviter
 * de lancer un envoi de transaction sans connexion (message clair au
 * lieu d'un timeout confus).
 */
object NetworkMonitor {

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true // en cas de doute, on n'empêche pas l'action
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** Flux réactif de connectivité — pour un bandeau « hors connexion » qui
     *  apparaît/disparaît en direct, sans sonder en boucle. */
    fun observe(context: Context): Flow<Boolean> = callbackFlow {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) { trySend(true); awaitClose {}; return@callbackFlow }
        trySend(isOnline(context))
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(isOnline(context)) }
            override fun onLost(network: Network) { trySend(isOnline(context)) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { trySend(isOnline(context)) }
        }
        cm.registerNetworkCallback(NetworkRequest.Builder().build(), callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    /** true si l'appareil est HORS connexion — se met à jour en direct. */
    @Composable
    fun observeOffline(): Boolean {
        val context = LocalContext.current
        // remember() : sans lui, chaque recomposition du Dashboard créerait un
        // nouveau flux (nouvelle instance) → ré-enregistrement du callback
        // réseau à chaque fois, au lieu d'une seule écoute stable.
        val flow = remember(context) { observe(context) }
        val online by flow.collectAsState(initial = isOnline(context))
        return !online
    }
}
