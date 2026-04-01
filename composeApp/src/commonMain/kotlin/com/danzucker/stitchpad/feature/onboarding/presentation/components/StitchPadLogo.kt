package com.danzucker.stitchpad.feature.onboarding.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danzucker.stitchpad.ui.theme.DesignTokens

@Composable
fun StitchPadLogo(
    size: Dp = 100.dp,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val saffron = DesignTokens.primary500
    val darkText = DesignTokens.neutral700

    Canvas(modifier = modifier.size(size)) {
        val canvasSize = this.size
        val center = Offset(canvasSize.width / 2, canvasSize.height / 2)
        val radius = canvasSize.width / 2

        // White circle background
        drawCircle(
            color = Color.White,
            radius = radius,
            center = center
        )

        // "S" letter
        val fontSize = (canvasSize.width * 0.48f)
        val textStyle = TextStyle(
            color = saffron,
            fontSize = fontSize.toSp(),
            fontWeight = FontWeight.Bold
        )
        val textLayout = textMeasurer.measure("S", textStyle)
        drawText(
            textLayoutResult = textLayout,
            topLeft = Offset(
                x = center.x - textLayout.size.width / 2,
                y = center.y - textLayout.size.height / 2
            )
        )

        // Small scissors accent (bottom-right)
        drawScissors(
            center = Offset(canvasSize.width * 0.78f, canvasSize.height * 0.78f),
            size = canvasSize.width * 0.18f,
            color = darkText
        )
    }
}

private fun Float.toSp() = this.sp

private fun DrawScope.drawScissors(center: Offset, size: Float, color: Color) {
    val halfSize = size / 2
    // Left blade (circle)
    drawCircle(
        color = color,
        radius = halfSize * 0.4f,
        center = Offset(center.x - halfSize * 0.3f, center.y - halfSize * 0.2f),
        style = Fill
    )
    // Right blade (circle)
    drawCircle(
        color = color,
        radius = halfSize * 0.4f,
        center = Offset(center.x + halfSize * 0.3f, center.y - halfSize * 0.2f),
        style = Fill
    )
    // Handles (two small lines going down)
    val path = Path().apply {
        // Left handle
        moveTo(center.x - halfSize * 0.15f, center.y + halfSize * 0.1f)
        lineTo(center.x - halfSize * 0.4f, center.y + halfSize * 0.6f)
        // Right handle
        moveTo(center.x + halfSize * 0.15f, center.y + halfSize * 0.1f)
        lineTo(center.x + halfSize * 0.4f, center.y + halfSize * 0.6f)
    }
    drawPath(
        path = path,
        color = color,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = size * 0.08f)
    )
}
