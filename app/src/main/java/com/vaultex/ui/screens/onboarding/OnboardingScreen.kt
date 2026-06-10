package com.vaultex.ui.screens.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

import com.vaultex.R
import com.vaultex.ui.navigation.Routes
import com.vaultex.ui.theme.*

import kotlinx.coroutines.launch

data class OnboardingPage(
    val title: String,
    val description: String
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    navController: NavHostController
) {

    val pages = listOf(

        OnboardingPage(
            stringResource(R.string.welcome_title),
            stringResource(R.string.onboarding_page1_desc)
        ),

        OnboardingPage(
            stringResource(R.string.onboarding_page2_title),
            stringResource(R.string.onboarding_page2_desc)
        ),

        OnboardingPage(
            stringResource(R.string.onboarding_page3_title),
            stringResource(R.string.onboarding_page3_desc)
        )
    )

    val pagerState = rememberPagerState(
        pageCount = { pages.size }
    )

    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        BgPrimary,
                        BgSecondary
                    )
                )
            )
    ) {

        Column(
            modifier = Modifier.fillMaxSize()
        ) {

            /*
            =========================
            SKIP
            =========================
             */

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.End
            ) {

                TextButton(
                    onClick = {
                        navController.navigate(Routes.WELCOME)
                    }
                ) {

                    Text(
                        text = stringResource(R.string.onboarding_skip),
                        color = TextPrimary
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
                        .padding(30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {

                    Text(
                        text = "◆",
                        fontSize = 90.sp,
                        color = AccentGold
                    )

                    Spacer(modifier = Modifier.height(40.dp))

                    Text(
                        text = item.title,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = item.description,
                        fontSize = 16.sp,
                        color = TextSecondary
                    )
                }
            }

            /*
            =========================
            BOTTOM
            =========================
             */

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(30.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Row {

                    repeat(pages.size) { index ->

                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .size(
                                    if (pagerState.currentPage == index)
                                        14.dp
                                    else
                                        8.dp
                                )
                                .background(
                                    if (pagerState.currentPage == index)
                                        AccentGold
                                    else
                                        TextSecondary
                                )
                        )
                    }
                }

                TextButton(
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
                    }
                ) {

                    Text(
                        text =
                            if (pagerState.currentPage == pages.lastIndex)
                                stringResource(R.string.onboarding_get_started)
                            else
                                stringResource(R.string.onboarding_next),

                        color = AccentGold,
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}