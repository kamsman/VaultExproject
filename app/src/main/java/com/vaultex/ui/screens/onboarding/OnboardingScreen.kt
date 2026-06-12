package com.vaultex.ui.screens.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

import com.vaultex.R
import com.vaultex.ui.components.PrimaryButton
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.*

import kotlinx.coroutines.launch

data class OnboardingPage(
    val title: String,
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
            stringResource(R.string.welcome_title),
            stringResource(R.string.onboarding_page1_desc),
            Icons.Outlined.Lock
        ),

        OnboardingPage(
            stringResource(R.string.onboarding_page2_title),
            stringResource(R.string.onboarding_page2_desc),
            Icons.Outlined.Public
        ),

        OnboardingPage(
            stringResource(R.string.onboarding_page3_title),
            stringResource(R.string.onboarding_page3_desc),
            Icons.Outlined.VpnKey
        )
    )

    val pagerState = rememberPagerState(
        pageCount = { pages.size }
    )

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgSecondary)
    ) {

        /*
        =========================
        PASSER (haut droite)
        =========================
         */

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {

            TextButton(
                onClick = {
                    navController.navigate(Routes.WELCOME)
                }
            ) {

                Text(
                    text = stringResource(R.string.onboarding_skip_action),
                    color = TextSecondary,
                    fontSize = 15.sp
                )
            }
        }

        /*
        =========================
        PAGER
        =========================
         */

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->

            val item = pages[page]

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // Carte illustration bleu pâle arrondie
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(BgTertiary, RoundedCornerShape(28.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(64.dp)
                    )
                }

                Spacer(modifier = Modifier.height(36.dp))

                Text(
                    text = item.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = item.description,
                    fontSize = 15.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }
        }

        /*
        =========================
        POINTS DE PAGINATION
        =========================
         */

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.Center
        ) {

            repeat(pages.size) { index ->

                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(10.dp)
                        .background(
                            color = if (pagerState.currentPage == index)
                                AccentBlue
                            else
                                AccentBlue.copy(alpha = 0.25f),
                            shape = CircleShape
                        )
                )
            }
        }

        /*
        =========================
        BOUTON SUIVANT
        =========================
         */

        PrimaryButton(
            text = if (pagerState.currentPage == pages.lastIndex)
                stringResource(R.string.onboarding_start_action)
            else
                stringResource(R.string.onboarding_next_action),
            onClick = {

                if (pagerState.currentPage < pages.lastIndex) {

                    scope.launch {

                        pagerState.animateScrollToPage(
                            pagerState.currentPage + 1
                        )
                    }

                } else {

                    navController.navigate(Routes.WELCOME)
                }
            },
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}
