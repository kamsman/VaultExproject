package com.vaultex.core.session

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

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
}
