package com.danzucker.stitchpad.feature.onboarding.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danzucker.stitchpad.feature.onboarding.presentation.components.StitchPadLogo
import com.danzucker.stitchpad.ui.theme.DesignTokens
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.app_name
import stitchpad.composeapp.generated.resources.splash_tagline

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = DesignTokens.durationEmphasis),
        label = "splash_fade"
    )

    LaunchedEffect(Unit) {
        isVisible = true
        delay(1500L)
        onSplashFinished()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DesignTokens.primary500)
            .alpha(alpha),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StitchPadLogo(size = 100.dp)
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(Res.string.app_name),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = DesignTokens.neutral800
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.splash_tagline),
            fontSize = 14.sp,
            color = DesignTokens.neutral800.copy(alpha = 0.6f)
        )
    }
}
