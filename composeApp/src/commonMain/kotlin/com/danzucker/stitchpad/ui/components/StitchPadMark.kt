package com.danzucker.stitchpad.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.LocalStitchPadColors
import com.danzucker.stitchpad.ui.theme.StitchPadTheme

/**
 * The StitchPad Measure Ledger mark — notebook silhouette with ruler ticks
 * along the left edge and a single saffron heritage accent.
 *
 * Lockup rules:
 * - Minimum size: 24.dp. Below this, ruler ticks merge.
 * - Never stretch: always 1:1 square via [size].
 * - Mark + wordmark spacing in horizontal lockups: 12.5% of mark width.
 * - Optical alignment: wordmark x-height sits on mark's vertical midpoint.
 *
 * Accessibility (WCAG):
 * - Default colors meet AA Large on paperLight (8.4:1) and inkDark (4.7:1).
 * - Saffron tick at AA Large on indigo cover (4.1:1) — decorative accent.
 *
 * Inverted variant (for use on dark photo backgrounds, e.g. AuthHero):
 * pass `coverColor = Color.White`, `coverDepthColor = neutral200`,
 * `detailColor = MaterialTheme.colorScheme.primary`.
 */
@Composable
fun StitchPadMark(
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    coverColor: Color = MaterialTheme.colorScheme.primary,
    coverDepthColor: Color = MaterialTheme.colorScheme.secondary,
    detailColor: Color = DesignTokens.paperLight,
    accentColor: Color = LocalStitchPadColors.current.heritageAccent,
    contentDescription: String? = "StitchPad",
) {
    val vector = remember(coverColor, coverDepthColor, detailColor, accentColor) {
        buildStitchPadMarkVector(
            coverColor = coverColor,
            coverDepthColor = coverDepthColor,
            detailColor = detailColor,
            accentColor = accentColor,
        )
    }
    Image(
        imageVector = vector,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
    )
}

private fun buildStitchPadMarkVector(
    coverColor: Color,
    coverDepthColor: Color,
    detailColor: Color,
    accentColor: Color,
): ImageVector {
    val builder = ImageVector.Builder(
        name = "StitchPadMark",
        defaultWidth = 1024.dp,
        defaultHeight = 1024.dp,
        viewportWidth = 1024f,
        viewportHeight = 1024f,
    )
    // Back cover (depth) — offset right + down
    builder.addRoundedRect(x = 240f, y = 180f, w = 560f, h = 720f, r = 28f, fill = coverDepthColor)
    // Front cover
    builder.addRoundedRect(x = 200f, y = 140f, w = 560f, h = 720f, r = 28f, fill = coverColor)
    // 12 ruler ticks at x=220, every 50px starting y=200.
    // Index 0,3,6,9 are long (50px); others short (30px). Index 5 is saffron.
    for (i in 0 until 12) {
        val isLong = i % 3 == 0
        val length = if (isLong) 50f else 30f
        val fill = if (i == 5) accentColor else detailColor
        builder.addRoundedRect(x = 220f, y = 200f + i * 50f, w = length, h = 6f, r = 3f, fill = fill)
    }
    // 14 stitch dashes at x=700, every 40px starting y=200 (26 tall + 14 gap).
    for (i in 0 until 14) {
        builder.addRoundedRect(x = 700f, y = 200f + i * 40f, w = 6f, h = 26f, r = 3f, fill = detailColor)
    }
    return builder.build()
}

private fun ImageVector.Builder.addRoundedRect(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    r: Float,
    fill: Color,
) {
    // Emit an SVG path string for the rounded rect, then convert via PathParser
    // (matches the project convention used in BrandLogos.kt).
    val svg = buildString {
        append("M${x + r} $y ")
        append("H${x + w - r} ")
        append("A$r $r 0 0 1 ${x + w} ${y + r} ")
        append("V${y + h - r} ")
        append("A$r $r 0 0 1 ${x + w - r} ${y + h} ")
        append("H${x + r} ")
        append("A$r $r 0 0 1 $x ${y + h - r} ")
        append("V${y + r} ")
        append("A$r $r 0 0 1 ${x + r} $y ")
        append("Z")
    }
    addPath(
        pathData = PathParser().parsePathString(svg).toNodes(),
        pathFillType = PathFillType.NonZero,
        fill = SolidColor(fill),
    )
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun StitchPadMarkPreview() {
    StitchPadTheme {
        Column(
            modifier = Modifier
                .background(DesignTokens.paperLight)
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                StitchPadMark(size = 24.dp)
                StitchPadMark(size = 48.dp)
                StitchPadMark(size = 80.dp)
                StitchPadMark(size = 120.dp)
            }
            // Inverted variant (for AuthHero photo bg)
            Box(modifier = Modifier.background(DesignTokens.neutral900).padding(24.dp)) {
                StitchPadMark(
                    size = 80.dp,
                    coverColor = Color.White,
                    coverDepthColor = DesignTokens.neutral200,
                    detailColor = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
