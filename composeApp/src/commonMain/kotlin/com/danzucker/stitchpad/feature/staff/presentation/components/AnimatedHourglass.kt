package com.danzucker.stitchpad.feature.staff.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

private const val CYCLE_MS = 3400
private const val ARC_MS = 2800
private const val DRAIN_END = 0.66f
private const val FLIP_START = 0.70f
private const val FLIP_END = 0.84f
private const val ARC_SWEEP_DEG = 140f

/**
 * The staff "waiting for approval" indicator: an hourglass whose sand drains
 * top→bottom and then flips 180° to drain again (seamless because an hourglass is
 * vertically symmetric), wrapped in a slowly rotating ring arc. Purely decorative.
 */
@Composable
fun AnimatedHourglass(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
) {
    val transition = rememberInfiniteTransition(label = "hourglass")

    val drain by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = CYCLE_MS
                0f at 0 using LinearEasing
                1f at (CYCLE_MS * DRAIN_END).toInt() using LinearEasing
                1f at CYCLE_MS
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "drain",
    )
    val flip by transition.animateFloat(
        initialValue = 0f,
        targetValue = 180f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = CYCLE_MS
                0f at 0
                0f at (CYCLE_MS * FLIP_START).toInt()
                180f at (CYCLE_MS * FLIP_END).toInt() using FastOutSlowInEasing
                180f at CYCLE_MS
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "flip",
    )
    val arc by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = ARC_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "arc",
    )

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            drawRing(color = color, trackColor = trackColor, arcRotation = arc)
            rotate(degrees = flip, pivot = center) {
                drawHourglass(color = color, drain = drain)
            }
        }
    }
}

private fun DrawScope.drawRing(color: Color, trackColor: Color, arcRotation: Float) {
    val stroke = (size.minDimension * 0.021f).coerceAtLeast(2f)
    val inset = stroke / 2f
    // Faint full track.
    drawCircle(color = trackColor, radius = (size.minDimension / 2f) - inset, style = Stroke(width = stroke))
    // Rotating arc segment — the continuous "working" signal.
    drawArc(
        color = color,
        startAngle = arcRotation - 90f,
        sweepAngle = ARC_SWEEP_DEG,
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawHourglass(color: Color, drain: Float) {
    val cx = center.x
    val cy = center.y
    val glass = min(size.width, size.height) * 0.42f
    val halfW = glass / 2f
    val halfH = glass / 2f
    val top = cy - halfH
    val bottom = cy + halfH

    // Sand — top chamber drains (level drops toward the neck).
    val topLevelY = top + (cy - top) * drain
    val topHalf = halfW * (1f - drain)
    if (drain < 1f) {
        val topSand = Path().apply {
            moveTo(cx, cy)
            lineTo(cx - topHalf, topLevelY)
            lineTo(cx + topHalf, topLevelY)
            close()
        }
        drawPath(topSand, color)
    }
    // Sand — bottom chamber fills from the base up.
    if (drain > 0f) {
        val fillY = cy + (bottom - cy) * (1f - drain)
        val fillHalf = halfW * (1f - drain)
        val bottomSand = Path().apply {
            moveTo(cx - fillHalf, fillY)
            lineTo(cx + fillHalf, fillY)
            lineTo(cx + halfW, bottom)
            lineTo(cx - halfW, bottom)
            close()
        }
        drawPath(bottomSand, color)
    }
    // Falling grain stream through the neck.
    if (drain in 0.04f..0.96f) {
        drawLine(
            color = color,
            start = Offset(cx, cy - halfH * 0.12f),
            end = Offset(cx, cy + halfH * 0.42f),
            strokeWidth = glass * 0.05f,
            cap = StrokeCap.Round,
        )
    }

    // Frame drawn on top of the sand.
    val frameStroke = glass * 0.09f
    val caps = Path().apply {
        moveTo(cx - halfW, top)
        lineTo(cx + halfW, top)
        moveTo(cx - halfW, bottom)
        lineTo(cx + halfW, bottom)
    }
    val body = Path().apply {
        moveTo(cx - halfW, top)
        lineTo(cx, cy)
        lineTo(cx - halfW, bottom)
        moveTo(cx + halfW, top)
        lineTo(cx, cy)
        lineTo(cx + halfW, bottom)
    }
    drawPath(caps, color, style = Stroke(width = frameStroke, cap = StrokeCap.Round))
    drawPath(body, color, style = Stroke(width = frameStroke, cap = StrokeCap.Round))
}
