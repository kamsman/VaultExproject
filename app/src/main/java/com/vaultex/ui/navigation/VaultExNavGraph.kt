package com.vaultex.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

import com.vaultex.ui.screens.splash.SplashScreen
import com.vaultex.ui.screens.welcome.WelcomeScreen
import com.vaultex.ui.screens.onboarding.OnboardingScreen
import com.vaultex.ui.screens.onboarding.MnemonicDisplayScreen
import com.vaultex.ui.screens.onboarding.MnemonicVerifyScreen
import com.vaultex.ui.screens.onboarding.ImportWalletScreen
import com.vaultex.ui.screens.security.PinSetupScreen
import com.vaultex.ui.screens.dashboard.DashboardScreen
import com.vaultex.ui.screens.market.MarketScreen
import com.vaultex.ui.screens.settings.SettingsScreen
import com.vaultex.ui.screens.send.SendScreen
import com.vaultex.ui.screens.receive.ReceiveScreen
import com.vaultex.ui.screens.swap.SwapScreen
import com.vaultex.ui.screens.history.HistoryScreen
import com.vaultex.ui.screens.security.NotificationsScreen
import com.vaultex.ui.screens.tokens.TokenDetailScreen
import com.vaultex.ui.screens.security.PinUnlockScreen
import com.vaultex.ui.screens.security.ShowMnemonicScreen
import com.vaultex.ui.screens.alerts.PriceAlertScreen
import com.vaultex.ui.viewmodel.OnboardingViewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument

@Composable
fun VaultExNavGraph(navController: NavHostController) {

    // ViewModel partagé entre tous les écrans d'onboarding.
    // Scoped à l'Activity (appelé depuis MainActivity.setContent) → même instance
    // pour MnemonicDisplay → MnemonicVerify → PinSetup.
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH
    ) {

        composable(Routes.SPLASH) {
            SplashScreen(navController)
        }

        composable(Routes.ONBOARDING) {
            OnboardingScreen(navController)
        }

        composable(Routes.WELCOME) {
            WelcomeScreen(navController)
        }

        composable(Routes.MNEMONIC_DISPLAY) {
            MnemonicDisplayScreen(navController, onboardingViewModel)
        }

        composable(Routes.MNEMONIC_VERIFY) {
            MnemonicVerifyScreen(navController, onboardingViewModel)
        }

        composable(Routes.IMPORT_WALLET) {
            ImportWalletScreen(navController, onboardingViewModel)
        }

        composable(Routes.PIN_SETUP) {
            PinSetupScreen(navController, onboardingViewModel)
        }

        composable(Routes.DASHBOARD) {
            DashboardScreen(navController)
        }

        composable(Routes.MARKET) {
            MarketScreen(navController)
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(navController)
        }

        composable(Routes.SEND) {
            SendScreen(navController, "")
        }

        composable(Routes.RECEIVE) {
            ReceiveScreen(navController, "")
        }

        composable(Routes.SWAP) {
            SwapScreen(navController)
        }

        composable(Routes.HISTORY) {
            HistoryScreen(navController)
        }

        composable(Routes.NOTIFICATIONS) {
            NotificationsScreen(navController)
        }

        composable(
            route = Routes.TOKEN_DETAIL,
            arguments = listOf(navArgument("symbol") { type = NavType.StringType })
        ) { backStackEntry ->
            TokenDetailScreen(navController, backStackEntry.arguments?.getString("symbol") ?: "")
        }

        composable(
            route = Routes.PIN_UNLOCK_TO_ROUTE,
            arguments = listOf(navArgument("destination") { type = NavType.StringType })
        ) { backStackEntry ->
            PinUnlockScreen(navController, backStackEntry.arguments?.getString("destination") ?: "")
        }

        composable(Routes.SHOW_MNEMONIC) {
            ShowMnemonicScreen(navController)
        }

        composable(Routes.PRICE_ALERTS) {
            PriceAlertScreen(navController)
        }

        composable(
            route = Routes.SEND_TOKEN,
            arguments = listOf(navArgument("symbol") { type = NavType.StringType })
        ) { backStackEntry ->
            SendScreen(navController, backStackEntry.arguments?.getString("symbol") ?: "")
        }

        composable(
            route = Routes.RECEIVE_TOKEN,
            arguments = listOf(navArgument("symbol") { type = NavType.StringType })
        ) { backStackEntry ->
            ReceiveScreen(navController, backStackEntry.arguments?.getString("symbol") ?: "")
        }
    }
}
