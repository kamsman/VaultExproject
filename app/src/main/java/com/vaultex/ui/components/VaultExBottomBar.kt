package com.vaultex.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.vaultex.R
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.AccentBlue
import com.vaultex.ui.theme.Surface
import com.vaultex.ui.theme.TextMuted

/**
 * Bottom bar à 4 onglets du prototype v2 :
 * Accueil · Marché · Swap · Réglages
 */
@Composable
fun VaultExBottomBar(navController: NavHostController) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    val items = listOf(
        Triple(Routes.DASHBOARD, Icons.Default.Home, stringResource(R.string.tab_home)),
        Triple(Routes.MARKET, Icons.Default.TrendingUp, stringResource(R.string.tab_market)),
        Triple(Routes.SWAP, Icons.Default.SwapHoriz, stringResource(R.string.tab_swap)),
        Triple(Routes.SETTINGS, Icons.Default.Settings, stringResource(R.string.tab_settings))
    )

    NavigationBar(containerColor = Surface, windowInsets = NavigationBarDefaults.windowInsets) {
        items.forEach { (route, icon, label) ->
            NavigationBarItem(
                selected = currentRoute == route,
                onClick = {
                    navController.navigate(route) {
                        popUpTo(Routes.DASHBOARD) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AccentBlue,
                    selectedTextColor = AccentBlue,
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted,
                    indicatorColor = Surface
                )
            )
        }
    }
}
