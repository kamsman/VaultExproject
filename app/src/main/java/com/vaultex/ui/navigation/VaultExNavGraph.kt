package com.vaultex.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

/*
=========================
SPLASH / WELCOME
=========================
*/

import com.vaultex.ui.screens.splash.SplashScreen
import com.vaultex.ui.screens.welcome.WelcomeScreen

/*
=========================
ONBOARDING
=========================
*/

import com.vaultex.ui.screens.onboarding.OnboardingScreen
import com.vaultex.ui.screens.onboarding.MnemonicDisplayScreen
import com.vaultex.ui.screens.onboarding.MnemonicVerifyScreen
import com.vaultex.ui.screens.onboarding.ImportWalletScreen

/*
=========================
SECURITY
=========================
*/

import com.vaultex.ui.screens.security.PinSetupScreen

/*
=========================
DASHBOARD
=========================
*/

import com.vaultex.ui.screens.dashboard.DashboardScreen

/*
=========================
MARKET
=========================
*/

import com.vaultex.ui.screens.market.MarketScreen

/*
=========================
SETTINGS
=========================
*/

import com.vaultex.ui.screens.settings.SettingsScreen

/*
=========================
SEND / RECEIVE / SWAP
=========================
*/

import com.vaultex.ui.screens.send.SendScreen
import com.vaultex.ui.screens.receive.ReceiveScreen
import com.vaultex.ui.screens.swap.SwapScreen

/*
=========================
HISTORY
=========================
*/

import com.vaultex.ui.screens.history.HistoryScreen

/*
=========================
NOTIFICATIONS
=========================
*/

import com.vaultex.ui.screens.notifications.NotificationsScreen

/*
=========================
TOKEN DETAIL
=========================
*/

import com.vaultex.ui.screens.token.TokenDetailScreen

@Composable
fun VaultExNavGraph(
    navController: NavHostController
) {

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH
    ) {

        /*
        =========================
        SPLASH
        =========================
         */

        composable(Routes.SPLASH) {

            SplashScreen(navController)
        }

        /*
        =========================
        ONBOARDING
        =========================
         */

        composable(Routes.ONBOARDING) {

            OnboardingScreen(navController)
        }

        /*
        =========================
        WELCOME
        =========================
         */

        composable(Routes.WELCOME) {

            WelcomeScreen(navController)
        }

        /*
        =========================
        CREATE WALLET
        =========================
         */

        composable(Routes.MNEMONIC_DISPLAY) {

            MnemonicDisplayScreen(navController)
        }

        composable(Routes.MNEMONIC_VERIFY) {

            MnemonicVerifyScreen(navController)
        }

        /*
        =========================
        IMPORT WALLET
        =========================
         */

        composable(Routes.IMPORT_WALLET) {

            ImportWalletScreen(navController)
        }

        /*
        =========================
        PIN SETUP
        =========================
         */

        composable(Routes.PIN_SETUP) {

            PinSetupScreen(navController)
        }

        /*
        =========================
        DASHBOARD
        =========================
         */

        composable(Routes.DASHBOARD) {

            DashboardScreen(navController)
        }

        /*
        =========================
        MARKET
        =========================
         */

        composable(Routes.MARKET) {

            MarketScreen(navController)
        }

        /*
        =========================
        SETTINGS
        =========================
         */

        composable(Routes.SETTINGS) {

            SettingsScreen(navController)
        }

        /*
        =========================
        SEND
        =========================
         */

        composable(Routes.SEND) {

            SendScreen(navController)
        }

        /*
        =========================
        RECEIVE
        =========================
         */

        composable(Routes.RECEIVE) {

            ReceiveScreen(navController)
        }

        /*
        =========================
        SWAP
        =========================
         */

        composable(Routes.SWAP) {

            SwapScreen(navController)
        }

        /*
        =========================
        HISTORY
        =========================
         */

        composable(Routes.HISTORY) {

            HistoryScreen(navController)
        }

        /*
        =========================
        NOTIFICATIONS
        =========================
         */

        composable(Routes.NOTIFICATIONS) {

            NotificationsScreen(navController)
        }

        /*
        =========================
        TOKEN DETAIL
        =========================
         */

        composable(Routes.TOKEN_DETAIL) {

            TokenDetailScreen(navController)
        }
    }
}