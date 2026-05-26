package com.danzucker.stitchpad.feature.settings.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.ui.components.BrandLogo
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.LocalIsDarkTheme
import com.danzucker.stitchpad.ui.theme.LocalStitchPadColors
import com.danzucker.stitchpad.ui.theme.StitchPadTheme

/**
 * Six flat avatar colors, indexed by [User.avatarColorIndex] (0..5).
 * Index falls back to saffron for any out-of-range value.
 *
 * Kept as a `Pair<Color, Color>` for backwards compatibility, but only the
 * deeper (second) tone is used at render time so the avatars read as one
 * solid colour instead of a linear gradient.
 */
val AvatarGradients: List<Pair<Color, Color>> = listOf(
    Color(0xFFFFB733) to Color(0xFFB07F00), // 0 saffron
    Color(0xFFF58A82) to Color(0xFFB0322A), // 1 rose
    Color(0xFF4FBE8B) to Color(0xFF1F6B49), // 2 emerald
    Color(0xFF5C8DF5) to Color(0xFF1B3F9C), // 3 cobalt
    Color(0xFFA077C2) to Color(0xFF4F2E73), // 4 aubergine
    Color(0xFF5A5550) to Color(0xFF181615), // 5 charcoal
)

internal fun avatarColor(colorIndex: Int): Color {
    val safeIndex = colorIndex.coerceIn(0, AvatarGradients.lastIndex)
    return AvatarGradients[safeIndex].second
}

internal fun avatarBrush(colorIndex: Int): Brush =
    androidx.compose.ui.graphics.SolidColor(avatarColor(colorIndex))

@Composable
fun ProfileHeroCard(
    businessName: String,
    logoUrl: String?,
    subtitle: String,
    avatarColorIndex: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    planBadgeLabel: String? = null,
) {
    // Flat warm tint instead of a linear gradient — the gradient's diagonal seam
    // was visually distracting on real device sizes. surfaceContainerHighest gives
    // a clean theme-aware lift over the screen background in dark mode; primary50
    // (cream) keeps the light-mode hero feeling warm and brand-tied.
    // Use LocalIsDarkTheme so this also follows the user's Appearance choice in
    // Settings, not just the OS-level dark-mode preference.
    val isDark = LocalIsDarkTheme.current
    // Dark mode keeps the warm vertical wash for atmospheric premium feel.
    // Light mode uses a flat primary50 — the cream-tinted wash was too
    // saturated and made the card feel like a yellow callout instead of a
    // soft background. The decorative dress-form watermark is removed
    // pending a proper dashed-outline SVG asset; the Checkroom placeholder
    // didn't carry the same intent.
    val cardBaseColor = if (isDark) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val cardBackground: androidx.compose.ui.graphics.Brush = if (isDark) {
        androidx.compose.ui.graphics.Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                cardBaseColor,
            ),
        )
    } else {
        androidx.compose.ui.graphics.SolidColor(cardBaseColor)
    }
    val borderColor = if (isDark) {
        MaterialTheme.colorScheme.outlineVariant
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        shape = RoundedCornerShape(DesignTokens.radiusXl),
        color = androidx.compose.ui.graphics.Color.Transparent,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardBackground),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(DesignTokens.space4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
            ) {
                BrandLogo(
                    logoUrl = logoUrl,
                    fallbackInitials = businessName,
                    size = 56.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = businessName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (planBadgeLabel != null) {
                        PlanBadge(
                            label = planBadgeLabel,
                            modifier = Modifier.padding(top = DesignTokens.space2),
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

/**
 * Small saffron-outlined pill rendered under the subtitle when the user is on a
 * paid plan. Acts as a visual reward for upgrading; the inline upgrade CTAs
 * (Plan card) are hidden in this state so the badge is the only plan surface.
 */
@Composable
private fun PlanBadge(
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        border = BorderStroke(1.dp, LocalStitchPadColors.current.heritageAccent),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = DesignTokens.space2,
                vertical = 2.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.WorkspacePremium,
                contentDescription = null,
                tint = LocalStitchPadColors.current.heritageAccent,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = LocalStitchPadColors.current.heritageAccent,
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun ProfileHeroCardPreview() {
    StitchPadTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(DesignTokens.space3),
                verticalArrangement = Arrangement.spacedBy(DesignTokens.space3),
            ) {
                ProfileHeroCard(
                    businessName = "Folake's Atelier",
                    logoUrl = null,
                    subtitle = "+234 803 555 0142 · folake@stitchpad.app",
                    avatarColorIndex = 0,
                    onClick = {},
                )
                ProfileHeroCard(
                    businessName = "Bola Couture",
                    logoUrl = null,
                    subtitle = "+234 802 999 1234",
                    avatarColorIndex = 3,
                    onClick = {},
                )
            }
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun ProfileHeroCardDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ProfileHeroCard(
                businessName = "Folake's Atelier",
                logoUrl = null,
                subtitle = "+234 803 555 0142 · folake@stitchpad.app",
                avatarColorIndex = 4,
                onClick = {},
                modifier = Modifier.padding(DesignTokens.space3),
            )
        }
    }
}
