package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.feature.dashboard.presentation.model.FocusVariant
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme

private data class FocusCardPalette(
    val border: Color,
    val ctaTint: Color,
    val backgroundGradient: Brush?,
)

private fun paletteFor(
    variant: FocusVariant,
    primary: Color,
    surface: Color,
): FocusCardPalette = when (variant) {
    FocusVariant.FirstOrder -> FocusCardPalette(
        border = DesignTokens.info500.copy(alpha = 0.25f),
        ctaTint = DesignTokens.info500,
        backgroundGradient = null,
    )
    FocusVariant.Quiet -> FocusCardPalette(
        border = DesignTokens.success500.copy(alpha = 0.25f),
        ctaTint = DesignTokens.success500,
        backgroundGradient = null,
    )
    FocusVariant.Steady -> FocusCardPalette(
        border = DesignTokens.info500.copy(alpha = 0.25f),
        ctaTint = DesignTokens.info500,
        backgroundGradient = null,
    )
    FocusVariant.Earn -> FocusCardPalette(
        border = primary.copy(alpha = 0.4f),
        ctaTint = primary,
        backgroundGradient = Brush.linearGradient(
            listOf(
                primary.copy(alpha = 0.12f),
                surface,
            ),
        ),
    )
    FocusVariant.Focus -> FocusCardPalette(
        border = DesignTokens.error500.copy(alpha = 0.3f),
        ctaTint = DesignTokens.error500,
        backgroundGradient = Brush.linearGradient(
            listOf(
                DesignTokens.error500.copy(alpha = 0.08f),
                surface,
            ),
        ),
    )
    FocusVariant.Pickup -> FocusCardPalette(
        border = DesignTokens.success500.copy(alpha = 0.3f),
        ctaTint = DesignTokens.success500,
        backgroundGradient = Brush.linearGradient(
            listOf(
                DesignTokens.success500.copy(alpha = 0.10f),
                surface,
            ),
        ),
    )
}

/**
 * V2 hero focus card — text-on-left, 88dp illustration-on-right layout.
 *
 * The whole card is a single tap target via [Surface] + [Modifier.clickable].
 * Six variant palettes (FirstOrder/Quiet/Steady/Earn/Focus/Pickup) drive the
 * border, CTA tint, and optional gradient background.
 *
 * @param variant Drives palette: border colour, CTA tint, optional gradient.
 * @param title   Primary headline, 1–2 lines.
 * @param supporting Optional secondary line shown under the title.
 * @param ctaLabel   Optional inline call-to-action with a forward arrow.
 * @param onClick Invoked on tap of the whole card.
 */
@Composable
fun IllustratedFocusCard(
    variant: FocusVariant,
    title: String,
    supporting: String?,
    ctaLabel: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val primary = scheme.primary
    val surface = scheme.surface
    val palette = remember(variant, primary, surface) { paletteFor(variant, primary, surface) }
    val drawable = remember(variant) { heroIllustrationFor(variant) }
    val shape = RoundedCornerShape(DesignTokens.radiusLg)

    // Surface(onClick=...) is not available in this project's Material3 version.
    // Using Surface + Modifier.clickable follows the pattern from WeeklyGoalsCard.
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, palette.border),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .then(
                    if (palette.backgroundGradient != null) {
                        Modifier.background(palette.backgroundGradient)
                    } else {
                        Modifier
                    },
                )
                .padding(DesignTokens.space4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (supporting != null) {
                    Spacer(Modifier.height(DesignTokens.space1))
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (ctaLabel != null) {
                    Spacer(Modifier.height(DesignTokens.space2))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DesignTokens.space1),
                    ) {
                        Text(
                            text = ctaLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = palette.ctaTint,
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = palette.ctaTint,
                            modifier = Modifier.size(DesignTokens.iconInline),
                        )
                    }
                }
            }
            DashboardIllustration(drawable = drawable)
        }
    }
}

// region — Previews (6 light + 2 dark for high-contrast variants)

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun IllustratedFocusCardFocusPreview() {
    StitchPadTheme {
        IllustratedFocusCard(
            variant = FocusVariant.Focus,
            title = "2 orders need attention today",
            supporting = "1 overdue fitting · 1 dress due today · ₦120,000 to collect",
            ctaLabel = "View priorities",
            onClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun IllustratedFocusCardPickupPreview() {
    StitchPadTheme {
        IllustratedFocusCard(
            variant = FocusVariant.Pickup,
            title = "1 order is ready for pickup",
            supporting = "Kunle Adeyemi's senator wear is ready. Message customer or mark delivered.",
            ctaLabel = "Open order",
            onClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun IllustratedFocusCardEarnPreview() {
    StitchPadTheme {
        IllustratedFocusCard(
            variant = FocusVariant.Earn,
            title = "₦185,000 is ready to collect",
            supporting = "2 deposits and 1 final balance can be collected today.",
            ctaLabel = "Collect payments",
            onClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun IllustratedFocusCardSteadyPreview() {
    StitchPadTheme {
        IllustratedFocusCard(
            variant = FocusVariant.Steady,
            title = "Workshop is steady",
            supporting = "5 orders are moving smoothly. Nothing is overdue today.",
            ctaLabel = "Open pipeline",
            onClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun IllustratedFocusCardQuietPreview() {
    StitchPadTheme {
        IllustratedFocusCard(
            variant = FocusVariant.Quiet,
            title = "Quiet day — bring in new work",
            supporting = "No orders are due today. Reconnect with past customers and follow up on quotes.",
            ctaLabel = "Reconnect now",
            onClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun IllustratedFocusCardFirstOrderPreview() {
    StitchPadTheme {
        IllustratedFocusCard(
            variant = FocusVariant.FirstOrder,
            title = "Turn your customer into your first order",
            supporting = "Add a customer, save measurements, and create your first custom outfit.",
            ctaLabel = "Create order for Ola Kunle",
            onClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun IllustratedFocusCardFocusDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        IllustratedFocusCard(
            variant = FocusVariant.Focus,
            title = "2 orders need attention today",
            supporting = "1 overdue fitting · 1 dress due today · ₦120,000 to collect",
            ctaLabel = "View priorities",
            onClick = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun IllustratedFocusCardPickupDarkPreview() {
    StitchPadTheme(darkTheme = true) {
        IllustratedFocusCard(
            variant = FocusVariant.Pickup,
            title = "1 order is ready for pickup",
            supporting = "Kunle Adeyemi's senator wear is ready. Message customer or mark delivered.",
            ctaLabel = "Open order",
            onClick = {},
        )
    }
}

// endregion
