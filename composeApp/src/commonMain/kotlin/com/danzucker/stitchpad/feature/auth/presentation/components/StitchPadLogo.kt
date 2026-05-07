package com.danzucker.stitchpad.feature.auth.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danzucker.stitchpad.ui.theme.DesignTokens

private const val MARK_SIZE_RATIO = 0.52f
private const val DOT_SIZE_RATIO = 0.12f
private const val DOT_OFFSET_X_RATIO = 0.18f
private const val DOT_OFFSET_Y_RATIO = 0.14f

/**
 * White circular badge with a typographic "S" mark and a small dark needle dot.
 * Placeholder until a real logo asset lands. Sized via [diameter].
 */
@Composable
fun StitchPadLogo(
    modifier: Modifier = Modifier,
    diameter: Dp = 80.dp,
) {
    val density = LocalDensity.current
    val markSize = with(density) { (diameter * MARK_SIZE_RATIO).toPx().toSp() / fontScale }
    val dotDiameter = diameter * DOT_SIZE_RATIO

    Box(
        modifier = modifier
            .size(diameter)
            .shadow(elevation = 12.dp, shape = CircleShape, clip = false)
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "S",
            style = TextStyle(
                fontSize = markSize,
                fontWeight = FontWeight.ExtraBold,
                color = DesignTokens.primary500,
                letterSpacing = (-0.04).sp,
            ),
        )
        Box(
            modifier = Modifier
                .size(dotDiameter)
                .offset(x = diameter * DOT_OFFSET_X_RATIO, y = diameter * DOT_OFFSET_Y_RATIO)
                .clip(CircleShape)
                .background(DesignTokens.neutral900),
        )
    }
}
