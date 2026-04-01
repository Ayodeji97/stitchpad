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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
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
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.feature.onboarding.presentation.components.MeasurementIllustration
import com.danzucker.stitchpad.feature.onboarding.presentation.components.NotebookIllustration
import com.danzucker.stitchpad.feature.onboarding.presentation.components.OnboardingPage
import com.danzucker.stitchpad.feature.onboarding.presentation.components.OrderTrackingIllustration
import com.danzucker.stitchpad.ui.theme.DesignTokens
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 3

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            when (page) {
                0 -> OnboardingPage(
                    illustration = { MeasurementIllustration() },
                    title = "Never lose a customer's measurements again",
                    subtitle = "Store chest, waist, hip and all body measurements safely in one place."
                )
                1 -> OnboardingPage(
                    illustration = { OrderTrackingIllustration() },
                    title = "Track orders from cutting to delivery",
                    subtitle = "Know exactly which orders are pending, in progress, or ready for pickup."
                )
                2 -> OnboardingPage(
                    illustration = { NotebookIllustration() },
                    title = "Your tailor's notebook",
                    subtitle = "Simple, fast, and built for fashion designers who work on their phones."
                )
            }
        }

        // Bottom section: dots + button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = DesignTokens.space10, top = DesignTokens.space4),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Dot indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(PAGE_COUNT) { index ->
                    val isActive = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(if (isActive) 24.dp else 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isActive) DesignTokens.primary500
                                else DesignTokens.neutral200
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Next / Get Started button
            Button(
                onClick = {
                    if (pagerState.currentPage < PAGE_COUNT - 1) {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    } else {
                        onFinished()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DesignTokens.space4)
                    .height(52.dp)
            ) {
                Text(
                    text = if (pagerState.currentPage < PAGE_COUNT - 1) "Next" else "Get Started"
                )
            }
        }
    }
}
