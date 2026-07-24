package com.vaultex.ui.screens.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

import com.vaultex.R
import com.vaultex.ui.navigation.Routes

import kotlinx.coroutines.launch

/* Même palette de marque que l'écran d'accueil : ces écrans forment la
   vitrine de l'app et restent sombres quel que soit le thème système. */
private val BrandBgTop = Color(0xFF070B1C)
private val BrandBgBottom = Color(0xFF0C1330)
private val BrandCard = Color(0xFF101A38)
private val BrandBorder = Color(0xFF1E2A4E)
private val BrandBlue = Color(0xFF3B82F6)
private val BrandBlueLight = Color(0xFF60A5FA)
private val BrandPurple = Color(0xFF8B5CF6)
private val BrandTextDim = Color(0xFF8A95B4)

/**
 * @param titlePre  début du titre, en blanc
 * @param titleAccent  mot mis en avant, en bleu (null = titre d'un seul bloc)
 */
data class OnboardingPage(
    val titlePre: String,
    val titleAccent: String?,
    val description: String,
    val icon: ImageVector
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    navController: NavHostController
) {
    val pages = listOf(
        OnboardingPage(
            stringResource(R.string.onboarding_welcome_pre),
            stringResource(R.string.app_name),
            stringResource(R.string.onboarding_page1_desc),
            Icons.Outlined.Lock
        ),
        OnboardingPage(
            stringResource(R.string.onboarding_multichain_pre),
            stringResource(R.string.onboarding_multichain_accent),
            stringResource(R.string.onboarding_page2_desc),
            Icons.Outlined.Public
        ),
        OnboardingPage(
            stringResource(R.string.onboarding_noncustodial_pre),
            stringResource(R.string.onboarding_noncustodial_accent),
            stringResource(R.string.onboarding_page3_desc),
            Icons.Outlined.VpnKey
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BrandBgTop, BrandBgBottom)))
    ) {
        // ─── « Passer » (haut droite) ───
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { navController.navigate(Routes.WELCOME) }) {
                Text(
                    text = stringResource(R.string.onboarding_skip_action),
                    color = BrandTextDim,
                    fontSize = 15.sp
                )
            }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            val item = pages[page]

            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ─── Grande carte illustration avec cercle lumineux ───
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(26.dp))
                        .background(BrandCard)
                        .border(1.dp, BrandBorder, RoundedCornerShape(26.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Anneau dégradé bleu → violet autour de l'icône.
                        Box(
                            modifier = Modifier
                                .size(150.dp)
                                .border(
                                    width = 2.dp,
                                    brush = Brush.linearGradient(listOf(BrandBlueLight, BrandPurple)),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = BrandBlueLight,
                                modifier = Modifier.size(66.dp)
                            )
                        }

                        Spacer(Modifier.height(34.dp))

                        // Titre : première partie blanche + mot clé en bleu.
                        Row(horizontalArrangement = Arrangement.Center) {
                            Text(
                                text = item.titlePre + if (item.titleAccent != null) " " else "",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            item.titleAccent?.let {
                                Text(
                                    text = it,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandBlueLight
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = item.description,
                            fontSize = 14.sp,
                            color = BrandTextDim,
                            textAlign = TextAlign.Center,
                            lineHeight = 21.sp,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }

        // ─── Points de pagination (le point actif s'allonge) ───
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 22.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(pages.size) { index ->
                val active = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .height(8.dp)
                        .width(if (active) 22.dp else 8.dp)
                        .clip(CircleShape)
                        .background(if (active) BrandBlue else BrandBlue.copy(alpha = 0.28f))
                )
            }
        }

        // ─── Bouton principal (dégradé) ───
        Box(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.horizontalGradient(listOf(BrandBlue, Color(0xFF2563EB))))
                .clickable {
                    if (pagerState.currentPage < pages.lastIndex) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        navController.navigate(Routes.WELCOME)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (pagerState.currentPage == pages.lastIndex)
                    stringResource(R.string.onboarding_start_action)
                else
                    stringResource(R.string.onboarding_next_action),
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(30.dp))
    }
}
