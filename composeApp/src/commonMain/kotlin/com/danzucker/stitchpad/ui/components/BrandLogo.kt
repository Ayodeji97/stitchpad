package com.danzucker.stitchpad.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.danzucker.stitchpad.ui.theme.StitchPadTheme
import org.jetbrains.compose.resources.stringResource
import stitchpad.composeapp.generated.resources.Res
import stitchpad.composeapp.generated.resources.brand_logo_content_description

/**
 * The canonical brand-logo render. When `logoUrl` is non-null, renders the user's
 * uploaded image (with [LoadingDots] in the Coil loading slot — required per the
 * project's image-loading convention). When null, falls back to an initials circle
 * that visually matches the legacy `UserAvatar`, so existing users see no change.
 */
@Composable
fun BrandLogo(
    logoUrl: String?,
    fallbackInitials: String,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
) {
    val description = stringResource(Res.string.brand_logo_content_description)
    val bgColor = MaterialTheme.colorScheme.primaryContainer
    val textColor = MaterialTheme.colorScheme.onPrimaryContainer
    val initial = fallbackInitials.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(bgColor)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (logoUrl != null) {
            SubcomposeAsyncImage(
                model = logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
                loading = { LoadingDots(dotSize = (size.value / 8f).dp.coerceAtLeast(4.dp)) },
                error = {
                    InitialsFallback(initial, textColor, size)
                },
            )
        } else {
            InitialsFallback(initial, textColor, size)
        }
    }
}

@Composable
private fun InitialsFallback(initial: String, textColor: Color, size: Dp) {
    Text(
        text = initial,
        color = textColor,
        fontSize = (size.value * 0.4f).sp,
        fontWeight = FontWeight.Bold,
    )
}

@Suppress("UnusedPrivateMember")
@Composable
@Preview
private fun BrandLogoFallbackPreview() {
    StitchPadTheme {
        BrandLogo(logoUrl = null, fallbackInitials = "Esther", size = 56.dp)
    }
}
