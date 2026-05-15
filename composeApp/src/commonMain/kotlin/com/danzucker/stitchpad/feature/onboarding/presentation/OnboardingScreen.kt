package com.danzucker.stitchpad.feature.onboarding.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.feature.onboarding.presentation.components.OnboardingPage
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.onboarding_get_started
import stitchpad.composeapp.generated.resources.onboarding_measurements
import stitchpad.composeapp.generated.resources.onboarding_next
import stitchpad.composeapp.generated.resources.onboarding_notebook
import stitchpad.composeapp.generated.resources.onboarding_orders
import stitchpad.composeapp.generated.resources.onboarding_subtitle_1
import stitchpad.composeapp.generated.resources.onboarding_subtitle_2
import stitchpad.composeapp.generated.resources.onboarding_subtitle_3
import stitchpad.composeapp.generated.resources.onboarding_title_1
import stitchpad.composeapp.generated.resources.onboarding_title_2
import stitchpad.composeapp.generated.resources.onboarding_title_3

private const val PAGE_COUNT = 3

private val buttonHeight = 52.dp

// Vertical space the bottom controls take up: screen padding + button + gap + indicators.
private val controlsHeight =
    DesignTokens.space10 + buttonHeight + DesignTokens.space6 + DesignTokens.space2

// Bottom padding for in-page text so it sits above the controls with breathing room.
private val pageTextBottomInset = controlsHeight + DesignTokens.space6

@Composable
fun OnboardingRoot(onFinished: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()

    OnboardingScreen(
        pagerState = pagerState,
        onPrimaryClick = {
            if (pagerState.currentPage < PAGE_COUNT - 1) {
                scope.launch {
                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                }
            } else {
                onFinished()
            }
        },
    )
}

@Composable
fun OnboardingScreen(
    pagerState: PagerState,
    onPrimaryClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                0 -> OnboardingPage(
                    photo = Res.drawable.onboarding_measurements,
                    title = stringResource(Res.string.onboarding_title_1),
                    subtitle = stringResource(Res.string.onboarding_subtitle_1),
                    bottomInset = pageTextBottomInset,
                )
                1 -> OnboardingPage(
                    photo = Res.drawable.onboarding_orders,
                    title = stringResource(Res.string.onboarding_title_2),
                    subtitle = stringResource(Res.string.onboarding_subtitle_2),
                    bottomInset = pageTextBottomInset,
                )
                2 -> OnboardingPage(
                    photo = Res.drawable.onboarding_notebook,
                    title = stringResource(Res.string.onboarding_title_3),
                    subtitle = stringResource(Res.string.onboarding_subtitle_3),
                    bottomInset = pageTextBottomInset,
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.space6)
                .padding(bottom = DesignTokens.space10),
            horizontalAlignment = Alignment.Start,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(PAGE_COUNT) { index ->
                    val isActive = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .height(DesignTokens.space2)
                            .width(if (isActive) DesignTokens.space6 else DesignTokens.space2)
                            .clip(RoundedCornerShape(DesignTokens.space1))
                            .background(
                                if (isActive) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.White.copy(alpha = 0.35f)
                                },
                            ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(DesignTokens.space6))

            Button(
                onClick = onPrimaryClick,
                shape = RoundedCornerShape(DesignTokens.radiusMd),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(buttonHeight),
            ) {
                Text(
                    text = if (pagerState.currentPage < PAGE_COUNT - 1) {
                        stringResource(Res.string.onboarding_next)
                    } else {
                        stringResource(Res.string.onboarding_get_started)
                    },
                )
            }
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun OnboardingScreenPreview() {
    StitchPadTheme {
        OnboardingScreen(
            pagerState = rememberPagerState(pageCount = { PAGE_COUNT }),
            onPrimaryClick = {},
        )
    }
}
