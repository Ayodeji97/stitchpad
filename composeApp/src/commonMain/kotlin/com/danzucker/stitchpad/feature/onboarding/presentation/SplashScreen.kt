package com.danzucker.stitchpad.feature.onboarding.presentation

import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.components.StitchPadMark
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.FrauncesFamily
import com.danzucker.stitchpad.ui.theme.ManropeFamily
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.app_name
import stitchpad.composeapp.generated.resources.splash_tagline

private const val SPLASH_DURATION_MS = 2400L
private const val STAGGER_DELAY_MS = 200L
private const val ELEMENT_ANIM_MS = 300
private const val WORDMARK_OFFSET_DP = 8f
private const val MARK_SCALE_START = 0.92f
private const val FONT_PRELOAD_TIMEOUT_MS = 1500L

@Composable
fun SplashRoot(onSplashFinished: () -> Unit) {
    var markVisible by remember { mutableStateOf(false) }
    var wordmarkVisible by remember { mutableStateOf(false) }
    var taglineVisible by remember { mutableStateOf(false) }

    val markAlpha by animateFloatAsState(
        targetValue = if (markVisible) 1f else 0f,
        animationSpec = tween(durationMillis = ELEMENT_ANIM_MS, easing = EaseOut),
        label = "splash_mark_alpha"
    )
    val markScale by animateFloatAsState(
        targetValue = if (markVisible) 1f else MARK_SCALE_START,
        animationSpec = tween(durationMillis = ELEMENT_ANIM_MS, easing = EaseOut),
        label = "splash_mark_scale"
    )
    val wordmarkAlpha by animateFloatAsState(
        targetValue = if (wordmarkVisible) 1f else 0f,
        animationSpec = tween(durationMillis = ELEMENT_ANIM_MS, easing = EaseOut),
        label = "splash_wordmark_alpha"
    )
    val density = LocalDensity.current.density
    val wordmarkOffsetY by animateFloatAsState(
        targetValue = if (wordmarkVisible) 0f else WORDMARK_OFFSET_DP * density,
        animationSpec = tween(durationMillis = ELEMENT_ANIM_MS, easing = EaseOut),
        label = "splash_wordmark_offset"
    )
    val taglineAlpha by animateFloatAsState(
        targetValue = if (taglineVisible) 1f else 0f,
        animationSpec = tween(durationMillis = ELEMENT_ANIM_MS, easing = EaseOut),
        label = "splash_tagline_alpha"
    )

    val resolver = LocalFontFamilyResolver.current
    val fraunces = FrauncesFamily()
    val manrope = ManropeFamily()

    LaunchedEffect(Unit) {
        markVisible = true
        withTimeoutOrNull(FONT_PRELOAD_TIMEOUT_MS) {
            runCatching {
                resolver.preload(fraunces)
                resolver.preload(manrope)
            }
        }
        delay(STAGGER_DELAY_MS)
        wordmarkVisible = true
        delay(STAGGER_DELAY_MS)
        taglineVisible = true
        delay(SPLASH_DURATION_MS - STAGGER_DELAY_MS - STAGGER_DELAY_MS)
        onSplashFinished()
    }

    SplashScreen(
        markAlpha = markAlpha,
        markScale = markScale,
        wordmarkAlpha = wordmarkAlpha,
        wordmarkOffsetY = wordmarkOffsetY,
        taglineAlpha = taglineAlpha
    )
}

@Composable
fun SplashScreen(
    markAlpha: Float,
    markScale: Float,
    wordmarkAlpha: Float,
    wordmarkOffsetY: Float,
    taglineAlpha: Float
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StitchPadMark(
            size = 100.dp,
            modifier = Modifier.graphicsLayer(
                alpha = markAlpha,
                scaleX = markScale,
                scaleY = markScale
            )
        )
        Spacer(modifier = Modifier.height(DesignTokens.space5))
        Text(
            text = stringResource(Res.string.app_name),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.graphicsLayer(
                alpha = wordmarkAlpha,
                translationY = wordmarkOffsetY
            )
        )
        Spacer(modifier = Modifier.height(DesignTokens.space2))
        Text(
            text = stringResource(Res.string.splash_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.graphicsLayer(alpha = taglineAlpha)
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun SplashScreenPreview() {
    StitchPadTheme {
        SplashScreen(
            markAlpha = 1f,
            markScale = 1f,
            wordmarkAlpha = 1f,
            wordmarkOffsetY = 0f,
            taglineAlpha = 1f
        )
    }
}
