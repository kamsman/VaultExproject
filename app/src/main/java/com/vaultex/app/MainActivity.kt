package com.vaultex.app

import android.os.Bundle
import android.view.WindowManager

import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity

import androidx.navigation.compose.rememberNavController

import com.vaultex.ui.navigation.VaultExNavGraph
import com.vaultex.ui.theme.VaultExTheme

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /*
        =========================
        BLOCK SCREENSHOTS
        =========================
         */

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContent {

            VaultExTheme {

                val navController =
                    rememberNavController()

                VaultExNavGraph(navController)
            }
        }
    }
}