package com.danzucker.stitchpad.feature.auth.presentation.components.icons

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp

/**
 * Polychromatic Google "G" mark — drawn from the official 4-colour SVG paths.
 * Uses ImageVector to keep tinting away from the marks (Google guidelines forbid
 * recolouring) while still scaling cleanly.
 */
@Composable
fun GoogleLogo(modifier: Modifier = Modifier) {
    Image(
        painter = rememberVectorPainter(googleLogoVector),
        contentDescription = "Google",
        modifier = modifier,
    )
}

private val googleLogoVector: ImageVector = ImageVector.Builder(
    name = "GoogleLogo",
    defaultWidth = 48.dp,
    defaultHeight = 48.dp,
    viewportWidth = 48f,
    viewportHeight = 48f,
).addPath(
    pathData = PathParser().parsePathString(
        "M43.611 20.083H42V20H24v8h11.303c-1.649 4.657-6.08 8-11.303 8-6.627 0-12-5.373-12-12s5.373-12 12-12c3.059 0 5.842 1.154 7.961 3.039l5.657-5.657C34.046 6.053 29.268 4 24 4 12.955 4 4 12.955 4 24s8.955 20 20 20 20-8.955 20-20c0-1.341-.138-2.65-.389-3.917z"
    ).toNodes(),
    fill = SolidColor(Color(0xFFFFC107)),
).addPath(
    pathData = PathParser().parsePathString(
        "M6.306 14.691l6.571 4.819C14.655 15.108 18.961 12 24 12c3.059 0 5.842 1.154 7.961 3.039l5.657-5.657C34.046 6.053 29.268 4 24 4 16.318 4 9.656 8.337 6.306 14.691z"
    ).toNodes(),
    fill = SolidColor(Color(0xFFFF3D00)),
).addPath(
    pathData = PathParser().parsePathString(
        "M24 44c5.166 0 9.86-1.977 13.409-5.192l-6.19-5.238A11.91 11.91 0 0 1 24 36c-5.202 0-9.619-3.317-11.283-7.946l-6.522 5.025C9.505 39.556 16.227 44 24 44z"
    ).toNodes(),
    fill = SolidColor(Color(0xFF4CAF50)),
).addPath(
    pathData = PathParser().parsePathString(
        "M43.611 20.083H42V20H24v8h11.303a12.04 12.04 0 0 1-4.087 5.571l.003-.002 6.19 5.238C36.971 39.205 44 34 44 24c0-1.341-.138-2.65-.389-3.917z"
    ).toNodes(),
    fill = SolidColor(Color(0xFF1976D2)),
).build()

/** Apple monochrome glyph — drawn from the standard 24px Apple logo path. */
@Composable
fun AppleLogo(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Image(
        painter = rememberVectorPainter(appleLogoVector(tint)),
        contentDescription = "Apple",
        modifier = modifier,
    )
}

private fun appleLogoVector(tint: Color): ImageVector = ImageVector.Builder(
    name = "AppleLogo",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).addPath(
    pathData = PathParser().parsePathString(
        "M17.05 12.04c-.03-2.66 2.18-3.95 2.28-4.01-1.24-1.81-3.18-2.06-3.86-2.09-1.64-.17-3.21.97-4.04.97-.85 0-2.13-.95-3.51-.92-1.79.03-3.46 1.04-4.38 2.65-1.89 3.27-.48 8.1 1.34 10.74.92 1.31 1.99 2.76 3.39 2.71 1.37-.05 1.89-.88 3.55-.88 1.65 0 2.13.88 3.55.85 1.46-.03 2.39-1.32 3.27-2.62 1.04-1.5 1.46-2.96 1.48-3.04-.04-.02-2.83-1.08-2.86-4.32zM14.41 4.27c.74-.91 1.25-2.16 1.11-3.42-1.07.04-2.39.71-3.16 1.6-.69.79-1.3 2.07-1.14 3.3 1.2.09 2.43-.61 3.19-1.48z"
    ).toNodes(),
    fill = SolidColor(tint),
).build()
