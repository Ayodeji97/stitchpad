package com.danzucker.stitchpad.feature.onboarding.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private val onboardingPhotoOverlay: Brush = Brush.verticalGradient(
    0f to Color.Transparent,
    0.35f to Color.Transparent,
    0.6f to Color.Black.copy(alpha = 0.45f),
    1f to Color.Black.copy(alpha = 0.85f),
)

@Composable
fun OnboardingPage(
    photo: DrawableResource,
    title: String,
    subtitle: String,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(photo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(onboardingPhotoOverlay),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.space6)
                .padding(bottom = bottomInset),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(DesignTokens.space3))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 17.sp,
                    lineHeight = 26.sp,
                ),
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}
