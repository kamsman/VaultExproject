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
import com.vaultex.ui.screens.home.HomeScreen
import com.vaultex.ui.screens.market.MarketScreen
import com.vaultex.ui.screens.market.CoinDetailScreen
import com.vaultex.ui.screens.settings.SettingsScreen
import com.vaultex.ui.screens.settings.BackupScreen
import com.vaultex.ui.screens.send.SendScreen
import com.vaultex.ui.screens.receive.ReceiveScreen
import com.vaultex.ui.screens.swap.SwapScreen
import com.vaultex.ui.screens.history.HistoryScreen
import com.vaultex.ui.screens.mobilemoney.MobileMoneyScreen
import com.vaultex.ui.screens.auth.PanicPinScreen
import com.vaultex.ui.screens.auth.BiometricSetupScreen
import com.vaultex.ui.screens.walletmanager.WalletManagerScreen
import com.vaultex.ui.screens.walletmanager.TokenManagerScreen
import com.vaultex.ui.screens.security.NotificationsScreen
import com.vaultex.ui.screens.tokens.TokenDetailScreen
import com.vaultex.ui.screens.unlock.UnlockScreen
import com.vaultex.ui.screens.addressbook.AddressBookScreen
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

        composable(Routes.COIN_DETAIL) {
            CoinDetailScreen(navController)
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

        composable(Routes.RECEIVE) {
            ReceiveScreen(navController)
        }

        composable(Routes.SWAP) {
            SwapScreen(navController)
        }

        composable(Routes.HISTORY) {
            HistoryScreen(navController)
        }

        composable(Routes.MOBILE_MONEY) {
            MobileMoneyScreen(navController)
        }

        composable(Routes.NOTIFICATIONS) {
            NotificationsScreen(navController)
        }

        composable(Routes.TOKEN_DETAIL) {
            TokenDetailScreen(navController)
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
    }
}
