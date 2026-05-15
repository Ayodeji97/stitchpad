package com.danzucker.stitchpad.feature.reports.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val DEFAULT_HEIGHT = 36.dp
private val STROKE_WIDTH = 2.dp
private val DOT_RADIUS = 3.dp

/**
 * Single-color polyline of values, fixed height, full width. Shows a filled
 * dot on the most-recent (rightmost) point. Empty values or all-zero values
 * render as a flat line.
 */
@Composable
fun Sparkline(
    values: List<Double>,
    modifier: Modifier = Modifier,
    color: Color? = null,
    height: Dp = DEFAULT_HEIGHT
) {
    val resolvedColor = color ?: MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier.height(height)) {
        if (values.isEmpty()) return@Canvas
        val maxValue = values.max().coerceAtLeast(1.0)
        val strokePx = STROKE_WIDTH.toPx()
        val dotPx = DOT_RADIUS.toPx()
        // Inset so the stroke and dot don't clip on edges.
        val inset = dotPx + strokePx / 2f
        val width = size.width
        val drawHeight = size.height - inset * 2f
        val xStep = if (values.size > 1) (width - inset * 2f) / (values.size - 1) else 0f

        val points = values.mapIndexed { index, value ->
            val ratio = (value / maxValue).toFloat().coerceIn(0f, 1f)
            val x = inset + xStep * index
            val y = size.height - inset - (drawHeight * ratio)
            Offset(x, y)
        }

        val path = Path().apply {
            points.firstOrNull()?.let { moveTo(it.x, it.y) }
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(
            path = path,
            color = resolvedColor,
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )
        points.lastOrNull()?.let { drawCircle(resolvedColor, radius = dotPx, center = it) }
    }
}
