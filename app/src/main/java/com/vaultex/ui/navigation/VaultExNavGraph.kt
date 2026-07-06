package com.vaultex.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

import com.vaultex.ui.screens.splash.SplashScreen
import com.vaultex.ui.screens.welcome.WelcomeScreen
import com.vaultex.ui.screens.onboarding.OnboardingScreen
import com.vaultex.ui.screens.onboarding.MnemonicDisplayScreen
import com.vaultex.ui.screens.onboarding.MnemonicVerifyScreen
import com.vaultex.ui.screens.onboarding.ImportWalletScreen
import com.vaultex.ui.screens.security.PinSetupScreen
import com.vaultex.ui.screens.pending.PendingSendsScreen
import com.vaultex.ui.screens.dashboard.DashboardScreen
import com.vaultex.ui.screens.home.HomeScreen
import com.vaultex.ui.screens.market.MarketScreen
import com.vaultex.ui.screens.market.CoinDetailScreen
import com.vaultex.ui.screens.settings.SettingsScreen
import com.vaultex.ui.screens.settings.BackupScreen
import com.vaultex.ui.screens.send.SendScreen
import com.vaultex.ui.screens.receive.ReceiveScreen
import com.vaultex.ui.screens.swap.SwapScreen
import com.vaultex.ui.screens.history.HistoryScreen
import com.vaultex.ui.screens.auth.PanicPinScreen
import com.vaultex.ui.screens.auth.BiometricSetupScreen
import com.vaultex.ui.screens.walletmanager.WalletManagerScreen
import com.vaultex.ui.screens.walletmanager.TokenManagerScreen
import com.vaultex.ui.screens.notifications.AlertsScreen
import com.vaultex.ui.screens.tokens.TokenDetailScreen
import com.vaultex.ui.screens.unlock.UnlockScreen
import com.vaultex.ui.screens.addressbook.AddressBookScreen
import com.vaultex.ui.screens.security.SecurityScreen
import com.vaultex.ui.screens.settings.NetworkSettingsScreen
import com.vaultex.ui.screens.scanner.QrScannerScreen
import com.vaultex.ui.screens.history.TransactionDetailScreen
import com.vaultex.ui.screens.help.HelpScreen
import com.vaultex.ui.screens.wallet.PortfolioScreen
import com.vaultex.ui.screens.wallet.TokenDetailScreen as PortfolioTokenDetailScreen
import com.vaultex.ui.screens.send.SendFormScreen
import com.vaultex.ui.screens.send.SendConfirmScreen
import com.vaultex.ui.screens.send.SendResultScreen
import com.vaultex.ui.screens.swap.SwapConfirmScreen
import com.vaultex.ui.screens.swap.SwapResultScreen
import com.vaultex.ui.screens.receive.ReceiveNetworkScreen
import com.vaultex.ui.screens.receive.ReceiveAddressScreen
import com.vaultex.ui.screens.tokens.AddTokenScreen
import com.vaultex.ui.screens.tokens.ManageTokensScreen
import com.vaultex.ui.screens.splash.FirstLaunchSplash
import com.vaultex.ui.screens.onboarding.SecuritySetupScreen
import com.vaultex.ui.screens.settings.ImportWalletScreen as SettingsImportWalletScreen
import com.vaultex.ui.screens.settings.WalletManagementScreen
import com.vaultex.ui.screens.security.SecurityNotificationsScreen
import com.vaultex.ui.viewmodel.OnboardingViewModel

@Composable
fun VaultExNavGraph(navController: NavHostController) {

    val onboardingViewModel: OnboardingViewModel = hiltViewModel()

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH
    ) {

        // ─── Splash ───────────────────────────────────────────
        composable(Routes.SPLASH) {
            SplashScreen(navController)
        }

        // ─── Onboarding ───────────────────────────────────────
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

        // ─── Auth / PIN ────────────────────────────────────────
        composable(Routes.PIN_SETUP) {
            PinSetupScreen(navController, onboardingViewModel)
        }

        composable(Routes.PIN_UNLOCK) {
            UnlockScreen(navController)
        }

        composable(Routes.BIOMETRIC_SETUP) {
            BiometricSetupScreen(navController)
        }

        composable(Routes.PANIC_PIN) {
            PanicPinScreen(navController)
        }

        // ─── Main ──────────────────────────────────────────────
        composable(Routes.HOME) {
            HomeScreen(navController)
        }

        composable(Routes.DASHBOARD) {
            DashboardScreen(navController)
        }

        composable(Routes.MARKET) {
            MarketScreen(navController)
        }

        composable(
            route = Routes.COIN_DETAIL,
            arguments = listOf(navArgument("coinId") { type = NavType.StringType })
        ) { backStackEntry ->
            val coinId = backStackEntry.arguments?.getString("coinId") ?: "bitcoin"
            CoinDetailScreen(navController, coinId)
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(navController)
        }

        composable(Routes.BACKUP) {
            BackupScreen(navController)
        }

        composable(Routes.SEND) {
            SendScreen(navController)
        }

        composable(Routes.SEND_SELECT) {
            com.vaultex.ui.screens.send.SendSelectScreen(navController)
        }

        composable(Routes.RECEIVE) {
            ReceiveScreen(navController)
        }

        composable(Routes.SWAP) {
            SwapScreen(navController)
        }

        composable(Routes.HISTORY) {
            HistoryScreen(navController)
        }

        composable(Routes.NOTIFICATIONS) {
            AlertsScreen(navController)
        }

        composable(Routes.NOTIFICATION_CENTER) {
            com.vaultex.ui.screens.notifications.NotificationCenterScreen(navController)
        }

        composable(
            route = Routes.TOKEN_DETAIL,
            arguments = listOf(navArgument("symbol") { type = NavType.StringType })
        ) { backStackEntry ->
            val symbol = backStackEntry.arguments?.getString("symbol") ?: "ETH"
            TokenDetailScreen(navController, symbol)
        }

        composable(Routes.ADDRESS_BOOK) {
            AddressBookScreen(navController)
        }

        composable(Routes.WALLET_MANAGER) {
            WalletManagerScreen(navController)
        }

        composable(Routes.TOKEN_MANAGER) {
            TokenManagerScreen(navController)
        }

        composable(Routes.SECURITY) {
            SecurityScreen(navController)
        }

        composable(Routes.LOGIN_HISTORY) {
            com.vaultex.ui.screens.security.LoginHistoryScreen(navController)
        }

        composable(Routes.PENDING_SENDS) {
            PendingSendsScreen(navController)
        }

        composable(Routes.NETWORK_SETTINGS) {
            NetworkSettingsScreen(navController)
        }

        composable(Routes.SCANNER) {
            QrScannerScreen(navController)
        }

        composable(
            route = Routes.HISTORY_DETAIL,
            arguments = listOf(navArgument("hash") { type = NavType.StringType })
        ) { backStackEntry ->
            val hash = backStackEntry.arguments?.getString("hash") ?: ""
            TransactionDetailScreen(navController, hash)
        }

        // ─── Écrans secondaires / prototypes ──────────────────
        composable(Routes.FIRST_LAUNCH) {
            FirstLaunchSplash(onFinished = {
                navController.navigate(Routes.ONBOARDING) {
                    popUpTo(Routes.FIRST_LAUNCH) { inclusive = true }
                }
            })
        }

        composable(Routes.HELP) {
            HelpScreen(navController)
        }

        composable(Routes.PORTFOLIO) {
            PortfolioScreen(navController)
        }

        composable(Routes.PORTFOLIO_TOKEN_DETAIL) {
            PortfolioTokenDetailScreen(navController)
        }

        composable(Routes.SEND_FORM) {
            SendFormScreen(navController)
        }

        composable(Routes.SEND_CONFIRM) {
            SendConfirmScreen(navController)
        }

        composable(
            route = Routes.SEND_RESULT,
            arguments = listOf(navArgument("hash") { type = NavType.StringType })
        ) { backStackEntry ->
            val hash = backStackEntry.arguments?.getString("hash") ?: ""
            SendResultScreen(navController, hash)
        }

        composable(Routes.SWAP_CONFIRM) {
            SwapConfirmScreen(navController)
        }

        composable(
            route = Routes.SWAP_RESULT,
            arguments = listOf(navArgument("hash") { type = NavType.StringType })
        ) { backStackEntry ->
            val hash = backStackEntry.arguments?.getString("hash") ?: ""
            SwapResultScreen(navController, hash)
        }

        composable(Routes.RECEIVE_NETWORK) {
            ReceiveNetworkScreen(navController)
        }

        composable(
            route = Routes.RECEIVE_ADDRESS,
            arguments = listOf(navArgument("blockchain") { type = NavType.StringType })
        ) { backStackEntry ->
            val blockchain = backStackEntry.arguments?.getString("blockchain") ?: "ETH"
            ReceiveAddressScreen(navController, blockchain)
        }

        composable(
            route = Routes.RECEIVE_ASSET,
            arguments = listOf(
                navArgument("symbol") { type = NavType.StringType },
                navArgument("chain") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val symbol = backStackEntry.arguments?.getString("symbol") ?: "ETH"
            val chain = backStackEntry.arguments?.getString("chain") ?: "ETH"
            com.vaultex.ui.screens.receive.ReceiveAssetScreen(navController, symbol, chain)
        }

        composable(Routes.ADD_TOKEN) {
            AddTokenScreen(navController)
        }

        composable(Routes.MANAGE_TOKENS) {
            ManageTokensScreen(navController)
        }

        composable(Routes.MANAGE_ASSETS) {
            com.vaultex.ui.screens.assets.ManageAssetsScreen(navController)
        }

        composable(Routes.SECURITY_SETUP) {
            SecuritySetupScreen(navController)
        }

        composable(Routes.SETTINGS_IMPORT_WALLET) {
            SettingsImportWalletScreen(navController)
        }

        composable(Routes.WALLET_MANAGEMENT) {
            WalletManagementScreen(navController)
        }

        composable(Routes.SECURITY_NOTIFICATIONS) {
            SecurityNotificationsScreen(navController)
        }
    }
}
