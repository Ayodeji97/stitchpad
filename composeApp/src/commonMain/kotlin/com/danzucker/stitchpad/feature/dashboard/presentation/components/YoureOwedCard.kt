package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.feature.dashboard.presentation.formatNaira
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme

private const val PULSE_CYCLE_MS = 15_000
private const val OVERDUE_PULSE_CYCLE_MS = 10_000
private const val PULSE_PEAK = 1.02f
private const val OVERDUE_PULSE_PEAK = 1.03f
private const val PULSE_RISE_MS = 450
private const val PULSE_SETTLE_MS = 1_100

/**
 * Dashboard summary card for money the tailor is owed — orders that are
 * Ready or Delivered but still carry a balance, sourced from
 * [com.danzucker.stitchpad.feature.collection.domain.CollectionCalculator].
 * Tapping opens the full To-Collect list.
 */
@Composable
fun YoureOwedCard(
    amount: Double,
    orderCount: Int,
    overdueCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val overdueSuffix = if (overdueCount > 0) " · $overdueCount overdue" else ""
    val subtitle = "across $orderCount orders$overdueSuffix"
    val cd = "Money to collect: ₦${formatNaira(amount)}, $subtitle"

    // Periodic "breathe": a subtle scale nudge every ~15s (~10s + slightly stronger when
    // overdue) to draw the eye back to money that's waiting to be collected. Animates via
    // graphicsLayer so nothing recomposes; mostly at rest between pulses.
    val overdue = overdueCount > 0
    val cycleMs = if (overdue) OVERDUE_PULSE_CYCLE_MS else PULSE_CYCLE_MS
    val peak = if (overdue) OVERDUE_PULSE_PEAK else PULSE_PEAK
    val pulseScale by rememberInfiniteTransition(label = "moneyToCollectPulse").animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = cycleMs
                1f at 0 using FastOutSlowInEasing
                peak at PULSE_RISE_MS using FastOutSlowInEasing
                1f at PULSE_SETTLE_MS using FastOutSlowInEasing
                1f at cycleMs
            },
        ),
        label = "moneyToCollectPulseScale",
    )

    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusLg),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
            }
            .clickable(onClick = onClick)
            .semantics { contentDescription = cd },
    ) {
        Row(
            modifier = Modifier.padding(DesignTokens.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Money to collect",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    text = "₦${formatNaira(amount)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun YoureOwedCardPreview() {
    StitchPadTheme {
        YoureOwedCard(amount = 45_000.0, orderCount = 3, overdueCount = 1, onClick = {})
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun YoureOwedCardDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        YoureOwedCard(amount = 45_000.0, orderCount = 3, overdueCount = 0, onClick = {})
    }
}
