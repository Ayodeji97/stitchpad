package com.danzucker.stitchpad.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.StitchPadTheme

private const val DOT_COUNT = 3
private const val CYCLE_MILLIS = 600
private const val STAGGER_MILLIS = 150
private const val BOUNCE_HEIGHT_FRACTION = 0.6f

@Composable
fun LoadingDots(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    dotSize: Dp = 8.dp,
    spacing: Dp = 6.dp,
) {
    val transition = rememberInfiniteTransition(label = "loading-dots")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(DOT_COUNT) { index ->
            val bounce by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = CYCLE_MILLIS, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(offsetMillis = index * STAGGER_MILLIS),
                ),
                label = "dot-$index",
            )
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .graphicsLayer { translationY = -bounce * dotSize.toPx() * BOUNCE_HEIGHT_FRACTION }
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun LoadingDotsPreview() {
    StitchPadTheme {
        LoadingDots()
    }
}
