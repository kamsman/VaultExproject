package com.vaultex.ui.screens.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vaultex.R
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.*
import com.vaultex.ui.viewmodel.SplashViewModel
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(navController: NavHostController) {
    val viewModel: SplashViewModel = hiltViewModel()

    val contexteApp = androidx.compose.ui.platform.LocalContext.current.applicationContext

    LaunchedEffect(Unit) {
        /*
        L'ATTENTE SUIT L'ANIMATION, elle n'est plus un chiffre arbitraire.

        3 200 ms était une valeur posée à la main, sans rapport avec ce que
        l'écran montre — et bien trop longue : on ouvre un portefeuille pour
        vérifier un solde, pas pour regarder un logo.

        On attend maintenant la durée de l'animation plus une demi-seconde
        pour laisser lire le nom. La constante vit à côté de l'animation : sa
        durée a déjà changé une fois — quand le chargeur à points a disparu et
        que le logo a dû porter l'écran seul — et l'attente a suivi sans qu'on
        y touche.
        */
        delay(com.vaultex.ui.components.LOGO_ANIME_DUREE_MS + 500L)
        // Route vers PIN_UNLOCK si wallet existe, animation de premier lancement sinon
        val aUnPortefeuille = viewModel.hasWallet()
        // Dernier instant où « nouvel arrivant » et « mise à jour » se
        // distinguent : après l'intégration, les deux ont un portefeuille.
        com.vaultex.ui.components.marquerDemarrage(contexteApp, aUnPortefeuille)
        val destination = if (aUnPortefeuille) Routes.PIN_UNLOCK else Routes.FIRST_LAUNCH
        navController.navigate(destination) {
            popUpTo(Routes.SPLASH) { inclusive = true }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SplashNavyTop, SplashNavyBottom))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            /*
            Le losange se forme, puis l'anneau se referme autour.

            Le logo était posé là, immobile, avec une simple respiration
            d'échelle. L'animation raconte quelque chose : un cercle qui se
            REFERME autour d'une forme dit « coffre » sans un mot — c'est ce
            que fait cette application, dit sans une phrase.
            */
            com.vaultex.ui.components.LogoAnime(taille = 124.dp)

            Spacer(Modifier.height(28.dp))

            com.vaultex.ui.components.VaultexWordmark(height = 40.dp)

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.splash_tagline),
                color = TextMuted,
                fontSize = 16.sp
            )

            /*
            LE CHARGEUR À POINTS EST RETIRÉ.

            Deux animations tournaient en même temps sur le même écran : le
            logo qui se construit, et six points qui tournaient en boucle
            au-dessous. L'œil ne savait plus laquelle regarder, et la seconde
            n'apprenait rien — elle tournait aussi vite un jour de bon réseau
            qu'un jour sans réseau.

            Le logo suffit : il progresse, donc il indique déjà que quelque
            chose se passe, et il s'arrête quand c'est fini.
            */
        }
    }
}
