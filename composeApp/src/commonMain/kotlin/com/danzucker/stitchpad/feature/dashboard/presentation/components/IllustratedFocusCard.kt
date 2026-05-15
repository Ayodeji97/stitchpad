package com.danzucker.stitchpad.feature.dashboard.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.danzucker.stitchpad.feature.dashboard.presentation.model.FocusVariant
import com.danzucker.stitchpad.ui.theme.DesignTokens
import com.danzucker.stitchpad.ui.theme.StitchPadTheme

private val PROMINENT_ILLUSTRATION_SIZE = 140.dp
private val SPARKLE_BADGE_SIZE = 36.dp

private data class FocusCardPalette(
    val border: Color,
    val ctaTint: Color,
    val backgroundGradient: Brush?,
    val prominent: Boolean = false,
    /**
     * Tint of the optional uppercase pill ("● CALM DAY") rendered above
     * the title in prominent variants. Defaults to the CTA tint when
     * unspecified — Steady overrides it to green so the pill mood
     * differs from the brand primary action button.
     */
    val sectionPillTint: Color? = null,
)

/**
 * Builds a prominent palette where the gradient and section-pill follow a
 * single accent. Each variant below picks its accent (brand primary for onboarding,
 * green for calm/celebratory, red for urgent) and an optional split between
 * the pill mood and the CTA action.
 */
private fun prominentPalette(
    accent: Color,
    surface: Color,
    ctaTint: Color = accent,
    sectionPillTint: Color? = null,
    borderAlpha: Float = 0.4f,
    gradientAlpha: Float = 0.12f,
): FocusCardPalette = FocusCardPalette(
    border = accent.copy(alpha = borderAlpha),
    ctaTint = ctaTint,
    sectionPillTint = sectionPillTint,
    backgroundGradient = Brush.linearGradient(
        listOf(accent.copy(alpha = gradientAlpha), surface),
    ),
    prominent = true,
)

private fun paletteFor(
    variant: FocusVariant,
    primary: Color,
    surface: Color,
): FocusCardPalette = when (variant) {
    // BrandNew + FirstOrder: brand primary prominent treatment so the two
    // onboarding milestones feel like a pair.
    FocusVariant.BrandNew,
    FocusVariant.FirstOrder -> prominentPalette(accent = primary, surface = surface)
    // Quiet + Steady: green mood pill, brand primary CTA — green is the mood,
    // brand primary is the action, matching the rest of the dashboard's button language.
    FocusVariant.Quiet,
    FocusVariant.Steady -> prominentPalette(
        accent = DesignTokens.success500,
        surface = surface,
        ctaTint = primary,
        sectionPillTint = DesignTokens.success500,
        borderAlpha = 0.3f,
        gradientAlpha = 0.10f,
    )
    // Earn (NbaActive): mood and action are both revenue work — pill + CTA
    // share brand primary, no mood split.
    FocusVariant.Earn -> prominentPalette(
        accent = primary,
        surface = surface,
        sectionPillTint = primary,
    )
    // Focus (BusyDay): urgency *is* the action — whole card in error tint.
    FocusVariant.Focus -> prominentPalette(
        accent = DesignTokens.error500,
        surface = surface,
        sectionPillTint = DesignTokens.error500,
        gradientAlpha = 0.10f,
    )
    // Pickup: celebratory "loop closed" moment — green throughout.
    FocusVariant.Pickup -> prominentPalette(
        accent = DesignTokens.success500,
        surface = surface,
        sectionPillTint = DesignTokens.success500,
    )
}

/**
 * V2 hero focus card — text-on-left, 88dp illustration-on-right layout.
 *
 * The whole card is a single tap target via [Surface] + [Modifier.clickable].
 * Seven variant palettes (BrandNew/FirstOrder/Quiet/Steady/Earn/Focus/Pickup) drive the
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
    ctaSubtitle: String? = null,
    sectionLabel: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val primary = scheme.primary
    val surface = scheme.surface
    val palette = remember(variant, primary, surface) { paletteFor(variant, primary, surface) }
    val drawable = remember(variant) { heroIllustrationFor(variant) }
    val shape = RoundedCornerShape(DesignTokens.radiusLg)

    val titleStyle = if (palette.prominent) {
        MaterialTheme.typography.headlineSmall
    } else {
        MaterialTheme.typography.titleSmall
    }
    val supportingStyle = if (palette.prominent) {
        MaterialTheme.typography.bodyMedium
    } else {
        MaterialTheme.typography.bodySmall
    }
    val cardPadding: Dp = if (palette.prominent) DesignTokens.space5 else DesignTokens.space4

    // Surface(onClick=...) is not available in this project's Material3 version.
    // Using Surface + Modifier.clickable follows the pattern from WeeklyGoalsCard.
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, palette.border),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick, role = Role.Button),
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
                .padding(cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space3),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (palette.prominent) {
                    if (sectionLabel != null) {
                        SectionLabelPill(
                            label = sectionLabel,
                            tint = palette.sectionPillTint ?: palette.ctaTint,
                        )
                    } else {
                        SparkleBadge(tint = palette.ctaTint)
                    }
                    Spacer(Modifier.height(DesignTokens.space3))
                }
                Text(
                    text = title,
                    style = titleStyle,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (supporting != null) {
                    Spacer(Modifier.height(DesignTokens.space2))
                    Text(
                        text = supporting,
                        style = supportingStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (ctaLabel != null) {
                    Spacer(Modifier.height(DesignTokens.space3))
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
                    if (ctaSubtitle != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = ctaSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            DashboardIllustration(
                drawable = drawable,
                size = if (palette.prominent) PROMINENT_ILLUSTRATION_SIZE else 88.dp,
            )
        }
    }
}

@Composable
private fun SparkleBadge(tint: Color) {
    Box(
        modifier = Modifier
            .size(SPARKLE_BADGE_SIZE)
            .background(color = tint.copy(alpha = 0.15f), shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SectionLabelPill(label: String, tint: Color) {
    Surface(
        shape = CircleShape,
        color = tint.copy(alpha = 0.15f),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = DesignTokens.space3,
                vertical = DesignTokens.space1,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.space1),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color = tint, shape = CircleShape),
            )
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = tint,
            )
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
            title = "2 orders need attention",
            supporting = "1 overdue · 1 due today",
            ctaLabel = "Open Gose Wale",
            sectionLabel = "Action needed",
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
            title = "Shop is steady",
            supporting = "1 order in progress. Use this time to review the order and keep things moving.",
            ctaLabel = "Open order",
            sectionLabel = "Calm day",
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
            title = "Time for your first order",
            supporting = "Save measurements for Ola Kunle and start creating their first outfit.",
            ctaLabel = "Create order",
            ctaSubtitle = "for Ola Kunle",
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
            title = "2 orders need attention",
            supporting = "1 overdue · 1 due today",
            ctaLabel = "Open Gose Wale",
            sectionLabel = "Action needed",
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

@Preview
@Composable
@Suppress("UnusedPrivateMember")
private fun IllustratedFocusCardBrandNewPreview() {
    StitchPadTheme {
        IllustratedFocusCard(
            variant = FocusVariant.BrandNew,
            title = "Let's create your first order",
            supporting = "Add a customer, save measurements, and create your first custom outfit.",
            ctaLabel = "Create first order",
            onClick = {},
        )
    }
}

// endregion
