package com.vaultex.ui.screens.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
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

/*
LE VOLET QUI DÉCOUVRE LE NOM : 1 100 ms.

Il démarre à [LOGO_ANIME_MOT_DEBUT_MS], pendant que la brillance traverse
encore le losange : le logo et le nom sont un seul mouvement, pas deux
animations à la suite.

─── NI LES 800 MS D'ESSAI, NI LES 1 450 DE LA MAQUETTE ─────────────────

La maquette étale le nom sur 1 450 ms, mais elle y fait monter HUIT
LETTRES l'une après l'autre, en décalé. C'est la succession qui occupe ce
temps-là, pas chaque lettre.

Un volet unique étalé sur la même durée traîne : il n'y a qu'un seul
mouvement à regarder, et il devient lent. À 800 ms, l'essai précédent,
il passait au contraire avant qu'on l'ait vu.

Le nom finit donc à 2 650 ms et l'écran dure 3 150 ms — toujours moins
que les 3 550 d'avant ce travail.

Changer ce nombre suffit : l'attente avant navigation se recalcule
dessous, et c'est précisément ce qu'elle ne faisait pas.
*/
private const val MS_REVELE_MOT = 1100

/*
═══════════════════════════════════════════════════════════════════════════
L'ATTENTE SE CALE SUR CE QUI FINIT EN DERNIER
═══════════════════════════════════════════════════════════════════════════

Elle valait `durée du logo + 500 ms`, et ne regardait pas le nom du tout.

Ça marchait par chance : le nom finissait à 2 350 ms, la navigation partait
à 2 950. Mais les deux chiffres n'étaient liés par rien. Allonger le volet
de six cents millisecondes — ce que la maquette demande — aurait fait
partir l'écran AVANT la fin du mot, et le défaut n'aurait eu aucun symptôme
lisible : juste un nom qui n'apparaît jamais tout à fait, sur un écran
qu'on ne regarde pas deux fois.

`maxOf` supprime la coïncidence. Peu importe lequel des deux temps est
rallongé, l'attente suit.

LES 500 MS QUI SUIVENT NE SONT PAS DE LA MARGE : c'est le temps de lire le
nom une fois qu'il est entier. Sans eux, le mot serait complet à l'instant
même où l'écran disparaît.
═══════════════════════════════════════════════════════════════════════════
*/
private const val MS_LECTURE_NOM = 500

private val MS_AVANT_NAVIGATION =
    maxOf(
        com.vaultex.ui.components.LOGO_ANIME_DUREE_MS,
        com.vaultex.ui.components.LOGO_ANIME_MOT_DEBUT_MS + MS_REVELE_MOT
    ) + MS_LECTURE_NOM

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

        Le calcul est juste au-dessus : le plus tardif des deux temps, plus
        le temps de lire le nom. Il a déjà fallu le reprendre deux fois —
        quand le chargeur à points a disparu, puis quand le nom s'est mis à
        s'animer — et c'est la deuxième qui a montré qu'un seul des deux
        temps ne suffisait pas.
        */
        delay(MS_AVANT_NAVIGATION.toLong())
        // Route vers PIN_UNLOCK si wallet existe, animation de premier lancement sinon
        val aUnPortefeuille = viewModel.hasWallet()
        // Dernier instant où « nouvel arrivant » et « mise à jour » se
        // distinguent : après l'intégration, les deux ont un portefeuille.
        com.vaultex.ui.components.marquerDemarrage(contexteApp, aUnPortefeuille)
        /*
        Trois destinations, pas deux.

        hasWallet() ne dit que « du chiffré est enregistré ». Quand le
        Keystore de l'appareil a perdu la clé maîtresse, c'était encore vrai
        — et l'application ouvrait l'écran de code sur un portefeuille
        qu'elle ne savait plus lire : code accepté, puis soldes à zéro,
        adresses vides, envois refusés. L'utilisateur y lisait des fonds
        volatilisés.

        La vérification coûte un déchiffrement, une fois au démarrage.
        */
        val destination = when {
            aUnPortefeuille && viewModel.seedIllisible() -> Routes.SEED_ILLISIBLE
            aUnPortefeuille -> Routes.PIN_UNLOCK
            else -> Routes.FIRST_LAUNCH
        }
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

            /*
            LE NOM FAIT PARTIE DU MÊME MOUVEMENT.

            Il se découvre pendant que la brillance traverse encore le
            losange — c'est voulu. Attendre la fin de l'icône pour
            commencer le nom donnerait deux animations à la suite ; ici
            il n'y en a qu'une, qui descend du logo vers le nom.

            Son horloge est séparée mais ses bornes viennent du composant
            du logo : le recouvrement est un réglage de l'animation, pas
            de cet écran.
            */
            val motRevele = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                delay(com.vaultex.ui.components.LOGO_ANIME_MOT_DEBUT_MS.toLong())
                motRevele.animateTo(
                    1f,
                    tween(MS_REVELE_MOT, easing = EaseOutCubic)
                )
            }
            com.vaultex.ui.components.VaultexWordmarkAnime(
                height = 40.dp,
                avancement = motRevele.value
            )

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
